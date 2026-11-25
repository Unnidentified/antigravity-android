package com.antigravity.wrapper.web

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Custom [WebViewClient] for Antigravity that keeps Antigravity and Google Auth domains
 * inside the WebView, routes external links to Chrome Custom Tabs, persists cookies to disk,
 * and intercepts main-frame network errors so a native error UI can be shown.
 */
class AntigravityWebViewClient(
    private val onPageStartedCallback: (url: String?) -> Unit,
    private val onPageFinishedCallback: (url: String?, hadMainFrameError: Boolean) -> Unit,
    private val onMainFrameErrorCallback: (failedUrl: String?, errorCode: Int, description: String?) -> Unit,
    private val onOpenExternalUrl: (url: String) -> Unit,
    private val onHandleSystemIntent: (url: String) -> Unit
) : WebViewClient() {

    private var hasMainFrameError = false

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        return when (UrlRouter.route(url)) {
            UrlRouter.Route.IN_APP -> false
            UrlRouter.Route.EXTERNAL_BROWSER -> {
                onOpenExternalUrl(url)
                true
            }
            UrlRouter.Route.SYSTEM_INTENT -> {
                onHandleSystemIntent(url)
                true
            }
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null && !url.startsWith("chrome-error://")) {
            hasMainFrameError = false
            onPageStartedCallback(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        CookieManager.getInstance().flush()
        onPageFinishedCallback(url, hasMainFrameError)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame != true) {
            return
        }

        val failedUrl = request.url?.toString()
        val errorCode = error?.errorCode ?: ERROR_UNKNOWN
        val description = error?.description?.toString()

        // Ignore synthetic blank/cancelled loads
        if (failedUrl.equals("about:blank", ignoreCase = true)) {
            return
        }

        hasMainFrameError = true
        onMainFrameErrorCallback(failedUrl, errorCode, description)
    }
}
