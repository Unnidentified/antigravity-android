package com.antigravity.wrapper.ui

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.antigravity.wrapper.BuildConfig
import com.antigravity.wrapper.data.AppDestination
import com.antigravity.wrapper.data.BookmarkItem
import com.antigravity.wrapper.data.QuickPrompt
import com.antigravity.wrapper.ui.components.AntigravityBottomBar
import com.antigravity.wrapper.ui.components.AntigravityDrawerSheet
import com.antigravity.wrapper.ui.components.ImmersiveExitPill
import com.antigravity.wrapper.ui.components.NativeErrorScreen
import com.antigravity.wrapper.ui.components.NativeLoadingSkeleton
import com.antigravity.wrapper.ui.components.QuickActionsSheet
import com.antigravity.wrapper.ui.components.SectionTopBar
import com.antigravity.wrapper.ui.components.WorkspaceTopBar
import com.antigravity.wrapper.ui.screens.BookmarksScreen
import com.antigravity.wrapper.ui.screens.DownloadsScreen
import com.antigravity.wrapper.ui.screens.SettingsScreen
import com.antigravity.wrapper.ui.state.MainViewModel
import com.antigravity.wrapper.ui.theme.AntigravityTheme
import com.antigravity.wrapper.ui.theme.isEffectiveDarkTheme
import com.antigravity.wrapper.web.AntigravityWebChromeClient
import com.antigravity.wrapper.web.AntigravityWebViewClient
import com.antigravity.wrapper.web.DownloadHandler
import com.antigravity.wrapper.web.UrlRouter
import com.antigravity.wrapper.web.UserAgentSanitizer
import com.antigravity.wrapper.web.WebViewController
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AntigravityApp(
    viewModel: MainViewModel,
    webViewController: WebViewController,
    snackbarHostState: SnackbarHostState,
    initialUrl: String,
    fullscreenWebView: View?,
    onConfigureSystemBars: (isDark: Boolean) -> Unit,
    onKeepScreenAwakeChanged: (Boolean) -> Unit,
    onBuildWebViewClient: () -> AntigravityWebViewClient,
    onBuildWebChromeClient: () -> AntigravityWebChromeClient,
    onBuildDownloadHandler: () -> DownloadHandler,
    onShareUrl: (title: String, url: String) -> Unit,
    onCopyToClipboard: (label: String, text: String) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onOpenSystemDownloads: () -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val quickPrompts by viewModel.quickPrompts.collectAsStateWithLifecycle()

    val isDarkEffective = isEffectiveDarkTheme(settings.themeMode)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val isImeVisible = WindowInsets.isImeVisible

    fun triggerHaptic() {
        if (settings.hapticFeedbackEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(isDarkEffective) {
        onConfigureSystemBars(isDarkEffective)
    }

    LaunchedEffect(settings.keepScreenAwake) {
        onKeepScreenAwakeChanged(settings.keepScreenAwake)
    }

    LaunchedEffect(settings, isDarkEffective) {
        webViewController.applySettings(settings, isDarkEffective)
    }

    val isCurrentBookmarked = remember(bookmarks, uiState.currentUrl) {
        bookmarks.any { it.url == uiState.currentUrl }
    }
    val pinnedBookmarks = remember(bookmarks) {
        bookmarks.filter { it.pinned }
    }

    val openBookmarkAction: (BookmarkItem) -> Unit = { bookmark ->
        triggerHaptic()
        scope.launch { drawerState.close() }
        viewModel.clearError()
        viewModel.selectDestination(AppDestination.WORKSPACE)
        webViewController.loadUrl(bookmark.url)
    }

    val usePromptAction: (QuickPrompt) -> Unit = { prompt ->
        triggerHaptic()
        viewModel.selectDestination(AppDestination.WORKSPACE)
        webViewController.insertPrompt(prompt.body) { inserted ->
            if (inserted) {
                showMessage("Inserted \"${prompt.title}\"")
            } else {
                onCopyToClipboard(prompt.title, prompt.body)
                showMessage("Copied \"${prompt.title}\" to clipboard")
            }
        }
    }

    AntigravityTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor
    ) {
        val bgColorArgb = MaterialTheme.colorScheme.background.toArgb()
        val surfaceColorArgb = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
        val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()

        val showChrome = !uiState.isImmersiveMode && !uiState.isFullscreenWebView
        val showBottomBar = showChrome && !(isImeVisible && uiState.activeDestination == AppDestination.WORKSPACE)

        Box(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen || uiState.activeDestination != AppDestination.WORKSPACE,
                drawerContent = {
                    AntigravityDrawerSheet(
                        activeDestination = uiState.activeDestination,
                        currentTitle = uiState.pageTitle,
                        currentUrl = uiState.currentUrl,
                        pinnedBookmarks = pinnedBookmarks,
                        isDesktopMode = settings.desktopMode,
                        isImmersiveMode = uiState.isImmersiveMode,
                        onSelectDestination = { dest ->
                            triggerHaptic()
                            viewModel.selectDestination(dest)
                            scope.launch { drawerState.close() }
                        },
                        onGoHome = {
                            triggerHaptic()
                            viewModel.clearError()
                            viewModel.selectDestination(AppDestination.WORKSPACE)
                            webViewController.goHome()
                            scope.launch { drawerState.close() }
                        },
                        onOpenBookmark = openBookmarkAction,
                        onOpenQuickPrompts = {
                            triggerHaptic()
                            scope.launch { drawerState.close() }
                            viewModel.selectDestination(AppDestination.WORKSPACE)
                            viewModel.setQuickActionsOpen(true)
                        },
                        onOpenFindInPage = {
                            triggerHaptic()
                            scope.launch { drawerState.close() }
                            viewModel.selectDestination(AppDestination.WORKSPACE)
                            viewModel.setImmersiveMode(false)
                            viewModel.setFindBarOpen(true)
                        },
                        onToggleDesktopMode = {
                            triggerHaptic()
                            viewModel.setDesktopMode(!settings.desktopMode)
                            scope.launch { drawerState.close() }
                            showMessage(
                                if (!settings.desktopMode) "Desktop viewport enabled" else "Mobile viewport enabled"
                            )
                        },
                        onToggleImmersiveMode = {
                            triggerHaptic()
                            viewModel.setImmersiveMode(!uiState.isImmersiveMode)
                            scope.launch { drawerState.close() }
                        },
                        onSharePage = {
                            triggerHaptic()
                            scope.launch { drawerState.close() }
                            onShareUrl(uiState.pageTitle, uiState.currentUrl)
                        },
                        onOpenInBrowser = {
                            triggerHaptic()
                            scope.launch { drawerState.close() }
                            onOpenExternalUrl(uiState.currentUrl)
                        }
                    )
                }
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        AnimatedVisibility(
                            visible = showChrome,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut()
                        ) {
                            when (uiState.activeDestination) {
                                AppDestination.WORKSPACE -> WorkspaceTopBar(
                                    title = uiState.pageTitle,
                                    currentUrl = uiState.currentUrl,
                                    isLoading = uiState.isLoading,
                                    loadingProgress = uiState.loadingProgress,
                                    isBookmarked = isCurrentBookmarked,
                                    isDesktopMode = settings.desktopMode,
                                    isFindBarOpen = uiState.isFindBarOpen,
                                    findQuery = uiState.findQuery,
                                    findActiveMatch = uiState.findActiveMatch,
                                    findTotalMatches = uiState.findTotalMatches,
                                    onOpenDrawer = {
                                        triggerHaptic()
                                        scope.launch { drawerState.open() }
                                    },
                                    onToggleBookmark = {
                                        triggerHaptic()
                                        viewModel.toggleCurrentBookmark { added ->
                                            showMessage(if (added) "Saved to Bookmarks" else "Removed from Bookmarks")
                                        }
                                    },
                                    onReload = {
                                        triggerHaptic()
                                        viewModel.clearError()
                                        webViewController.reload(uiState.error?.failedUrl)
                                    },
                                    onOpenQuickActions = {
                                        triggerHaptic()
                                        viewModel.setQuickActionsOpen(true)
                                    },
                                    onFindQueryChange = { q ->
                                        viewModel.updateFindQuery(q)
                                        webViewController.findInPage(q)
                                    },
                                    onFindNext = { forward ->
                                        webViewController.findNext(forward)
                                    },
                                    onCloseFindBar = {
                                        viewModel.setFindBarOpen(false)
                                        webViewController.clearFind()
                                    }
                                )

                                AppDestination.SAVED -> SectionTopBar(
                                    title = "Saved",
                                    subtitle = "${bookmarks.size} bookmarked ${if (bookmarks.size == 1) "page" else "pages"}",
                                    onOpenDrawer = {
                                        triggerHaptic()
                                        scope.launch { drawerState.open() }
                                    }
                                )

                                AppDestination.DOWNLOADS -> SectionTopBar(
                                    title = "Downloads",
                                    subtitle = "Files saved from Antigravity",
                                    onOpenDrawer = {
                                        triggerHaptic()
                                        scope.launch { drawerState.open() }
                                    }
                                )

                                AppDestination.SETTINGS -> SectionTopBar(
                                    title = "Settings",
                                    subtitle = "Appearance, workspace & privacy",
                                    onOpenDrawer = {
                                        triggerHaptic()
                                        scope.launch { drawerState.open() }
                                    }
                                )
                            }
                        }
                    },
                    bottomBar = {
                        AnimatedVisibility(
                            visible = showBottomBar,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            AntigravityBottomBar(
                                activeDestination = uiState.activeDestination,
                                savedCount = bookmarks.size,
                                downloadsCount = downloads.size,
                                onSelectDestination = { dest ->
                                    triggerHaptic()
                                    if (dest == AppDestination.WORKSPACE &&
                                        uiState.activeDestination == AppDestination.WORKSPACE
                                    ) {
                                        webViewController.scrollToTop()
                                    } else {
                                        viewModel.selectDestination(dest)
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .imePadding()
                    ) {
                        // Retained WebView layer — stays alive across native tab switches so sessions never reset
                        RetainedWorkspaceWebView(
                            initialUrl = initialUrl,
                            bgColorArgb = bgColorArgb,
                            surfaceColorArgb = surfaceColorArgb,
                            primaryColorArgb = primaryColorArgb,
                            webViewController = webViewController,
                            onBuildWebViewClient = onBuildWebViewClient,
                            onBuildWebChromeClient = onBuildWebChromeClient,
                            onBuildDownloadHandler = onBuildDownloadHandler,
                            onFindMatchesUpdated = { active, count ->
                                viewModel.updateFindMatches(active, count)
                            },
                            onRefreshTriggered = {
                                viewModel.clearError()
                                webViewController.reload(uiState.error?.failedUrl)
                            },
                            onWebViewCreated = onWebViewCreated,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Cold-start native shimmer skeleton to eliminate blank WebView flash
                        AnimatedVisibility(
                            visible = !uiState.hasCompletedInitialLoad && uiState.error == null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            NativeLoadingSkeleton()
                        }

                        // Native offline / connection error overlay
                        uiState.error?.let { err ->
                            NativeErrorScreen(
                                error = err,
                                onRetry = {
                                    triggerHaptic()
                                    viewModel.clearError()
                                    webViewController.reload(err.failedUrl)
                                },
                                onGoHome = {
                                    triggerHaptic()
                                    viewModel.clearError()
                                    webViewController.goHome()
                                }
                            )
                        }

                        // Native Saved / Bookmarks Screen
                        AnimatedVisibility(
                            visible = uiState.activeDestination == AppDestination.SAVED,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            BookmarksScreen(
                                bookmarks = bookmarks,
                                currentTitle = uiState.pageTitle,
                                currentUrl = uiState.currentUrl,
                                onOpenBookmark = openBookmarkAction,
                                onAddBookmark = { title, url ->
                                    triggerHaptic()
                                    viewModel.addBookmark(title, url)
                                    showMessage("Saved \"$title\"")
                                },
                                onRenameBookmark = { id, newTitle ->
                                    viewModel.renameBookmark(id, newTitle)
                                    showMessage("Bookmark updated")
                                },
                                onTogglePin = { id ->
                                    triggerHaptic()
                                    viewModel.togglePinBookmark(id)
                                },
                                onDeleteBookmark = { id ->
                                    triggerHaptic()
                                    viewModel.removeBookmark(id)
                                    showMessage("Bookmark removed")
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        }

                        // Native Downloads Screen
                        AnimatedVisibility(
                            visible = uiState.activeDestination == AppDestination.DOWNLOADS,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            DownloadsScreen(
                                downloads = downloads,
                                onOpenSystemDownloads = {
                                    triggerHaptic()
                                    onOpenSystemDownloads()
                                },
                                onCopyUrl = { url ->
                                    triggerHaptic()
                                    onCopyToClipboard("Download URL", url)
                                    showMessage("Link copied")
                                },
                                onRemoveDownload = { id ->
                                    triggerHaptic()
                                    viewModel.removeDownload(id)
                                },
                                onClearAll = {
                                    triggerHaptic()
                                    viewModel.clearDownloads()
                                    showMessage("Download history cleared")
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        }

                        // Native Settings Screen
                        AnimatedVisibility(
                            visible = uiState.activeDestination == AppDestination.SETTINGS,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            SettingsScreen(
                                settings = settings,
                                onThemeModeChange = {
                                    triggerHaptic()
                                    viewModel.updateThemeMode(it)
                                },
                                onDynamicColorChange = {
                                    triggerHaptic()
                                    viewModel.setDynamicColor(it)
                                },
                                onTextZoomChange = { viewModel.setTextZoom(it) },
                                onMobileUiPolishChange = {
                                    triggerHaptic()
                                    viewModel.setMobileUiPolish(it)
                                },
                                onDesktopModeChange = {
                                    triggerHaptic()
                                    viewModel.setDesktopMode(it)
                                },
                                onPullToRefreshChange = {
                                    triggerHaptic()
                                    viewModel.setPullToRefresh(it)
                                },
                                onKeepScreenAwakeChange = {
                                    triggerHaptic()
                                    viewModel.setKeepScreenAwake(it)
                                },
                                onHapticFeedbackChange = { viewModel.setHapticFeedback(it) },
                                onClearCache = {
                                    triggerHaptic()
                                    webViewController.clearCache()
                                    showMessage("Web cache cleared")
                                },
                                onClearSession = {
                                    triggerHaptic()
                                    webViewController.clearSessionAndReload()
                                    viewModel.selectDestination(AppDestination.WORKSPACE)
                                    showMessage("Signed out & session cleared")
                                },
                                onOpenGitHub = {
                                    onOpenExternalUrl("https://github.com/Unnidentified/antigravity-android")
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        }

                        // Focus Mode floating exit chip
                        if (uiState.isImmersiveMode &&
                            uiState.activeDestination == AppDestination.WORKSPACE &&
                            !uiState.isFullscreenWebView
                        ) {
                            ImmersiveExitPill(
                                onExitImmersive = {
                                    triggerHaptic()
                                    viewModel.setImmersiveMode(false)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .statusBarsPadding()
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Quick Actions & Prompts Modal Bottom Sheet
            if (uiState.isQuickActionsOpen) {
                QuickActionsSheet(
                    settings = settings,
                    canGoBack = uiState.canGoBack,
                    canGoForward = uiState.canGoForward,
                    isImmersiveMode = uiState.isImmersiveMode,
                    prompts = quickPrompts,
                    onDismiss = { viewModel.setQuickActionsOpen(false) },
                    onBack = {
                        triggerHaptic()
                        webViewController.goBack()
                    },
                    onForward = {
                        triggerHaptic()
                        webViewController.goForward()
                    },
                    onHome = {
                        triggerHaptic()
                        viewModel.clearError()
                        webViewController.goHome()
                    },
                    onFindInPage = {
                        triggerHaptic()
                        viewModel.setImmersiveMode(false)
                        viewModel.setFindBarOpen(true)
                    },
                    onCopyUrl = {
                        triggerHaptic()
                        onCopyToClipboard("Antigravity URL", uiState.currentUrl)
                        showMessage("Link copied")
                    },
                    onShareUrl = {
                        triggerHaptic()
                        onShareUrl(uiState.pageTitle, uiState.currentUrl)
                    },
                    onOpenInBrowser = {
                        triggerHaptic()
                        onOpenExternalUrl(uiState.currentUrl)
                    },
                    onTextZoomChange = { viewModel.setTextZoom(it) },
                    onToggleDesktopMode = {
                        triggerHaptic()
                        viewModel.setDesktopMode(!settings.desktopMode)
                    },
                    onToggleMobilePolish = {
                        triggerHaptic()
                        viewModel.setMobileUiPolish(!settings.mobileUiPolish)
                    },
                    onToggleImmersiveMode = {
                        triggerHaptic()
                        viewModel.setImmersiveMode(!uiState.isImmersiveMode)
                    },
                    onUsePrompt = usePromptAction,
                    onAddPrompt = { title, body, category ->
                        triggerHaptic()
                        viewModel.addQuickPrompt(title, body, category)
                        showMessage("Prompt \"$title\" saved")
                    },
                    onDeletePrompt = { id ->
                        triggerHaptic()
                        viewModel.removeQuickPrompt(id)
                    }
                )
            }

            // Fullscreen video / custom WebChromeClient overlay
            if (fullscreenWebView != null) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            (fullscreenWebView.parent as? ViewGroup)?.removeView(fullscreenWebView)
                            addView(
                                fullscreenWebView,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RetainedWorkspaceWebView(
    initialUrl: String,
    bgColorArgb: Int,
    surfaceColorArgb: Int,
    primaryColorArgb: Int,
    webViewController: WebViewController,
    onBuildWebViewClient: () -> AntigravityWebViewClient,
    onBuildWebChromeClient: () -> AntigravityWebChromeClient,
    onBuildDownloadHandler: () -> DownloadHandler,
    onFindMatchesUpdated: (activeOrdinal: Int, numberOfMatches: Int) -> Unit,
    onRefreshTriggered: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            val webView = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(bgColorArgb)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                overScrollMode = View.OVER_SCROLL_NEVER

                val defaultUa = WebViewController.safeDefaultUserAgent(this)
                settings.apply {
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
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(true)
                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient = onBuildWebViewClient()
                webChromeClient = onBuildWebChromeClient()
                setDownloadListener(onBuildDownloadHandler())
                setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                    onFindMatchesUpdated(activeMatchOrdinal, numberOfMatches)
                }
            }

            SwipeRefreshLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setProgressBackgroundColorSchemeColor(surfaceColorArgb)
                setColorSchemeColors(primaryColorArgb)
                setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
                setOnRefreshListener { onRefreshTriggered() }
                addView(webView)

                val defaultUa = WebViewController.safeDefaultUserAgent(webView)
                webViewController.attach(webView, this, defaultUa)
                onWebViewCreated(webView)
                webView.loadUrl(initialUrl.ifBlank { UrlRouter.START_URL })
            }
        },
        update = { swipeLayout ->
            swipeLayout.setProgressBackgroundColorSchemeColor(surfaceColorArgb)
            swipeLayout.setColorSchemeColors(primaryColorArgb)
            val webView = swipeLayout.getChildAt(0) as? WebView
            webView?.setBackgroundColor(bgColorArgb)
        }
    )
}
