package com.antigravity.wrapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.antigravity.wrapper.databinding.ActivityMainBinding
import com.antigravity.wrapper.web.AntigravityWebChromeClient
import com.antigravity.wrapper.web.AntigravityWebViewClient
import com.antigravity.wrapper.web.DownloadHandler
import com.antigravity.wrapper.web.UrlRouter
import com.antigravity.wrapper.web.UserAgentSanitizer
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var lastFailedUrl: String? = null
    private var isInitialLoadComplete = false

    private var customFullscreenView: View? = null
    private var customFullscreenCallback: WebChromeClient.CustomViewCallback? = null

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

        // Keep the splash screen briefly until initial WebView navigation settles
        splashScreen.setKeepOnScreenCondition { !isInitialLoadComplete }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupSwipeRefresh()
        setupErrorView()
        setupWebView()
        setupBackNavigation()

        // Release splash screen if the network is slow so the user sees the progress indicator
        binding.rootContainer.postDelayed({
            isInitialLoadComplete = true
        }, SPLASH_MAX_HOLD_MS)

        if (savedInstanceState != null) {
            val restored = binding.webView.restoreState(savedInstanceState)
            if (restored == null) {
                loadInitialUrl(intent)
            }
        } else {
            loadInitialUrl(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val deepLink = extractAllowedDeepLink(intent)
        if (deepLink != null) {
            hideErrorView()
            binding.webView.loadUrl(deepLink)
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )
            windowInsets
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.apply {
            setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(this@MainActivity, R.color.antigravity_surface)
            )
            setColorSchemeColors(
                ContextCompat.getColor(this@MainActivity, R.color.antigravity_primary)
            )
            // Trigger pull-to-refresh only when the WebView is scrolled to the very top
            setOnChildScrollUpCallback { _, _ ->
                binding.webView.scrollY > 0
            }
            setOnRefreshListener {
                reloadCurrentOrStartPage()
            }
        }
    }

    private fun setupErrorView() {
        binding.retryButton.setOnClickListener {
            hideErrorView()
            reloadCurrentOrStartPage()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val webView = binding.webView
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.antigravity_background))
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val defaultUserAgent = try {
            WebSettings.getDefaultUserAgent(this)
        } catch (_: Exception) {
            webView.settings.userAgentString
        }
        val chromeLikeUserAgent = UserAgentSanitizer.sanitize(defaultUserAgent)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            userAgentString = chromeLikeUserAgent

            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowContentAccess = true
            allowFileAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(true)
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = AntigravityWebViewClient(
            onPageStartedCallback = { _ ->
                binding.loadingProgressBar.isVisible = true
                if (binding.loadingProgressBar.progress < 10) {
                    binding.loadingProgressBar.setProgressCompat(10, false)
                }
            },
            onPageFinishedCallback = { _, hadMainFrameError ->
                isInitialLoadComplete = true
                binding.swipeRefreshLayout.isRefreshing = false
                binding.loadingProgressBar.isVisible = false
                if (!hadMainFrameError) {
                    lastFailedUrl = null
                    hideErrorView()
                }
            },
            onMainFrameErrorCallback = { failedUrl, errorCode, description ->
                isInitialLoadComplete = true
                lastFailedUrl = failedUrl ?: webView.url ?: UrlRouter.START_URL
                binding.swipeRefreshLayout.isRefreshing = false
                binding.loadingProgressBar.isVisible = false
                showErrorView(errorCode, description)
            },
            onOpenExternalUrl = { url ->
                openInCustomTabsOrBrowser(url)
            },
            onHandleSystemIntent = { url ->
                openSystemIntent(url)
            }
        )

        webView.webChromeClient = AntigravityWebChromeClient(
            onProgressUpdate = { progress ->
                updateLoadingProgress(progress)
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

        webView.setDownloadListener(
            DownloadHandler(this) { message ->
                showSnackbar(message)
            }
        )
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (customFullscreenView != null) {
                        exitFullscreenView()
                        return
                    }

                    if (binding.webView.canGoBack()) {
                        hideErrorView()
                        binding.webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun loadInitialUrl(launchIntent: Intent?) {
        val targetUrl = extractAllowedDeepLink(launchIntent) ?: UrlRouter.START_URL
        binding.webView.loadUrl(targetUrl)
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

    private fun reloadCurrentOrStartPage() {
        val urlToLoad = lastFailedUrl
            ?: binding.webView.url?.takeIf { !it.startsWith("chrome-error://") && it != "about:blank" }
            ?: UrlRouter.START_URL

        if (lastFailedUrl != null || binding.webView.url.isNullOrBlank() ||
            binding.webView.url?.startsWith("chrome-error://") == true
        ) {
            binding.webView.loadUrl(urlToLoad)
        } else {
            binding.webView.reload()
        }
    }

    private fun updateLoadingProgress(progress: Int) {
        if (progress >= 100) {
            binding.loadingProgressBar.setProgressCompat(100, true)
            binding.loadingProgressBar.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    binding.loadingProgressBar.isVisible = false
                }
            }, 150L)
            binding.swipeRefreshLayout.isRefreshing = false
        } else {
            binding.loadingProgressBar.isVisible = true
            binding.loadingProgressBar.setProgressCompat(progress.coerceAtLeast(8), true)
        }
    }

    private fun handleShowFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams?
    ): Boolean {
        // Cancel any previously pending callback so the WebView never stalls
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
                else -> {
                    // Allow trusted-origin browser permissions such as clipboard read/write
                    resource.contains("CLIPBOARD", ignoreCase = true)
                }
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
                hideErrorView()
                binding.webView.loadUrl(url)
            }
            UrlRouter.Route.EXTERNAL_BROWSER -> openInCustomTabsOrBrowser(url)
            UrlRouter.Route.SYSTEM_INTENT -> openSystemIntent(url)
        }
    }

    private fun openInCustomTabsOrBrowser(url: String) {
        val uri = Uri.parse(url)
        try {
            val darkColors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(this, R.color.antigravity_surface))
                .setNavigationBarColor(ContextCompat.getColor(this, R.color.antigravity_background))
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setDefaultColorSchemeParams(darkColors)
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

    private fun showErrorView(errorCode: Int, description: String?) {
        val offline = !isNetworkAvailable()
        binding.errorTitle.setText(
            if (offline) R.string.error_offline_title else R.string.error_load_title
        )
        binding.errorDescription.setText(
            if (offline) R.string.error_offline_description else R.string.error_load_description
        )
        if (!offline && !description.isNullOrBlank()) {
            binding.errorDetail.text = getString(R.string.error_details_format, description, errorCode)
            binding.errorDetail.isVisible = true
        } else {
            binding.errorDetail.isVisible = false
        }
        binding.errorContainer.isVisible = true
        binding.swipeRefreshLayout.isVisible = false
    }

    private fun hideErrorView() {
        binding.errorContainer.isVisible = false
        binding.swipeRefreshLayout.isVisible = true
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
        if (customFullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        customFullscreenView = view
        customFullscreenCallback = callback
        binding.fullscreenContainer.addView(view)
        binding.fullscreenContainer.isVisible = true
        binding.swipeRefreshLayout.isVisible = false
    }

    private fun exitFullscreenView() {
        val view = customFullscreenView ?: return
        binding.fullscreenContainer.removeView(view)
        binding.fullscreenContainer.isVisible = false
        binding.swipeRefreshLayout.isVisible = !binding.errorContainer.isVisible
        customFullscreenCallback?.onCustomViewHidden()
        customFullscreenView = null
        customFullscreenCallback = null
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.rootContainer, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        binding.webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onStop() {
        CookieManager.getInstance().flush()
        super.onStop()
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        pendingWebPermissionRequest?.deny()
        pendingWebPermissionRequest = null
        exitFullscreenView()

        binding.webView.apply {
            stopLoading()
            setDownloadListener(null)
            webChromeClient = null
            webViewClient = android.webkit.WebViewClient()
            destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_MAX_HOLD_MS = 1200L
    }
}
