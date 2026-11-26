package com.antigravity.wrapper

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.SnackbarHostState
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.antigravity.wrapper.bridge.AntigravityBridgeEngine
import com.antigravity.wrapper.data.ThemeMode
import com.antigravity.wrapper.ui.AntigravityNativeApp
import com.antigravity.wrapper.ui.state.ChatViewModel
import com.antigravity.wrapper.web.AntigravityWebChromeClient
import com.antigravity.wrapper.web.AntigravityWebViewClient
import com.antigravity.wrapper.web.DownloadHandler
import com.antigravity.wrapper.web.UrlRouter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private val snackbarHostState = SnackbarHostState()

    private lateinit var bridgeEngine: AntigravityBridgeEngine

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var isSplashReleased = false

    private val openDocumentsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback != null) {
            val result = if (uris.isNotEmpty()) uris.toTypedArray() else null
            callback.onReceiveValue(result)
        }
    }

    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val request = pendingWebPermissionRequest ?: return@registerForActivityResult
        pendingWebPermissionRequest = null
        resolveWebPermissionRequest(request, grantResults)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { !isSplashReleased }
        window.decorView.postDelayed({ isSplashReleased = true }, SPLASH_MAX_HOLD_MS)

        bridgeEngine = AntigravityBridgeEngine(
            onSnapshotReceived = { snapshot ->
                isSplashReleased = true
                viewModel.onBridgeSnapshot(snapshot)
            }
        )

        configureSystemBars(isDark = true)
        setupBackNavigation()

        val initialUrl = extractAllowedDeepLink(intent) ?: UrlRouter.START_URL

        setContent {
            AntigravityNativeApp(
                viewModel = viewModel,
                bridgeEngine = bridgeEngine,
                snackbarHostState = snackbarHostState,
                initialUrl = initialUrl,
                onConfigureSystemBars = { isDark -> configureSystemBars(isDark) },
                onKeepScreenAwakeChanged = { keepAwake -> updateKeepScreenAwake(keepAwake) },
                onBuildWebViewClient = { buildWebViewClient() },
                onBuildWebChromeClient = { buildWebChromeClient() },
                onBuildDownloadHandler = { buildDownloadHandler() },
                onCopyToClipboard = { label, text -> copyToClipboard(label, text) },
                onShareText = { text -> shareText(text) },
                onOpenExternalUrl = { url -> openInCustomTabsOrBrowser(url) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val deepLink = extractAllowedDeepLink(intent)
        if (deepLink != null) {
            bridgeEngine.reload(deepLink)
        }
    }

    private fun configureSystemBars(isDark: Boolean) {
        val style = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun updateKeepScreenAwake(keepAwake: Boolean) {
        if (keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun isDarkForMode(mode: ThemeMode): Boolean {
        return when (mode) {
            ThemeMode.DARK, ThemeMode.SLATE, ThemeMode.AMOLED -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> {
                val nightMask = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun buildWebViewClient(): AntigravityWebViewClient {
        return AntigravityWebViewClient(
            onPageStartedCallback = { url ->
                if (isGoogleAuthUrl(url)) {
                    isSplashReleased = true
                    viewModel.onBridgeNavigatingToAuth()
                }
            },
            onPageFinishedCallback = { url, hadMainFrameError ->
                isSplashReleased = true
                if (!hadMainFrameError) {
                    if (isGoogleAuthUrl(url)) {
                        viewModel.onBridgeNavigatingToAuth()
                    } else {
                        bridgeEngine.injectBridgeScript()
                    }
                }
            },
            onNavigationStateChanged = { url, _, _ ->
                if (isGoogleAuthUrl(url)) {
                    viewModel.onBridgeNavigatingToAuth()
                } else {
                    bridgeEngine.injectBridgeScript()
                }
            },
            onMainFrameErrorCallback = { _, _, description ->
                isSplashReleased = true
                viewModel.onBridgeError(
                    description = description,
                    isOffline = !isNetworkAvailable()
                )
            },
            onOpenExternalUrl = { url -> openInCustomTabsOrBrowser(url) },
            onHandleSystemIntent = { url -> openSystemIntent(url) }
        )
    }

    private fun buildWebChromeClient(): AntigravityWebChromeClient {
        return AntigravityWebChromeClient(
            onProgressUpdate = { _ -> },
            onTitleChanged = { _ -> },
            onFileChooserRequested = { callback, params ->
                handleShowFileChooser(callback, params)
            },
            onWebPermissionRequested = { request ->
                handleWebPermissionRequest(request)
            },
            onWebPermissionCanceled = { request ->
                if (pendingWebPermissionRequest == request) {
                    pendingWebPermissionRequest = null
                }
            },
            onPopupUrlResolved = { url -> handlePopupUrl(url) },
            onEnterFullscreen = { _, callback -> callback.onCustomViewHidden() },
            onExitFullscreen = {}
        )
    }

    private fun buildDownloadHandler(): DownloadHandler {
        return DownloadHandler(
            context = this,
            onDownloadRecorded = { _, _, _, _ -> },
            onMessage = { message -> showSnackbar(message) }
        )
    }

    private fun isGoogleAuthUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("accounts.google.") ||
            lower.contains("signin.google.") ||
            lower.contains("consent.google.")
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val state = viewModel.uiState.value
                    val settings = viewModel.settings.value
                    when {
                        settings.webInspectorMode -> viewModel.setWebInspectorMode(false)
                        state.diffReviewData != null -> viewModel.closeDiffReview()
                        state.documentViewerData != null -> viewModel.closeDocumentViewer()
                        state.isWorkspaceSheetOpen -> viewModel.setWorkspaceSheetOpen(false)
                        state.isProjectSheetOpen -> viewModel.setProjectSheetOpen(false)
                        state.isPermissionsSheetOpen -> viewModel.setPermissionsSheetOpen(false)
                        state.isModelSheetOpen -> viewModel.setModelSheetOpen(false)
                        state.isQuotaSheetOpen -> viewModel.setQuotaSheetOpen(false)
                        state.isQuickPromptsSheetOpen -> viewModel.setQuickPromptsSheetOpen(false)
                        state.isSettingsSheetOpen -> viewModel.setSettingsSheetOpen(false)
                        else -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }
        )
    }

    private fun extractAllowedDeepLink(launchIntent: Intent?): String? {
        if (launchIntent?.action != Intent.ACTION_VIEW) return null
        val dataString = launchIntent.dataString ?: return null
        return if (UrlRouter.route(dataString) == UrlRouter.Route.IN_APP) {
            dataString
        } else {
            null
        }
    }

    private fun handleShowFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams?
    ): Boolean {
        bridgeEngine.notifyFileChooserOpened()
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback

        val mimeTypes = resolveAcceptMimeTypes(params?.acceptTypes)
        return try {
            openDocumentsLauncher.launch(mimeTypes)
            true
        } catch (_: ActivityNotFoundException) {
            filePathCallback = null
            callback.onReceiveValue(null)
            showSnackbar(getString(R.string.file_chooser_error))
            false
        }
    }

    private fun resolveAcceptMimeTypes(acceptTypes: Array<String>?): Array<String> {
        if (acceptTypes.isNullOrEmpty()) return arrayOf("*/*")
        val mimeMap = MimeTypeMap.getSingleton()
        val resolved = acceptTypes
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                when {
                    entry == "*/*" -> "*/*"
                    entry.contains("/") -> entry
                    entry.startsWith(".") -> mimeMap.getMimeTypeFromExtension(entry.removePrefix("."))
                    else -> mimeMap.getMimeTypeFromExtension(entry)
                }
            }
            .distinct()
        return if (resolved.isEmpty() || resolved.contains("*/*")) arrayOf("*/*") else resolved.toTypedArray()
    }

    private fun handleWebPermissionRequest(request: PermissionRequest) {
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                request.deny()
                return@runOnUiThread
            }
            if (!UrlRouter.isTrustedPermissionOrigin(request.origin?.toString())) {
                request.deny()
                return@runOnUiThread
            }
            val requestedResources = request.resources ?: emptyArray()
            if (requestedResources.isEmpty()) {
                request.deny()
                return@runOnUiThread
            }
            val neededPermissions = mutableListOf<String>()
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in requestedResources &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                neededPermissions.add(Manifest.permission.CAMERA)
            }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in requestedResources &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                neededPermissions.add(Manifest.permission.RECORD_AUDIO)
            }
            if (neededPermissions.isEmpty()) {
                resolveWebPermissionRequest(request, emptyMap())
            } else {
                pendingWebPermissionRequest?.deny()
                pendingWebPermissionRequest = request
                runtimePermissionLauncher.launch(neededPermissions.toTypedArray())
            }
        }
    }

    private fun resolveWebPermissionRequest(
        request: PermissionRequest,
        freshGrantResults: Map<String, Boolean>
    ) {
        val grantedResources = request.resources.filter { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    freshGrantResults[Manifest.permission.CAMERA] == true ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                }
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    freshGrantResults[Manifest.permission.RECORD_AUDIO] == true ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                }
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
                PermissionRequest.RESOURCE_MIDI_SYSEX -> true
                else -> resource.contains("CLIPBOARD", ignoreCase = true)
            }
        }
        if (grantedResources.isNotEmpty()) {
            request.grant(grantedResources.toTypedArray())
        } else {
            request.deny()
        }
    }

    private fun handlePopupUrl(url: String) {
        when (UrlRouter.route(url)) {
            UrlRouter.Route.IN_APP -> bridgeEngine.reload(url)
            UrlRouter.Route.EXTERNAL_BROWSER -> openInCustomTabsOrBrowser(url)
            UrlRouter.Route.SYSTEM_INTENT -> openSystemIntent(url)
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun shareText(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
        } catch (_: ActivityNotFoundException) {
            showSnackbar(getString(R.string.external_link_error))
        }
    }

    private fun openInCustomTabsOrBrowser(url: String) {
        val uri = Uri.parse(url)
        val isDark = isDarkForMode(viewModel.settings.value.themeMode)
        try {
            val toolbarColor = ContextCompat.getColor(
                this,
                if (isDark) R.color.antigravity_surface else R.color.antigravity_light_surface
            )
            val navColor = ContextCompat.getColor(
                this,
                if (isDark) R.color.antigravity_background else R.color.antigravity_light_background
            )
            val colors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(toolbarColor)
                .setNavigationBarColor(navColor)
                .build()
            val customTabsIntent = CustomTabsIntent.Builder()
                .setColorScheme(
                    if (isDark) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT
                )
                .setDefaultColorSchemeParams(colors)
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(this, uri)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                showSnackbar(getString(R.string.external_link_error))
            }
        }
    }

    private fun openSystemIntent(url: String) {
        try {
            val intent = if (url.startsWith("intent://", ignoreCase = true)) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                if (!fallbackUrl.isNullOrBlank()) {
                    handlePopupUrl(fallbackUrl)
                } else {
                    showSnackbar(getString(R.string.external_link_error))
                }
            }
        } catch (_: Exception) {
            showSnackbar(getString(R.string.external_link_error))
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showSnackbar(message: String) {
        lifecycleScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    override fun onResume() {
        super.onResume()
        bridgeEngine.onResume()
    }

    override fun onPause() {
        bridgeEngine.onPause()
        super.onPause()
    }

    override fun onStop() {
        bridgeEngine.flushCookies()
        super.onStop()
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        pendingWebPermissionRequest?.deny()
        pendingWebPermissionRequest = null
        bridgeEngine.destroy()
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_MAX_HOLD_MS = 750L
    }
}
