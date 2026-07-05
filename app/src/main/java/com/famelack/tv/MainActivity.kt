package com.famelack.tv

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import androidx.mediarouter.app.MediaRouteButton

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var splashView: FrameLayout
    private lateinit var rootLayout: FrameLayout
    private lateinit var fullscreenContainer: FrameLayout

    private val handler = Handler(Looper.getMainLooper())
    private var splashHidden = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var castSession: CastSession? = null
    private lateinit var sessionManager: SessionManager
    private lateinit var castButton: MediaRouteButton

    // External player pref
    private val prefs by lazy { getSharedPreferences("player_prefs", Context.MODE_PRIVATE) }
    private var useExternalPlayer: Boolean
        get() = prefs.getBoolean("use_external_player", false)
        set(value) = prefs.edit().putBoolean("use_external_player", value).apply()

    private inner class VideoStateInterface {
        @android.webkit.JavascriptInterface
        fun onVideoPlaying() {
            handler.post {
                if (useExternalPlayer) {
                    // External player mode — extract video URL and launch VLC
                    webView.evaluateJavascript(
                        "(function(){var v=document.querySelector('video');return v?(v.currentSrc||v.src||''):'';})()",
                        ValueCallback { urlJson ->
                            val cleanUrl = urlJson?.trim('"')?.trim('\'') ?: ""
                            if (cleanUrl.startsWith("http")) {
                                // Pause the WebView video
                                webView.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.pause();})()", null)
                                launchExternalPlayer(cleanUrl)
                            }
                        }
                    )
                } else {
                    // Internal player mode — hide cast button, click ⛶ button to trigger native
                    // fullscreen, and inject CSS so video fills the screen
                    castButton.animate().alpha(0f).setDuration(200).withEndAction { castButton.visibility = View.GONE }.start()
                    webView.evaluateJavascript(
                        "(function(){" +
                        "var b=document.getElementById('__ftv_fs_btn');if(b)b.click();" +
                        "var s=document.getElementById('__ftv_fs_style');" +
                        "if(!s){" +
                        "s=document.createElement('style');" +
                        "s.id='__ftv_fs_style';" +
                        "s.textContent='video{position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;z-index:999999!important;background:#000!important;object-fit:fill!important}';" +
                        "document.head.appendChild(s);" +
                        "}})()", null
                    )
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun onVideoPaused() {
            handler.post {
                castButton.visibility = View.VISIBLE
                castButton.animate().alpha(1f).setDuration(300).start()
                webView.evaluateJavascript(
                    "(function(){var s=document.getElementById('__ftv_fs_style');if(s)s.remove();})()", null
                )
            }
        }

        @android.webkit.JavascriptInterface
        fun togglePlayerMode() {
            handler.post {
                useExternalPlayer = !useExternalPlayer
                val mode = if (useExternalPlayer) "External (VLC)" else "Internal"
                Toast.makeText(this@MainActivity, "Player: $mode", Toast.LENGTH_SHORT).show()
                // Update the toggle button text via JS
                webView.evaluateJavascript(
                    "(function(){" +
                    "var b=document.getElementById('__ftv_player_toggle');" +
                    "if(b)b.textContent='${if (useExternalPlayer) "VLC" else "WEB"}';" +
                    "})()", null
                )
            }
        }

        @android.webkit.JavascriptInterface
        fun getPlayerMode(): String {
            return if (useExternalPlayer) "external" else "internal"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.root_layout)
        webView = findViewById(R.id.webview)
        splashView = findViewById(R.id.splash_view)

        setupWebView()
        setupImmersiveMode()
        setupCastButton()

        handler.postDelayed({ hideSplash() }, 2000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        // CRITICAL for Fire TV: transparent WebView allows the hardware video SurfaceView to show through
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.displayZoomControls = false
        s.builtInZoomControls = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.databaseEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.userAgentString = "Mozilla/5.0 (Linux; Android 11; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        webView.addJavascriptInterface(VideoStateInterface(), "Android")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                customView?.let { (it.parent as? ViewGroup)?.removeView(it) }
                view?.let { v ->
                    v.setBackgroundColor(Color.BLACK)
                    fullscreenContainer.addView(v, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                    ))
                    fullscreenContainer.visibility = View.VISIBLE
                    fullscreenContainer.bringToFront()
                    webView.visibility = View.GONE
                }
                customView = view
                customViewCallback = callback
                setupImmersiveMode()
            }

            override fun onHideCustomView() {
                customView?.let { (it.parent as? ViewGroup)?.removeView(it) }
                fullscreenContainer.visibility = View.GONE
                webView.visibility = View.VISIBLE
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                setupImmersiveMode()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                hideSplash()
                view?.evaluateJavascript(
                    """
                    (function(){
                        try {
                            // --- Player mode toggle button (top-left corner) ---
                            var tb = document.getElementById('__ftv_player_toggle');
                            if(!tb) {
                                tb = document.createElement('div');
                                tb.id = '__ftv_player_toggle';
                                tb.textContent = 'WEB';
                                tb.style.cssText = 'position:fixed;top:8px;left:8px;z-index:999999;padding:6px 12px;border-radius:4px;background:rgba(0,0,0,0.6);color:#fff;font-size:14px;font-weight:bold;cursor:pointer;border:1px solid rgba(255,255,255,0.3);';
                                tb.onclick = function(e) {
                                    e.stopPropagation();
                                    Android.togglePlayerMode();
                                };
                                document.body.appendChild(tb);
                                // Update text based on saved state
                                var mode = Android.getPlayerMode();
                                tb.textContent = mode === 'external' ? 'VLC' : 'WEB';
                            }

                            // --- Fullscreen button ---
                            function addFsBtn() {
                                var video = document.querySelector('video');
                                if(!video || document.getElementById('__ftv_fs_btn')) return;
                                var fsBtn = document.createElement('div');
                                fsBtn.id = '__ftv_fs_btn';
                                fsBtn.innerHTML = '⛶';
                                fsBtn.style.cssText = 'position:fixed;bottom:80px;right:16px;z-index:99999;width:48px;height:48px;border-radius:50%;background:rgba(255,255,255,0.3);color:white;font-size:24px;display:flex;align-items:center;justify-content:center;cursor:pointer;backdrop-filter:blur(4px);border:2px solid rgba(255,255,255,0.5);';
                                fsBtn.onclick = function(e){
                                    e.stopPropagation();
                                    var v = document.querySelector('video');
                                    if(v) {
                                        if(v.webkitEnterFullscreen) v.webkitEnterFullscreen();
                                        else if(v.requestFullscreen) v.requestFullscreen();
                                    }
                                };
                                document.body.appendChild(fsBtn);
                                var obs = new MutationObserver(function(){
                                    var v = document.querySelector('video');
                                    fsBtn.style.display = v ? 'flex' : 'none';
                                });
                                obs.observe(document.body, {childList:true, subtree:true});
                            }
                            addFsBtn();

                            // --- Video event listeners ---
                            var video=document.querySelector('video');
                            if(video){
                                video.addEventListener('play',function(){Android.onVideoPlaying();});
                                video.addEventListener('pause',function(){Android.onVideoPaused();});
                                video.addEventListener('ended',function(){Android.onVideoPaused();});
                                if(!video.paused)Android.onVideoPlaying();
                            }
                        } catch(e){}
                    })();
                    """.trimIndent(), null
                )
            }
        }

        webView.loadUrl("https://famelack.com/tv/uk")

        fullscreenContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            visibility = View.GONE
        }
        rootLayout.addView(fullscreenContainer)
    }

    private fun launchExternalPlayer(videoUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(videoUrl), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Try VLC specifically if installed
                setPackage("org.videolan.vlc")
            }
            startActivity(intent)
        } catch (e1: Exception) {
            // VLC not installed — try generic chooser
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(videoUrl), "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(Intent.createChooser(intent, "Open with..."))
            } catch (e2: Exception) {
                webView.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(v)v.play();})()", null
                )
            }
        }
    }

    private fun hideSplash() {
        if (splashHidden) return
        splashHidden = true
        ObjectAnimator.ofFloat(splashView, "alpha", 1f, 0f).apply {
            duration = 500
            start()
        }
        handler.postDelayed({
            splashView.visibility = View.GONE
        }, 500)
    }

    private fun setupCastButton() {
        castButton = MediaRouteButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply { setMargins(0, (60 * resources.displayMetrics.density).toInt(), (16 * resources.displayMetrics.density).toInt(), 0) }
        }
        rootLayout.addView(castButton)
        try {
            CastContext.getSharedInstance(this).sessionManager.also {
                sessionManager = it
                it.addSessionManagerListener(object : SessionManagerListener<Session> {
                    override fun onSessionEnded(session: Session, error: Int) {
                        castSession = session as? CastSession
                    }
                    override fun onSessionEnding(session: Session) {}
                    override fun onSessionStarted(session: Session, sessionId: String) {
                        castSession = session as? CastSession
                    }
                    override fun onSessionStarting(session: Session) {}
                    override fun onSessionStartFailed(session: Session, error: Int) {}
                    override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
                        castSession = session as? CastSession
                    }
                    override fun onSessionResuming(session: Session, sessionId: String) {}
                    override fun onSessionResumeFailed(session: Session, error: Int) {}
                    override fun onSessionSuspended(session: Session, reason: Int) {}
                })
            }
        } catch (e: Exception) {
            // Google Play Services not available (e.g. Fire TV)
        }
    }

    private fun setupImmersiveMode() {
        val ctrl = WindowCompat.getInsetsController(window, window.decorView)
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // If fullscreen custom view is showing, route DPAD to it
        if (customView != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    // Pass to custom view via dispatchKeyEvent
                    customView?.dispatchKeyEvent(event ?: return true)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    hideCustomView()
                    return true
                }
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { webView.scrollBy(0, -150); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { webView.scrollBy(0, 150); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                webView.evaluateJavascript("(function(){var f=Array.from(document.querySelectorAll('button,a,input,select,textarea,video,[tabindex]:not([tabindex=\"-1\"])')).filter(function(e){return e.offsetParent!==null});var i=f.indexOf(document.activeElement);if(i>0)f[i-1].focus();})()", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                webView.evaluateJavascript("(function(){var f=Array.from(document.querySelectorAll('button,a,input,select,textarea,video,[tabindex]:not([tabindex=\"-1\"])')).filter(function(e){return e.offsetParent!==null});var i=f.indexOf(document.activeElement);if(i<f.length-1)f[i+1].focus();})()", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // Try clicking the ⛶ fullscreen button if video is playing
                webView.evaluateJavascript("(function(){var v=document.querySelector('video');if(v&&!v.paused){var b=document.getElementById('__ftv_fs_btn');if(b)b.click();return'fs';}return'no';})()", null)
                injectClick()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                // Toggle play/pause on video
                webView.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}})()", null)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (customView != null) { hideCustomView() }
                else if (webView.canGoBack()) { webView.goBack() }
                else { moveTaskToBack(true) }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hideCustomView() {
        customView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        fullscreenContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        setupImmersiveMode()
    }

    private fun injectClick() {
        webView.evaluateJavascript("""
            (function(){
                var el=document.activeElement;
                if(el&&(el.tagName==='BUTTON'||el.tagName==='A'||el.tagName==='INPUT'||el.tagName==='VIDEO')) el.click();
            })();
        """.trimIndent(), null)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersiveMode()
    }
}
