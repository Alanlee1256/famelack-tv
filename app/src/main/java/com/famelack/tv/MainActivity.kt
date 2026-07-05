package com.famelack.tv

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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

    private inner class VideoStateInterface {
        @android.webkit.JavascriptInterface
        fun onVideoPlaying() {
            handler.post {
                castButton.animate().alpha(0f).setDuration(200).withEndAction { castButton.visibility = View.GONE }.start()
                webView.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){try{v.webkitEnterFullscreen();}catch(e){}try{v.requestFullscreen();}catch(e){}var s=document.getElementById('__ftv_fs_style');if(!s){s=document.createElement('style');s.id='__ftv_fs_style';s.textContent='video{position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;z-index:999999!important;background:#000!important;object-fit:contain!important}';document.head.appendChild(s);}}})()", null)
            }
        }
        @android.webkit.JavascriptInterface
        fun onVideoPaused() {
            handler.post {
                castButton.visibility = View.VISIBLE; castButton.animate().alpha(1f).setDuration(300).start()
                webView.evaluateJavascript("(function(){var s=document.getElementById('__ftv_fs_style');if(s)s.remove();})()", null)
            }
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
        webView.setBackgroundColor(Color.BLACK)
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
                view?.evaluateJavascript("""
                    (function(){
                        try {
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
                            var video=document.querySelector('video');
                            if(video){
                                video.addEventListener('play',function(){Android.onVideoPlaying();});
                                video.addEventListener('pause',function(){Android.onVideoPaused();});
                                video.addEventListener('ended',function(){Android.onVideoPaused();});
                                if(!video.paused)Android.onVideoPlaying();
                            }
                        } catch(e){}
                    })();
                """.trimIndent(), null)
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
            // Google Play Services not available (e.g. Fire TV) — Cast features disabled
        }
    }

    private fun setupImmersiveMode() {
        val ctrl = WindowCompat.getInsetsController(window, window.decorView)
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
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
                webView.evaluateJavascript("(function(){var v=document.querySelector('video');if(v&&!v.paused){try{v.webkitEnterFullscreen();}catch(e){}try{v.requestFullscreen();}catch(e){}return'fs';}return'no';})()", null)
                injectClick(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                webView.evaluateJavascript("(function(){var v=document.querySelector('video');if(v&&!v.paused){try{v.webkitEnterFullscreen();}catch(e){}try{v.requestFullscreen();}catch(e){}return'fs';}return'no';})()", null)
                injectVideoClick(); return true
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

    private fun injectVideoClick() {
        webView.evaluateJavascript("""
            (function(){
                var el=document.activeElement;
                if(el&&el.tagName==='VIDEO') el.click();
                else{ var v=document.querySelector('video'); if(v) v.click(); }
            })();
        """.trimIndent(), null)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersiveMode()
    }
}
