package com.famelack.tv

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webview: WebView
    private lateinit var splashOverlay: FrameLayout
    private var isSplashRemoved = false
    private val hideSystemUiHandler = Handler(Looper.getMainLooper())
    private val hideSystemUiRunnable = Runnable { hideSystemUI() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        webview = findViewById(R.id.webview)
        splashOverlay = findViewById(R.id.splash_overlay)

        setupImmersiveMode()

        webview.apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                displayZoomControls = false
                builtInZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                userAgentString = "Mozilla/5.0 (Linux; Android 11; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    removeSplash()
                }
            }

            webChromeClient = WebChromeClient()
            loadUrl("https://famelack.com/tv/uk")
        }

        Handler(Looper.getMainLooper()).postDelayed({
            removeSplash()
        }, 2000)
    }

    private fun removeSplash() {
        if (isSplashRemoved) return
        isSplashRemoved = true

        ObjectAnimator.ofFloat(splashOverlay, "alpha", 1f, 0f).apply {
            duration = 500
            start()
        }.also {
            Handler(Looper.getMainLooper()).postDelayed({
                splashOverlay.visibility = View.GONE
            }, 500)
        }
    }

    private fun setupImmersiveMode() {
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                hideSystemUiHandler.removeCallbacks(hideSystemUiRunnable)
                hideSystemUiHandler.postDelayed(hideSystemUiRunnable, 1500)
            }
        }
        hideSystemUI()
    }

    @SuppressLint("InlinedApi")
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                webview.scrollBy(0, -60)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                webview.scrollBy(0, 60)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                webview.scrollBy(-60, 0)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                webview.scrollBy(60, 0)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                injectClickOnFocusedElement()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                injectClickOnFocusedVideoElement()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                handleBackPress()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackPress()
    }

    private fun handleBackPress() {
        if (webview.canGoBack()) {
            webview.goBack()
        } else {
            moveTaskToBack(true)
        }
    }

    private fun injectClickOnFocusedElement() {
        val js = "(function() { " +
                "var el = document.activeElement; " +
                "if(el && (el.tagName === 'BUTTON' || el.tagName === 'A' || el.tagName === 'INPUT' || el.tagName === 'VIDEO')) { " +
                "el.click(); " +
                "} else { " +
                "var focused = document.querySelector(':focus'); " +
                "if(focused) { focused.click(); } " +
                "} " +
                "})();"
        webview.evaluateJavascript(js, null)
    }

    private fun injectClickOnFocusedVideoElement() {
        val js = "(function() { " +
                "var el = document.activeElement; " +
                "if(el && el.tagName === 'VIDEO') { " +
                "el.click(); " +
                "} else { " +
                "var vids = document.getElementsByTagName('video'); " +
                "if(vids.length > 0) { vids[0].click(); } " +
                "} " +
                "})();"
        webview.evaluateJavascript(js, null)
    }

    override fun onResume() {
        super.onResume()
        webview.onResume()
        hideSystemUI()
    }

    override fun onPause() {
        webview.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webview.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        hideSystemUiHandler.removeCallbacks(hideSystemUiRunnable)
        super.onDestroy()
    }
}
