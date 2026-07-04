package com.famelack.tv

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var splashView: FrameLayout
    private lateinit var rootLayout: FrameLayout
    private lateinit var shareButton: AppCompatButton

    private val handler = Handler(Looper.getMainLooper())
    private var splashHidden = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.root_layout)
        webView = findViewById(R.id.webview)
        splashView = findViewById(R.id.splash_view)

        setupWebView()
        setupShareButton()
        setupImmersiveMode()

        // Backup timer to auto-hide splash after 2 seconds
        handler.postDelayed({
            hideSplash()
        }, 2000)
    }

    private fun setupWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.displayZoomControls = false
        settings.builtInZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 11; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Hide share button with fade
                ObjectAnimator.ofFloat(shareButton, "alpha", shareButton.alpha, 0f).apply {
                    duration = 200
                    start()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                hideSplash()
            }
        }

        webView.loadUrl("https://famelack.com/tv/uk")
    }

    private fun hideSplash() {
        if (splashHidden) return
        splashHidden = true
        val animator = ObjectAnimator.ofFloat(splashView, "alpha", 1f, 0f)
        animator.duration = 500
        animator.start()
        handler.postDelayed({
            splashView.visibility = View.GONE
            // 1 second after splash is removed, fade in share button
            handler.postDelayed({
                shareButton.visibility = View.VISIBLE
                ObjectAnimator.ofFloat(shareButton, "alpha", 0f, 1f).apply {
                    duration = 300
                    start()
                }
            }, 1000)
        }, 500)
    }

    private fun setupShareButton() {
        val density = resources.displayMetrics.density
        val size = (48 * density).toInt()
        val margin = (16 * density).toInt()

        shareButton = AppCompatButton(this).apply {
            text = "📤"
            setBackgroundColor(Color.parseColor("#4464D2FF"))
            setTextColor(Color.WHITE)
            textSize = 18f
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(margin, margin, margin, margin)
            }
            visibility = View.INVISIBLE
            alpha = 0f
            setOnClickListener { shareCurrentUrl() }
        }
        rootLayout.addView(shareButton)
    }

    private fun shareCurrentUrl() {
        val url = webView.url ?: "https://famelack.com/tv/uk"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_SUBJECT, "Famelack TV")
        }
        startActivity(Intent.createChooser(shareIntent, "Share Famelack TV"))
    }

    private fun setupImmersiveMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                webView.scrollBy(0, -60)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                webView.scrollBy(0, 60)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (shareButton.hasFocus()) {
                    webView.requestFocus()
                } else {
                    webView.scrollBy(-60, 0)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                webView.scrollBy(60, 0)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                injectClickOnFocusedElement()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                injectClickOnVideoElement()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    moveTaskToBack(true)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun injectClickOnFocusedElement() {
        val js = """
            (function() {
                var el = document.activeElement;
                if (el && (el.tagName === 'BUTTON' || el.tagName === 'A' || el.tagName === 'INPUT' || el.tagName === 'VIDEO')) {
                    el.click();
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectClickOnVideoElement() {
        val js = """
            (function() {
                var el = document.activeElement;
                if (el && el.tagName === 'VIDEO') {
                    el.click();
                } else {
                    var v = document.querySelector('video');
                    if (v) { v.click(); }
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }
}
