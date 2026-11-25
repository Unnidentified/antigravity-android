package com.antigravity.wrapper.bridge

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import com.antigravity.wrapper.BuildConfig
import com.antigravity.wrapper.data.BridgeSnapshot
import com.antigravity.wrapper.data.BridgeStateParser
import com.antigravity.wrapper.web.AntigravityWebChromeClient
import com.antigravity.wrapper.web.AntigravityWebViewClient
import com.antigravity.wrapper.web.DownloadHandler
import com.antigravity.wrapper.web.UrlRouter
import com.antigravity.wrapper.web.UserAgentSanitizer

/**
 * Controller for the invisible/headless Antigravity WebView session.
 * Blocks Android focus and touch interception whenever hidden so it never steals
 * keyboard focus from the native Compose composer or interferes with drawer gestures.
 */
class AntigravityBridgeEngine(
    private val onSnapshotReceived: (BridgeSnapshot) -> Unit,
    private val onBridgeLog: (String) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var interactiveMode: Boolean = false

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "ClickableViewAccessibility")
    fun configureWebView(
        view: WebView,
        webViewClient: AntigravityWebViewClient,
        webChromeClient: AntigravityWebChromeClient,
        downloadHandler: DownloadHandler,
        initialUrl: String
    ) {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        this.webView = view

        val defaultUa = try {
            WebSettings.getDefaultUserAgent(view.context)
        } catch (_: Exception) {
            view.settings.userAgentString
        }

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            userAgentString = UserAgentSanitizer.sanitize(defaultUa)
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowContentAccess = true
            allowFileAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(view, true)

        view.setOnTouchListener { _, _ -> !interactiveMode }
        setInteractive(false)

        view.addJavascriptInterface(NativeJsBridge(), "AntigravityNative")
        view.webViewClient = webViewClient
        view.webChromeClient = webChromeClient
        view.setDownloadListener(downloadHandler)
        view.loadUrl(initialUrl.ifBlank { UrlRouter.START_URL })
    }

    fun setInteractive(interactive: Boolean) {
        interactiveMode = interactive
        val view = webView ?: return
        view.isFocusable = interactive
        view.isFocusableInTouchMode = interactive
        view.isClickable = interactive
        view.descendantFocusability = if (interactive) {
            ViewGroup.FOCUS_AFTER_DESCENDANTS
        } else {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        if (!interactive && view.hasFocus()) {
            view.clearFocus()
        }
    }

    fun injectBridgeScript() {
        webView?.evaluateJavascript(AntigravityBridgeScript.buildBootstrapScript(), null)
    }

    fun syncNow() {
        webView?.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.syncNow() : null;",
            null
        )
    }

    fun sendPrompt(text: String, onResult: (Boolean) -> Unit) {
        val view = webView
        if (view == null) {
            onResult(false)
            return
        }
        val escaped = AntigravityBridgeScript.escapeJsString(text)
        view.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.sendPrompt(\"$escaped\") : false;"
        ) { raw ->
            onResult(raw?.trim()?.equals("true", ignoreCase = true) == true)
        }
    }

    fun stopGeneration() {
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.stopGeneration();",
            null
        )
    }

    fun refreshModels() {
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.refreshModels();",
            null
        )
    }

    fun selectModel(modelName: String, effortTier: String = "") {
        val escapedModel = AntigravityBridgeScript.escapeJsString(modelName)
        val escapedTier = AntigravityBridgeScript.escapeJsString(effortTier)
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.selectModel(\"$escapedModel\", \"$escapedTier\");",
            null
        )
    }

    fun refreshQuota() {
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.refreshQuota();",
            null
        )
    }

    fun startNewChat() {
        webView?.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.startNewChat() : (window.location.href='${UrlRouter.START_URL}');",
            null
        )
    }

    fun selectConversation(url: String, title: String) {
        val escapedUrl = AntigravityBridgeScript.escapeJsString(url)
        val escapedTitle = AntigravityBridgeScript.escapeJsString(title)
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.selectConversation(\"$escapedUrl\", \"$escapedTitle\");",
            null
        )
    }

    fun triggerFileUpload() {
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.triggerFileUpload();",
            null
        )
    }

    fun exportDomSnapshot(onResult: (String?) -> Unit) {
        val view = webView
        if (view == null) {
            onResult(null)
            return
        }
        view.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.dumpDomSnapshot() : '';"
        ) { raw ->
            val decoded = decodeJsStringResult(raw)
            onResult(decoded?.takeIf { it.isNotBlank() })
        }
    }

    fun reload(url: String? = null) {
        val view = webView ?: return
        if (!url.isNullOrBlank()) {
            view.loadUrl(url)
        } else {
            val current = view.url
            if (current.isNullOrBlank() || current.startsWith("chrome-error://")) {
                view.loadUrl(UrlRouter.START_URL)
            } else {
                view.reload()
            }
        }
    }

    fun signOutAndReload() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            WebStorage.getInstance().deleteAllData()
            webView?.apply {
                clearCache(true)
                clearHistory()
                loadUrl(UrlRouter.START_URL)
            }
        }
    }

    fun onResume() {
        webView?.onResume()
    }

    fun onPause() {
        webView?.onPause()
        CookieManager.getInstance().flush()
    }

    fun flushCookies() {
        CookieManager.getInstance().flush()
    }

    fun destroy() {
        webView?.apply {
            stopLoading()
            setOnTouchListener(null)
            removeJavascriptInterface("AntigravityNative")
            setDownloadListener(null)
            webChromeClient = null
            webViewClient = android.webkit.WebViewClient()
            destroy()
        }
        webView = null
    }

    private fun decodeJsStringResult(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            com.antigravity.wrapper.data.SimpleJson.asString(
                com.antigravity.wrapper.data.SimpleJson.parse(raw)
            )
        } catch (_: Exception) {
            raw.trim('"')
        }
    }

    private inner class NativeJsBridge {
        @JavascriptInterface
        fun onStateSnapshot(json: String?) {
            val snapshot = BridgeStateParser.parseSnapshot(json) ?: return
            mainHandler.post {
                onSnapshotReceived(snapshot)
            }
        }

        @JavascriptInterface
        fun onBridgeLog(message: String?) {
            if (!message.isNullOrBlank()) {
                mainHandler.post { onBridgeLog(message) }
            }
        }
    }
}
