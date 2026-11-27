package com.antigravity.wrapper.bridge

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import com.antigravity.wrapper.BuildConfig
import com.antigravity.wrapper.data.BridgeSnapshot
import com.antigravity.wrapper.data.BridgeStateParser
import com.antigravity.wrapper.data.DiffReviewData
import com.antigravity.wrapper.data.DocumentViewerData
import com.antigravity.wrapper.data.WorkspaceArtifactItem
import com.antigravity.wrapper.web.AntigravityWebChromeClient
import com.antigravity.wrapper.web.AntigravityWebViewClient
import com.antigravity.wrapper.web.DownloadHandler
import com.antigravity.wrapper.web.UrlRouter
import com.antigravity.wrapper.web.UserAgentSanitizer
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Controller for the headless Antigravity WebView session.
 * - Keeps `alpha = 1f` behind the opaque Compose root Surface so Chromium never throttles
 *   `BeginMainFrame`, React 18 commits, or streaming responses.
 * - Parses bridge JSON on a background executor with deduplication to keep 60fps scrolling.
 * - Uses trusted Android `MotionEvent`, `InputConnection.commitText`, and `KeyEvent.KEYCODE_ENTER`
 *   during prompt submission so Lexical/ProseMirror always updates its internal editor state cleanly,
 *   then immediately re-locks hidden WebView focus.
 */
