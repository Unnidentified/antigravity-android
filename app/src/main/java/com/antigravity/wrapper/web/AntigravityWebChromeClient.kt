package com.antigravity.wrapper.web

import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Custom [WebChromeClient] handling loading progress, page title updates, native file uploads via
 * [onShowFileChooser], runtime-backed browser permissions via [onPermissionRequest],
 * popup/new-tab routing via [onCreateWindow], and fullscreen custom views.
 */
class AntigravityWebChromeClient(
    private val onProgressUpdate: (progress: Int) -> Unit,
    private val onTitleChanged: (title: String?) -> Unit,
    private val onFileChooserRequested: (
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams?
    ) -> Boolean,
    private val onWebPermissionRequested: (request: PermissionRequest) -> Unit,
    private val onWebPermissionCanceled: (request: PermissionRequest) -> Unit,
    private val onPopupUrlResolved: (url: String) -> Unit,
    private val onEnterFullscreen: (view: View, callback: CustomViewCallback) -> Unit,
    private val onExitFullscreen: () -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressUpdate(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        onTitleChanged(title)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        if (filePathCallback == null) {
            return false
        }
        return onFileChooserRequested(filePathCallback, fileChooserParams)
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        onWebPermissionRequested(request)
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
        super.onPermissionRequestCanceled(request)
        if (request == null) return
        onWebPermissionCanceled(request)
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        val parentWebView = view ?: return false
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false

        // Use a lightweight transient WebView to resolve the target URL of window.open / target="_blank"
        // and then route it through UrlRouter (in-app vs Chrome Custom Tabs).
        val transientWebView = WebView(parentWebView.context).apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    childView: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString()
                    if (!targetUrl.isNullOrBlank()) {
                        onPopupUrlResolved(targetUrl)
                    }
                    childView?.destroy()
                    return true
                }
            }
        }

        transport.webView = transientWebView
        resultMsg.sendToTarget()
        return true
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view == null || callback == null) {
            super.onShowCustomView(view, callback)
            return
        }
        onEnterFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        super.onHideCustomView()
        onExitFullscreen()
    }
}
