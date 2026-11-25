package com.antigravity.wrapper

import android.Manifest
import android.app.DownloadManager
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
import android.view.View
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.antigravity.wrapper.data.AppDestination
import com.antigravity.wrapper.data.ThemeMode
import com.antigravity.wrapper.ui.AntigravityApp
import com.antigravity.wrapper.ui.state.MainViewModel
import com.antigravity.wrapper.web.AntigravityWebChromeClient
import com.antigravity.wrapper.web.AntigravityWebViewClient
import com.antigravity.wrapper.web.DownloadHandler
import com.antigravity.wrapper.web.UrlRouter
import com.antigravity.wrapper.web.WebViewController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val webViewController = WebViewController()
    private val snackbarHostState = SnackbarHostState()

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var isSplashReleased = false

    private var fullscreenWebView by mutableStateOf<View?>(null)
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

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

        configureSystemBars(isDark = true)
        setupBackNavigation()

        val initialUrl = extractAllowedDeepLink(intent) ?: UrlRouter.START_URL

        setContent {
            AntigravityApp(
                viewModel = viewModel,
                webViewController = webViewController,
                snackbarHostState = snackbarHostState,
                initialUrl = initialUrl,
                fullscreenWebView = fullscreenWebView,
                onConfigureSystemBars = { isDark -> configureSystemBars(isDark) },
                onKeepScreenAwakeChanged = { keepAwake -> updateKeepScreenAwake(keepAwake) },
                onBuildWebViewClient = { buildWebViewClient() },
                onBuildWebChromeClient = { buildWebChromeClient() },
                onBuildDownloadHandler = { buildDownloadHandler() },
                onShareUrl = { title, url -> shareCurrentPage(title, url) },
                onCopyToClipboard = { label, text -> copyToClipboard(label, text) },
                onOpenExternalUrl = { url -> openInCustomTabsOrBrowser(url) },
                onOpenSystemDownloads = { openSystemDownloads() },
                onWebViewCreated = {
                    val settings = viewModel.settings.value
                    val darkEffective = isDarkForMode(settings.themeMode)
                    webViewController.applySettings(
                        settings = settings,
                        isDarkEffective = darkEffective,
                        reloadIfViewportChanged = false
                    )
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val deepLink = extractAllowedDeepLink(intent)
        if (deepLink != null) {
            viewModel.clearError()
            viewModel.selectDestination(AppDestination.WORKSPACE)
            webViewController.loadUrl(deepLink)
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
            ThemeMode.DARK, ThemeMode.AMOLED -> true
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
                viewModel.onPageStarted(url)
            },
            onPageFinishedCallback = { url, hadMainFrameError ->
                isSplashReleased = true
                webViewController.setRefreshing(false)
                viewModel.onPageFinished(url, hadMainFrameError)
                if (!hadMainFrameError) {
                    val settings = viewModel.settings.value
                    webViewController.injectMobilePolish(
                        settings = settings,
                        isDarkEffective = isDarkForMode(settings.themeMode)
                    )
                }
            },
            onNavigationStateChanged = { url, canGoBack, canGoForward ->
                viewModel.onNavigationStateChanged(url, canGoBack, canGoForward)
            },
            onMainFrameErrorCallback = { failedUrl, errorCode, description ->
                isSplashReleased = true
                webViewController.setRefreshing(false)
                viewModel.onMainFrameError(
                    failedUrl = failedUrl,
                    errorCode = errorCode,
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
            onProgressUpdate = { progress ->
                viewModel.onProgressChanged(progress)
                if (progress >= 100) {
                    isSplashReleased = true
                    webViewController.setRefreshing(false)
                }
            },
            onTitleChanged = { title ->
                viewModel.onTitleChanged(title)
            },
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
            onPopupUrlResolved = { url ->
                handlePopupUrl(url)
            },
            onEnterFullscreen = { view, callback ->
                enterFullscreenView(view, callback)
            },
            onExitFullscreen = {
                exitFullscreenView()
            }
        )
    }

    private fun buildDownloadHandler(): DownloadHandler {
        return DownloadHandler(
            context = this,
            onDownloadRecorded = { id, fileName, url, mimeType ->
                viewModel.recordDownload(id, fileName, url, mimeType)
            },
            onMessage = { message ->
                showSnackbar(message)
            }
        )
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val state = viewModel.uiState.value
                    when {
                        fullscreenWebView != null -> exitFullscreenView()
                        state.isQuickActionsOpen -> viewModel.setQuickActionsOpen(false)
                        state.isFindBarOpen -> {
                            viewModel.setFindBarOpen(false)
                            webViewController.clearFind()
                        }
                        state.activeDestination != AppDestination.WORKSPACE -> {
                            viewModel.selectDestination(AppDestination.WORKSPACE)
                        }
                        state.isImmersiveMode -> viewModel.setImmersiveMode(false)
                        webViewController.canGoBack() -> {
                            viewModel.clearError()
                            webViewController.goBack()
                        }
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
        if (acceptTypes.isNullOrEmpty()) {
            return arrayOf("*/*")
        }

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

        return if (resolved.isEmpty() || resolved.contains("*/*")) {
            arrayOf("*/*")
        } else {
            resolved.toTypedArray()
        }
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

            val neededAndroidPermissions = mutableListOf<String>()
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in requestedResources &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                neededAndroidPermissions.add(Manifest.permission.CAMERA)
            }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in requestedResources &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                neededAndroidPermissions.add(Manifest.permission.RECORD_AUDIO)
            }

            if (neededAndroidPermissions.isEmpty()) {
                resolveWebPermissionRequest(request, emptyMap())
            } else {
                pendingWebPermissionRequest?.deny()
                pendingWebPermissionRequest = request
                runtimePermissionLauncher.launch(neededAndroidPermissions.toTypedArray())
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
            UrlRouter.Route.IN_APP -> {
                viewModel.clearError()
                viewModel.selectDestination(AppDestination.WORKSPACE)
                webViewController.loadUrl(url)
            }
            UrlRouter.Route.EXTERNAL_BROWSER -> openInCustomTabsOrBrowser(url)
            UrlRouter.Route.SYSTEM_INTENT -> openSystemIntent(url)
        }
    }

    private fun shareCurrentPage(title: String, url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
        } catch (_: ActivityNotFoundException) {
            showSnackbar(getString(R.string.external_link_error))
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun openSystemDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (_: ActivityNotFoundException) {
            showSnackbar(getString(R.string.downloads_app_error))
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

    private fun enterFullscreenView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenWebView != null) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenWebView = view
        fullscreenCallback = callback
        viewModel.setFullscreenWebView(true)
    }

    private fun exitFullscreenView() {
        if (fullscreenWebView == null) return
        fullscreenWebView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        viewModel.setFullscreenWebView(false)
    }

    private fun showSnackbar(message: String) {
        lifecycleScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    override fun onResume() {
        super.onResume()
        webViewController.onResume()
    }

    override fun onPause() {
        webViewController.onPause()
        super.onPause()
    }

    override fun onStop() {
        webViewController.flushCookies()
        super.onStop()
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        pendingWebPermissionRequest?.deny()
        pendingWebPermissionRequest = null
        exitFullscreenView()
        webViewController.destroy()
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_MAX_HOLD_MS = 900L
    }
}