class AntigravityBridgeEngine(
    private val onSnapshotReceived: (BridgeSnapshot) -> Unit,
    private val onBridgeLog: (String) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val parserExecutor = Executors.newSingleThreadExecutor()
    private var webView: WebView? = null
    private var interactiveMode: Boolean = false
    private var allowProgrammaticTouch: Boolean = false
    private var pendingSendCallback: ((Boolean, String) -> Unit)? = null
    private var sendUnlockGuard: Runnable? = null
    private var pendingUploadCallback: ((Boolean, String) -> Unit)? = null
    private var uploadUnlockGuard: Runnable? = null
    private var pendingDiffCallback: ((DiffReviewData) -> Unit)? = null
    private var diffTimeoutGuard: Runnable? = null
    private var pendingDocumentCallback: ((DocumentViewerData) -> Unit)? = null
    private var documentTimeoutGuard: Runnable? = null
    private var pendingWorkspaceCallback: ((List<WorkspaceArtifactItem>) -> Unit)? = null
    private var workspaceTimeoutGuard: Runnable? = null

    @Volatile
    private var isDestroyed: Boolean = false

    @Volatile
    private var lastDeliveredSnapshot: BridgeSnapshot? = null

    private val rawSnapshotLock = Any()
    private var lastRawJsonHash: Long = 0L
    private var lastRawJsonLength: Int = -1
    private val pendingSnapshotJson = AtomicReference<String?>(null)
    private val snapshotParseScheduled = AtomicBoolean(false)

    @Volatile
    private var isNativeScrolling: Boolean = false

    @Volatile
    private var allowDeliverDuringScroll: Boolean = false

    private var pendingSnapshotDuringScroll: BridgeSnapshot? = null

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

        // Keep alpha=1f so Chromium's compositor stays active behind our opaque native Surface
        view.alpha = 1f
        view.setOnTouchListener { _, _ -> !interactiveMode && !allowProgrammaticTouch }
        setInteractive(false)

        view.addJavascriptInterface(NativeJsBridge(), "AntigravityNative")
        view.webViewClient = webViewClient
        view.webChromeClient = webChromeClient
        view.setDownloadListener(downloadHandler)
        view.loadUrl(initialUrl.ifBlank { UrlRouter.START_URL })
    }

    fun setInteractive(interactive: Boolean) {
        if (interactiveMode == interactive && webView?.isFocusable == interactive) return
        interactiveMode = interactive
        val view = webView ?: return
        view.isFocusable = interactive
        view.isFocusableInTouchMode = interactive
        view.isClickable = interactive
        view.importantForAccessibility = if (interactive) {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        view.descendantFocusability = if (interactive) {
            ViewGroup.FOCUS_AFTER_DESCENDANTS
        } else {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        if (!interactive && view.hasFocus()) {
            view.clearFocus()
        }
    }

    private fun restoreFocusLock() {
        sendUnlockGuard?.let { mainHandler.removeCallbacks(it) }
        sendUnlockGuard = null
        allowProgrammaticTouch = false
        val view = webView ?: return
        if (!interactiveMode) {
            view.clearFocus()
            view.isFocusable = false
            view.isFocusableInTouchMode = false
            view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }

    private fun performTrustedTap(normX: Float, normY: Float) {
        val view = webView ?: return
        if (view.width <= 0 || view.height <= 0) return
        val px = (normX.coerceIn(0.01f, 0.99f) * view.width)
        val py = (normY.coerceIn(0.01f, 0.99f) * view.height)
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, px, py, 0)
        val up = MotionEvent.obtain(downTime, downTime + 25L, MotionEvent.ACTION_UP, px, py, 0)
        val prevAllow = allowProgrammaticTouch
        allowProgrammaticTouch = true
        try {
            view.dispatchTouchEvent(down)
            view.dispatchTouchEvent(up)
        } finally {
            allowProgrammaticTouch = prevAllow
            down.recycle()
            up.recycle()
        }
    }

    fun injectBridgeScript() {
        webView?.evaluateJavascript(AntigravityBridgeScript.buildBootstrapScript(), null)
    }

    fun syncNow() {
        allowDeliverDuringScroll = true
        webView?.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.syncNow(true) : null;",
            null
        )
    }

    fun sendPrompt(text: String, onResult: (Boolean, String) -> Unit) {
        val view = webView
        if (view == null) {
            onResult(false, "Session not ready")
            return
        }
        allowDeliverDuringScroll = true
        pendingSendCallback = onResult

        // Briefly unlock DOM focus so Chromium's InputConnection + Lexical accept trusted input
        if (!interactiveMode) {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            view.requestFocus()
        }

        // Step 1: Focus the Lexical/ProseMirror contenteditable input in DOM (with trusted tap)
        view.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.focusComposer() : false;"
        ) { _ ->
            // Step 2: Commit text via genuine trusted Android InputConnection, then run JS verification & submit
            mainHandler.postDelayed({
                try {
                    val ic = view.onCreateInputConnection(EditorInfo())
                    ic?.commitText(text, 1)
                } catch (_: Exception) {
                    // JS fallback will handle insertion if InputConnection is unavailable
                }

                val escaped = AntigravityBridgeScript.escapeJsString(text)
                view.evaluateJavascript(
                    "window.__agBridge ? window.__agBridge.submitPromptAfterCommit(\"$escaped\") : false;",
                    null
                )
            }, 130L)
        }

        // Safety timeout to always restore focus lock
        sendUnlockGuard?.let { mainHandler.removeCallbacks(it) }
        val guard = Runnable {
            restoreFocusLock()
            val cb = pendingSendCallback
            pendingSendCallback = null
            cb?.invoke(true, "")
        }
        sendUnlockGuard = guard
        mainHandler.postDelayed(guard, 2_600L)
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
        allowDeliverDuringScroll = true
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
        allowDeliverDuringScroll = true
        webView?.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.startNewChat() : (window.location.href='${UrlRouter.START_URL}');",
            null
        )
    }

    fun selectConversation(url: String, title: String) {
        allowDeliverDuringScroll = true
        val escapedUrl = AntigravityBridgeScript.escapeJsString(url)
        val escapedTitle = AntigravityBridgeScript.escapeJsString(title)
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.selectConversation(\"$escapedUrl\", \"$escapedTitle\");",
            null
        )
    }

    fun loadOlderMessages() {
        allowDeliverDuringScroll = true
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.loadOlderMessages();",
            null
        )
    }

    private inline fun executeOnParser(crossinline block: () -> Unit) {
        if (isDestroyed || parserExecutor.isShutdown) return
        try {
            parserExecutor.execute {
                if (!isDestroyed) {
                    block()
                }
            }
        } catch (_: RejectedExecutionException) {
            // Engine is tearing down; safely ignore late callbacks
        }
    }

    fun openReviewDiffs(cardTitle: String = "", onResult: (DiffReviewData) -> Unit) {
        val view = webView
        if (view == null || isDestroyed) {
            onResult(DiffReviewData(error = "Session not ready"))
            return
        }
        diffTimeoutGuard?.let { mainHandler.removeCallbacks(it) }
        pendingDiffCallback = onResult
        val guard = Runnable {
            diffTimeoutGuard = null
            val cb = pendingDiffCallback
            pendingDiffCallback = null
            cb?.invoke(
                DiffReviewData(
                    title = cardTitle.ifBlank { "Review Changes" },
                    error = "Review request timed out — session may still be connecting"
                )
            )
        }
        diffTimeoutGuard = guard
        mainHandler.postDelayed(guard, SHEET_BRIDGE_TIMEOUT_MS)

        val escaped = AntigravityBridgeScript.escapeJsString(cardTitle)
        view.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.openReviewDiffs(\"$escaped\") : false;"
        ) { raw ->
            if (raw == "false" || raw == "null") {
                mainHandler.removeCallbacks(guard)
                diffTimeoutGuard = null
                val cb = pendingDiffCallback
                pendingDiffCallback = null
                cb?.invoke(
                    DiffReviewData(
                        title = cardTitle.ifBlank { "Review Changes" },
                        error = "Session bridge not ready — try again in a moment"
                    )
                )
            }
        }
    }

    fun openDocumentViewer(title: String, onResult: (DocumentViewerData) -> Unit) {
        val view = webView
        if (view == null || isDestroyed) {
            onResult(DocumentViewerData(title = title, error = "Session not ready"))
            return
        }
        documentTimeoutGuard?.let { mainHandler.removeCallbacks(it) }
        pendingDocumentCallback = onResult
        val guard = Runnable {
            documentTimeoutGuard = null
            val cb = pendingDocumentCallback
            pendingDocumentCallback = null
            cb?.invoke(
                DocumentViewerData(
                    title = title,
                    error = "Document request timed out — session may still be connecting"
                )
            )
        }
        documentTimeoutGuard = guard
        mainHandler.postDelayed(guard, SHEET_BRIDGE_TIMEOUT_MS)

        val escaped = AntigravityBridgeScript.escapeJsString(title)
        view.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.openDocumentViewer(\"$escaped\") : false;"
        ) { raw ->
            if (raw == "false" || raw == "null") {
                mainHandler.removeCallbacks(guard)
                documentTimeoutGuard = null
                val cb = pendingDocumentCallback
                pendingDocumentCallback = null
                cb?.invoke(
                    DocumentViewerData(
                        title = title,
                        error = "Session bridge not ready — try again in a moment"
                    )
                )
            }
        }
    }

    fun openExternalArtifact(cardTitle: String) {
        val escaped = AntigravityBridgeScript.escapeJsString(cardTitle)
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.openExternalArtifact(\"$escaped\");",
            null
        )
    }

    fun expandReasoningBlock(blockId: String, blockTitle: String = "") {
        val escapedId = AntigravityBridgeScript.escapeJsString(blockId)
        val escapedTitle = AntigravityBridgeScript.escapeJsString(blockTitle)
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.expandReasoningBlock(\"$escapedId\", \"$escapedTitle\");",
            null
        )
    }

    fun expandReasoningStep(
        blockId: String,
        sectionId: String = "",
        stepId: String = "",
        stepTitle: String = ""
    ) {
        val bId = AntigravityBridgeScript.escapeJsString(blockId)
        val secId = AntigravityBridgeScript.escapeJsString(sectionId)
        val stId = AntigravityBridgeScript.escapeJsString(stepId)
        val stTitle = AntigravityBridgeScript.escapeJsString(stepTitle)
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.expandReasoningStep(\"$bId\", \"$secId\", \"$stId\", \"$stTitle\");",
            null
        )
    }

    fun fetchWorkspaceOverview(onResult: (List<WorkspaceArtifactItem>) -> Unit) {
        val view = webView
        if (view == null || isDestroyed) {
            onResult(emptyList())
            return
        }
        workspaceTimeoutGuard?.let { mainHandler.removeCallbacks(it) }
        pendingWorkspaceCallback = onResult
        val guard = Runnable {
            workspaceTimeoutGuard = null
            val cb = pendingWorkspaceCallback
            pendingWorkspaceCallback = null
            cb?.invoke(emptyList())
        }
        workspaceTimeoutGuard = guard
        mainHandler.postDelayed(guard, SHEET_BRIDGE_TIMEOUT_MS)

        view.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.fetchWorkspaceOverview() : false;"
        ) { raw ->
            if (raw == "false" || raw == "null") {
                mainHandler.removeCallbacks(guard)
                workspaceTimeoutGuard = null
                val cb = pendingWorkspaceCallback
                pendingWorkspaceCallback = null
                cb?.invoke(emptyList())
            }
        }
    }

    fun refreshProjects() {
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.refreshProjects();",
            null
        )
    }

    fun selectProject(projectName: String) {
        val escaped = AntigravityBridgeScript.escapeJsString(projectName)
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.selectProject(\"$escaped\");",
            null
        )
    }

    fun selectPermissions(preset: String) {
        val escaped = AntigravityBridgeScript.escapeJsString(preset)
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.selectPermissions(\"$escaped\");",
            null
        )
    }

    fun runThreadAction(action: String, argument: String = "") {
        val escapedAction = AntigravityBridgeScript.escapeJsString(action)
        val escapedArg = AntigravityBridgeScript.escapeJsString(argument)
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.runThreadAction(\"$escapedAction\", \"$escapedArg\");",
            null
        )
    }

    fun notifyFileChooserOpened() {
        mainHandler.post {
            uploadUnlockGuard?.let { mainHandler.removeCallbacks(it) }
            uploadUnlockGuard = null
            restoreFocusLock()
            val cb = pendingUploadCallback
            pendingUploadCallback = null
            cb?.invoke(true, "")
        }
    }

    fun triggerFileUpload(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val view = webView
        if (view == null || isDestroyed) {
            onResult(false, "Session not ready")
            return
        }
        pendingUploadCallback = onResult
        if (!interactiveMode) {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            view.requestFocus()
        }
        uploadUnlockGuard?.let { mainHandler.removeCallbacks(it) }
        val guard = Runnable {
            uploadUnlockGuard = null
            restoreFocusLock()
            val cb = pendingUploadCallback
            pendingUploadCallback = null
            cb?.invoke(false, "File upload is unavailable in this view")
        }
        uploadUnlockGuard = guard
        mainHandler.postDelayed(guard, 2_400L)

        view.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.triggerFileUpload() : false;"
        ) { raw ->
            if (raw == "false" || raw == "null") {
                mainHandler.removeCallbacks(guard)
                uploadUnlockGuard = null
                restoreFocusLock()
                val cb = pendingUploadCallback
                pendingUploadCallback = null
                cb?.invoke(false, "File upload is unavailable in this view")
            }
        }
    }

    fun exportDomSnapshot(onResult: (String?) -> Unit) {
        val view = webView
        if (view == null || isDestroyed) {
            onResult(null)
            return
        }
        view.evaluateJavascript(
            "window.__agBridge ? window.__agBridge.dumpDomSnapshot() : '';"
        ) { raw ->
            executeOnParser {
                val decoded = decodeJsStringResult(raw)?.takeIf { it.isNotBlank() }
                if (BuildConfig.DEBUG && !decoded.isNullOrBlank()) {
                    var offset = 0
                    while (offset < decoded.length) {
                        val end = minOf(offset + 3000, decoded.length)
                        android.util.Log.d("AG_DOM", decoded.substring(offset, end))
                        offset = end
                    }
                }
                mainHandler.post { onResult(decoded) }
            }
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
            lastDeliveredSnapshot = null
            synchronized(rawSnapshotLock) {
                lastRawJsonHash = 0L
                lastRawJsonLength = -1
            }
            pendingSnapshotJson.set(null)
            webView?.apply {
                clearCache(true)
                clearHistory()
                loadUrl(UrlRouter.START_URL)
            }
        }
    }

    fun setScrolling(scrolling: Boolean) {
        if (isNativeScrolling == scrolling) return
        isNativeScrolling = scrolling
        val flag = if (scrolling) "true" else "false"
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.setScrolling && window.__agBridge.setScrolling($flag);",
            null
        )
        if (!scrolling) {
            val deferred = pendingSnapshotDuringScroll
            pendingSnapshotDuringScroll = null
            if (deferred != null && !isDestroyed) {
                onSnapshotReceived(deferred)
            }
        }
    }

    fun onResume() {
        webView?.onResume()
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.setPaused && window.__agBridge.setPaused(false);",
            null
        )
    }

    fun onPause() {
        webView?.evaluateJavascript(
            "window.__agBridge && window.__agBridge.setPaused && window.__agBridge.setPaused(true);",
            null
        )
        webView?.onPause()
        CookieManager.getInstance().flush()
    }

    fun flushCookies() {
        CookieManager.getInstance().flush()
    }

    fun destroy() {
        isDestroyed = true
        uploadUnlockGuard?.let { mainHandler.removeCallbacks(it) }
        uploadUnlockGuard = null
        sendUnlockGuard?.let { mainHandler.removeCallbacks(it) }
        sendUnlockGuard = null
        diffTimeoutGuard?.let { mainHandler.removeCallbacks(it) }
        diffTimeoutGuard = null
        documentTimeoutGuard?.let { mainHandler.removeCallbacks(it) }
        documentTimeoutGuard = null
        workspaceTimeoutGuard?.let { mainHandler.removeCallbacks(it) }
        workspaceTimeoutGuard = null
        pendingSendCallback = null
        pendingUploadCallback = null
        pendingDiffCallback = null
        pendingDocumentCallback = null
        pendingWorkspaceCallback = null
        pendingSnapshotJson.set(null)
        parserExecutor.shutdownNow()
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

    private fun computePayloadHash64(s: String): Long {
        var h = -3750763034362895579L // FNV-1a 64-bit offset basis
        for (i in 0 until s.length) {
            h = (h xor s[i].code.toLong()) * 1099511628211L
        }
        return h
    }

    private fun scheduleSnapshotDrain() {
        if (!snapshotParseScheduled.compareAndSet(false, true)) return
        executeOnParser {
            try {
                while (!isDestroyed) {
                    val json = pendingSnapshotJson.getAndSet(null) ?: break
                    val len = json.length
                    val hash = computePayloadHash64(json)
                    val alreadyParsed = synchronized(rawSnapshotLock) {
                        if (len == lastRawJsonLength && hash == lastRawJsonHash) {
                            true
                        } else {
                            lastRawJsonLength = len
                            lastRawJsonHash = hash
                            false
                        }
                    }
                    if (alreadyParsed) continue

                    val snapshot = BridgeStateParser.parseSnapshot(json) ?: continue
                    if (snapshot == lastDeliveredSnapshot) continue
                    lastDeliveredSnapshot = snapshot
                    mainHandler.post {
                        if (isDestroyed) return@post
                        val bypassScroll = allowDeliverDuringScroll
                        if (bypassScroll) {
                            allowDeliverDuringScroll = false
                        }
                        if (isNativeScrolling && !bypassScroll && !snapshot.authRequired) {
                            pendingSnapshotDuringScroll = snapshot
                        } else {
                            pendingSnapshotDuringScroll = null
                            onSnapshotReceived(snapshot)
                        }
                    }
                }
            } finally {
                snapshotParseScheduled.set(false)
                // Re-arm if a newer snapshot arrived right before clearing snapshotParseScheduled
                if (!isDestroyed && pendingSnapshotJson.get() != null) {
                    scheduleSnapshotDrain()
                }
            }
        }
    }

    private inner class NativeJsBridge {
        @JavascriptInterface
        fun onStateSnapshot(json: String?) {
            if (json.isNullOrBlank() || isDestroyed) return
            pendingSnapshotJson.set(json)
            scheduleSnapshotDrain()
        }

        @JavascriptInterface
        fun dispatchTrustedTap(normX: Double, normY: Double) {
            mainHandler.post {
                performTrustedTap(normX.toFloat(), normY.toFloat())
            }
        }

        @JavascriptInterface
        fun dispatchTrustedEnter() {
            mainHandler.post {
                val view = webView ?: return@post
                view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }

        @JavascriptInterface
        fun onPromptSendResult(ok: Boolean, reason: String?) {
            mainHandler.post {
                restoreFocusLock()
                val cb = pendingSendCallback
                pendingSendCallback = null
                cb?.invoke(ok, reason.orEmpty())
            }
        }

        @JavascriptInterface
        fun onFileUploadResult(ok: Boolean, reason: String?) {
            mainHandler.post {
                if (!ok) {
                    uploadUnlockGuard?.let { mainHandler.removeCallbacks(it) }
                    uploadUnlockGuard = null
                    restoreFocusLock()
                    val cb = pendingUploadCallback
                    pendingUploadCallback = null
                    cb?.invoke(false, reason.orEmpty())
                }
            }
        }

        @JavascriptInterface
        fun onDiffReviewLoaded(json: String?) {
            if (isDestroyed) return
            executeOnParser {
                val parsed = BridgeStateParser.parseDiffReview(json)
                mainHandler.post {
                    diffTimeoutGuard?.let { mainHandler.removeCallbacks(it) }
                    diffTimeoutGuard = null
                    val cb = pendingDiffCallback
                    pendingDiffCallback = null
                    cb?.invoke(parsed)
                }
            }
        }

        @JavascriptInterface
        fun onDocumentLoaded(json: String?) {
            if (isDestroyed) return
            executeOnParser {
                val parsed = BridgeStateParser.parseDocumentViewer(json)
                mainHandler.post {
                    documentTimeoutGuard?.let { mainHandler.removeCallbacks(it) }
                    documentTimeoutGuard = null
                    val cb = pendingDocumentCallback
                    pendingDocumentCallback = null
                    cb?.invoke(parsed)
                }
            }
        }

        @JavascriptInterface
        fun onWorkspaceOverviewLoaded(json: String?) {
            if (isDestroyed) return
            executeOnParser {
                val parsed = BridgeStateParser.parseWorkspaceArtifacts(json)
                mainHandler.post {
                    workspaceTimeoutGuard?.let { mainHandler.removeCallbacks(it) }
                    workspaceTimeoutGuard = null
                    val cb = pendingWorkspaceCallback
                    pendingWorkspaceCallback = null
                    cb?.invoke(parsed)
                }
            }
        }

        @JavascriptInterface
        fun onBridgeLog(message: String?) {
            if (!message.isNullOrBlank()) {
                mainHandler.post { onBridgeLog(message) }
            }
        }
    }

    companion object {
        private const val SHEET_BRIDGE_TIMEOUT_MS = 10_000L
    }
}
