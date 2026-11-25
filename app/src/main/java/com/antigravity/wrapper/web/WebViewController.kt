package com.antigravity.wrapper.web

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.antigravity.wrapper.data.AppSettings
import com.antigravity.wrapper.data.ThemeMode

/**
 * Imperative controller bridging Jetpack Compose screens and actions with the retained [WebView].
 */
class WebViewController {

    private var webView: WebView? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var defaultMobileUserAgent: String? = null
    private var lastAppliedDesktopMode: Boolean? = null

    fun attach(
        webView: WebView,
        swipeRefreshLayout: SwipeRefreshLayout,
        defaultUserAgent: String?
    ) {
        this.webView = webView
        this.swipeRefreshLayout = swipeRefreshLayout
        this.defaultMobileUserAgent = defaultUserAgent
    }

    fun detach() {
        webView = null
        swipeRefreshLayout = null
    }

    fun currentUrl(): String? = webView?.url

    fun canGoBack(): Boolean = webView?.canGoBack() == true

    fun canGoForward(): Boolean = webView?.canGoForward() == true

    fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    fun goHome() {
        webView?.loadUrl(UrlRouter.START_URL)
    }

    fun goBack() {
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward() {
        webView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload(fallbackUrl: String? = null) {
        val view = webView ?: return
        val current = view.url
        if (fallbackUrl != null || current.isNullOrBlank() ||
            current.startsWith("chrome-error://") || current == "about:blank"
        ) {
            view.loadUrl(fallbackUrl ?: UrlRouter.START_URL)
        } else {
            view.reload()
        }
    }

    fun setRefreshing(refreshing: Boolean) {
        swipeRefreshLayout?.isRefreshing = refreshing
    }

    fun applySettings(
        settings: AppSettings,
        isDarkEffective: Boolean,
        reloadIfViewportChanged: Boolean = true
    ) {
        val view = webView ?: return
        val swipe = swipeRefreshLayout

        swipe?.isEnabled = settings.pullToRefreshEnabled
        view.settings.textZoom = settings.textZoomPercent

        val targetUa = UserAgentSanitizer.forViewportMode(
            defaultMobileUserAgent,
            settings.desktopMode
        )
        val viewportChanged = lastAppliedDesktopMode != null &&
            lastAppliedDesktopMode != settings.desktopMode
        lastAppliedDesktopMode = settings.desktopMode

        if (view.settings.userAgentString != targetUa) {
            view.settings.userAgentString = targetUa
        }
        view.settings.useWideViewPort = true
        view.settings.loadWithOverviewMode = true
        view.settings.builtInZoomControls = settings.desktopMode
        view.settings.displayZoomControls = false

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, isDarkEffective)
        }

        if (viewportChanged && reloadIfViewportChanged) {
            reload()
        } else {
            injectMobilePolish(settings, isDarkEffective)
        }
    }

    fun injectMobilePolish(settings: AppSettings, isDarkEffective: Boolean) {
        val view = webView ?: return
        val enablePolish = settings.mobileUiPolish && !settings.desktopMode
        val script = MobileExperienceEnhancer.buildMobilePolishScript(
            enabled = enablePolish,
            darkTheme = isDarkEffective,
            amoledBlack = settings.themeMode == ThemeMode.AMOLED
        )
        view.evaluateJavascript(script, null)
    }

    fun findInPage(query: String) {
        val view = webView ?: return
        if (query.isBlank()) {
            view.clearMatches()
        } else {
            view.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) {
        webView?.findNext(forward)
    }

    fun clearFind() {
        webView?.clearMatches()
    }

    fun insertPrompt(promptText: String, onResult: (inserted: Boolean) -> Unit) {
        val view = webView
        if (view == null) {
            onResult(false)
            return
        }
        val script = MobileExperienceEnhancer.buildInsertPromptScript(promptText)
        view.evaluateJavascript(script) { raw ->
            onResult(raw?.trim()?.equals("true", ignoreCase = true) == true)
        }
    }

    fun clearCache() {
        webView?.clearCache(true)
    }

    fun clearSessionAndReload() {
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

    fun scrollToTop() {
        webView?.scrollTo(0, 0)
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
            setDownloadListener(null)
            webChromeClient = null
            webViewClient = android.webkit.WebViewClient()
            destroy()
        }
        detach()
    }

    companion object {
        fun safeDefaultUserAgent(view: WebView): String? {
            return try {
                WebSettings.getDefaultUserAgent(view.context)
            } catch (_: Exception) {
                view.settings.userAgentString
            }
        }
    }
}
