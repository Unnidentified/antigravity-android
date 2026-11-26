package com.antigravity.wrapper.ui

import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.wrapper.bridge.AntigravityBridgeEngine
import com.antigravity.wrapper.data.ArtifactCard
import com.antigravity.wrapper.data.BridgeStatus
import com.antigravity.wrapper.data.MessageRole
import com.antigravity.wrapper.ui.components.ChatDrawerSheet
import com.antigravity.wrapper.ui.components.ChatMessageItem
import com.antigravity.wrapper.ui.components.ExpandedReasoningState
import com.antigravity.wrapper.ui.components.ChatTopBar
import com.antigravity.wrapper.ui.components.DocumentViewerSheet
import com.antigravity.wrapper.ui.components.EmptyChatWelcome
import com.antigravity.wrapper.ui.components.FileDiffViewerSheet
import com.antigravity.wrapper.ui.components.ModelSelectorSheet
import com.antigravity.wrapper.ui.components.PermissionsSelectorSheet
import com.antigravity.wrapper.ui.components.ProjectSelectorSheet
import com.antigravity.wrapper.ui.components.PromptComposerBar
import com.antigravity.wrapper.ui.components.QuickPromptsSheet
import com.antigravity.wrapper.ui.components.SettingsFullScreen
import com.antigravity.wrapper.ui.components.UsageQuotaSheet
import com.antigravity.wrapper.ui.components.WorkspaceAndActionsSheet
import com.antigravity.wrapper.ui.state.ChatViewModel
import com.antigravity.wrapper.ui.theme.AntigravityTheme
import com.antigravity.wrapper.ui.theme.isEffectiveDarkTheme
import com.antigravity.wrapper.web.AntigravityWebChromeClient
import com.antigravity.wrapper.web.AntigravityWebViewClient
import com.antigravity.wrapper.web.DownloadHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun AntigravityNativeApp(
    viewModel: ChatViewModel,
    bridgeEngine: AntigravityBridgeEngine,
    snackbarHostState: SnackbarHostState,
    initialUrl: String,
    onConfigureSystemBars: (isDark: Boolean) -> Unit,
    onKeepScreenAwakeChanged: (Boolean) -> Unit,
    onBuildWebViewClient: () -> AntigravityWebViewClient,
    onBuildWebChromeClient: () -> AntigravityWebChromeClient,
    onBuildDownloadHandler: () -> DownloadHandler,
    onCopyToClipboard: (label: String, text: String) -> Unit,
    onShareText: (text: String) -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val quickPrompts by viewModel.quickPrompts.collectAsStateWithLifecycle()

    val isDarkEffective = isEffectiveDarkTheme(settings.themeMode)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val displayedMessages = uiState.displayedMessages
    val currentUrl = uiState.snapshot.currentUrl
    val conversationKey = remember(currentUrl) {
        currentUrl.trim()
    }
    val hasOlderHeader = (uiState.snapshot.hasOlderMessages || uiState.isLoadingOlderMessages) &&
        displayedMessages.isNotEmpty()
    val listState = key(conversationKey) {
        val initialIndex = if (displayedMessages.isNotEmpty()) {
            displayedMessages.size + (if (hasOlderHeader) 1 else 0)
        } else {
            0
        }
        rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    }
    var pendingPrependAnchorId by remember(conversationKey) { mutableStateOf<String?>(null) }
    var pendingPrependAnchorOffset by remember(conversationKey) { mutableIntStateOf(0) }
    var pendingPrependPrevCount by remember(conversationKey) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val currentHapticEnabled by rememberUpdatedState(settings.hapticFeedbackEnabled)
    val currentOnCopyToClipboard by rememberUpdatedState(onCopyToClipboard)
    val currentOnShareText by rememberUpdatedState(onShareText)
    val currentOnOpenExternalUrl by rememberUpdatedState(onOpenExternalUrl)

    fun dismissKeyboardAndFocus() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun triggerHaptic() {
        if (currentHapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun showToast(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun copyDomSnapshot() {
        bridgeEngine.exportDomSnapshot { snapshotJson ->
            if (!snapshotJson.isNullOrBlank()) {
                currentOnCopyToClipboard("Antigravity DOM Snapshot", snapshotJson)
                showToast("Sanitized DOM snapshot copied")
            } else {
                showToast("Session still loading — try again in a moment")
            }
        }
    }

    fun openDiffReview(cardTitle: String = "") {
        triggerHaptic()
        dismissKeyboardAndFocus()
        viewModel.openDiffReviewLoading(cardTitle.ifBlank { "Review Changes" })
        scope.launch {
            delay(180L)
            bridgeEngine.openReviewDiffs(cardTitle) { diffData ->
                viewModel.onDiffReviewLoaded(diffData)
            }
        }
    }

    fun openDocument(docTitle: String) {
        triggerHaptic()
        dismissKeyboardAndFocus()
        viewModel.openDocumentLoading(docTitle)
        scope.launch {
            delay(180L)
            bridgeEngine.openDocumentViewer(docTitle) { docData ->
                val externalPrefix = "EXTERNAL_LINK:"
                val rawErr = docData.error?.trim().orEmpty()
                val rawMd = docData.markdownContent.trim()
                val externalUrl = when {
                    rawErr.startsWith(externalPrefix, ignoreCase = true) ->
                        rawErr.substring(externalPrefix.length).trim()
                    rawMd.startsWith(externalPrefix, ignoreCase = true) ->
                        rawMd.substring(externalPrefix.length).trim()
                    else -> ""
                }
                if (externalUrl.isNotEmpty()) {
                    viewModel.closeDocumentViewer()
                    currentOnOpenExternalUrl(externalUrl)
                } else {
                    viewModel.onDocumentLoaded(docData)
                }
            }
        }
    }

    fun handleLinkClick(url: String) {
        if (url.startsWith("ag-file://", ignoreCase = true)) {
            val fileName = Uri.decode(url.removePrefix("ag-file://")).trim()
            if (fileName.isNotEmpty()) {
                openDocument(fileName)
            }
        } else {
            currentOnOpenExternalUrl(url)
        }
    }

    fun isExternalArtifactLink(title: String, subtitle: String, url: String): Boolean {
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            return true
        }
        val sub = subtitle.trim().lowercase()
        if (sub.matches(Regex("^(?:[a-z0-9-]+\\.)+(?:com|org|net|io|dev|ai|app|co|edu|gov)(?:[\\s/·•].*)?$"))) {
            return true
        }
        val trimmedTitle = title.trim()
        if (trimmedTitle.matches(Regex("^v?\\d+\\.\\d+\\.\\d+(?:[-+].*)?$")) && (sub.isNotBlank() || url.isBlank())) {
            return true
        }
        return false
    }

    fun openProjectSelector() {
        triggerHaptic()
        dismissKeyboardAndFocus()
        viewModel.setProjectSheetOpen(true)
        scope.launch {
            delay(180L)
            bridgeEngine.refreshProjects()
        }
    }

    fun sendCurrentPrompt(overrideText: String? = null) {
        val textToSend = (overrideText ?: composerText).trim()
        if (textToSend.isEmpty()) return
        triggerHaptic()
        viewModel.addOptimisticUserMessage(textToSend)
        bridgeEngine.sendPrompt(textToSend) { dispatched, reason ->
            if (!dispatched) {
                val detail = if (reason.isNotBlank()) " ($reason)" else ""
                showToast("Couldn't send message$detail")
            }
        }
    }

    val showHeadlessWebForAuth = uiState.bridgeStatus == BridgeStatus.AUTH_REQUIRED
    val showWebOverlay = showHeadlessWebForAuth || settings.webInspectorMode

    // Unfocus composer & hide keyboard ONLY once when the drawer transitions into opening/open.
    // Avoids calling clearFocus() on every swipe frame and never mutates snapshot state inside snapshotFlow.
    val density = LocalDensity.current
    val drawerOpenThresholdPx = with(density) { 40.dp.toPx() }
    LaunchedEffect(drawerState) {
        var closedBaselineOffset = Float.NaN
        snapshotFlow {
            Triple(drawerState.currentValue, drawerState.targetValue, drawerState.currentOffset)
        }
            .map { (cur, target, offset) ->
                if (cur == DrawerValue.Closed && target == DrawerValue.Closed && !offset.isNaN()) {
                    if (closedBaselineOffset.isNaN() || kotlin.math.abs(offset - closedBaselineOffset) < 2f || offset < closedBaselineOffset) {
                        closedBaselineOffset = offset
                    }
                }
                cur == DrawerValue.Open ||
                    target == DrawerValue.Open ||
                    (!offset.isNaN() && !closedBaselineOffset.isNaN() && (offset - closedBaselineOffset) > drawerOpenThresholdPx)
            }
            .distinctUntilChanged()
            .collect { shouldDismiss ->
                if (shouldDismiss) {
                    dismissKeyboardAndFocus()
                }
            }
    }

    LaunchedEffect(
        uiState.isModelSheetOpen,
        uiState.isQuotaSheetOpen,
        uiState.isSettingsSheetOpen,
        uiState.isQuickPromptsSheetOpen,
        uiState.isWorkspaceSheetOpen,
        uiState.isProjectSheetOpen,
        uiState.isPermissionsSheetOpen,
        uiState.diffReviewData != null,
        uiState.documentViewerData != null,
        showWebOverlay
    ) {
        if (uiState.isModelSheetOpen ||
            uiState.isQuotaSheetOpen ||
            uiState.isSettingsSheetOpen ||
            uiState.isQuickPromptsSheetOpen ||
            uiState.isWorkspaceSheetOpen ||
            uiState.isProjectSheetOpen ||
            uiState.isPermissionsSheetOpen ||
            uiState.diffReviewData != null ||
            uiState.documentViewerData != null ||
            showWebOverlay
        ) {
            dismissKeyboardAndFocus()
        }
    }

    BackHandler(
        enabled = drawerState.isOpen &&
            !showWebOverlay &&
            !uiState.isSettingsSheetOpen &&
            !uiState.isModelSheetOpen &&
            !uiState.isQuotaSheetOpen &&
            !uiState.isQuickPromptsSheetOpen &&
            !uiState.isWorkspaceSheetOpen &&
            !uiState.isProjectSheetOpen &&
            !uiState.isPermissionsSheetOpen &&
            uiState.diffReviewData == null &&
            uiState.documentViewerData == null
    ) {
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(isDarkEffective) {
        onConfigureSystemBars(isDarkEffective)
    }

    // Only toggle WebView focus/touch when showWebOverlay actually flips (never during typing/scrolling!)
    LaunchedEffect(showWebOverlay) {
        bridgeEngine.setInteractive(showWebOverlay)
    }

    LaunchedEffect(settings.keepScreenAwake, uiState.snapshot.isStreaming) {
        onKeepScreenAwakeChanged(settings.keepScreenAwake && uiState.snapshot.isStreaming)
    }

    val bottomAnchorIndex = displayedMessages.size + (if (hasOlderHeader) 1 else 0)
    val lastMessageSignature = displayedMessages.lastOrNull()?.let { msg ->
        val reasoningSig = msg.reasoningBlocks.sumOf { b ->
            b.content.length + b.sections.sumOf { s -> s.steps.size }
        }
        "${msg.role}:${msg.id}:${msg.markdownContent.length}:${msg.markdownContent.takeLast(32)}:${msg.artifacts.size}:$reasoningSig:${msg.isStreaming}"
    }
    var seenLastSignature by remember(conversationKey) { mutableStateOf<String?>(null) }
    var prevBottomAnchorIndex by remember(conversationKey) { mutableIntStateOf(bottomAnchorIndex) }
    var initialSnapDeadlineMs by remember(conversationKey) { mutableLongStateOf(0L) }
    var userScrolledAway by remember(conversationKey) { mutableStateOf(false) }
    var wasPinnedToBottom by remember(conversationKey) { mutableStateOf(true) }

    LaunchedEffect(listState, conversationKey) {
        try {
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { bridgeEngine.setScrolling(it) }
        } finally {
            bridgeEngine.setScrolling(false)
        }
    }

    LaunchedEffect(listState, conversationKey) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                userScrolledAway = true
            }
        }
    }

    // Keep bottom pinned when IME opens or multiline composer shrinks viewport while already at bottom.
    LaunchedEffect(listState, conversationKey) {
        var prevHeight = 0
        snapshotFlow {
            Triple(
                listState.layoutInfo.viewportSize.height,
                listState.canScrollForward,
                listState.layoutInfo.totalItemsCount
            )
        }.collect { (height, canScrollFwd, totalItems) ->
            if (height > 0 && prevHeight > 0 && height < prevHeight && wasPinnedToBottom && totalItems > 0) {
                listState.scrollToItem(totalItems - 1)
            } else if (height > 0) {
                wasPinnedToBottom = !canScrollFwd
            }
            if (height > 0) {
                prevHeight = height
            }
        }
    }

    // Synchronously restore prepend scroll offset within the same layout frame (zero visual jump or jitter)
    SideEffect {
        val anchorId = pendingPrependAnchorId
        if (anchorId != null && displayedMessages.size > pendingPrependPrevCount) {
            val anchorMsgIdx = displayedMessages.indexOfFirst { it.id == anchorId }
            if (anchorMsgIdx >= 0) {
                val targetIdx = anchorMsgIdx + (if (hasOlderHeader) 1 else 0)
                listState.requestScrollToItem(targetIdx, -pendingPrependAnchorOffset)
            }
        }
    }

    // Clear prepend anchor after older batch settles; snap to bottom for initial hydration and tail/streaming updates.
    LaunchedEffect(conversationKey, displayedMessages.size, lastMessageSignature, hasOlderHeader, uiState.isLoadingOlderMessages) {
        val anchorId = pendingPrependAnchorId
        if (anchorId != null) {
            val anchorMsgIdx = displayedMessages.indexOfFirst { it.id == anchorId }
            if (anchorMsgIdx >= 0) {
                val targetIdx = anchorMsgIdx + (if (hasOlderHeader) 1 else 0)
                listState.scrollToItem(targetIdx, -pendingPrependAnchorOffset)
            }
            if (displayedMessages.size > pendingPrependPrevCount || !uiState.isLoadingOlderMessages) {
                pendingPrependAnchorId = null
                seenLastSignature = lastMessageSignature
                prevBottomAnchorIndex = bottomAnchorIndex
                initialSnapDeadlineMs = 0L
            }
            return@LaunchedEffect
        }

        if (displayedMessages.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (seenLastSignature == null) {
                initialSnapDeadlineMs = now + 2_000L
                userScrolledAway = false
            }
            val withinInitialHydration = !userScrolledAway && now <= initialSnapDeadlineMs
            val isUserMessage = displayedMessages.lastOrNull()?.role == com.antigravity.wrapper.data.MessageRole.USER
            val lastVisibleIdx = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val wasNearBottom = !listState.canScrollForward ||
                wasPinnedToBottom ||
                lastVisibleIdx >= (prevBottomAnchorIndex - 2)
            val signatureChanged = lastMessageSignature != seenLastSignature
            if (seenLastSignature == null ||
                withinInitialHydration ||
                (signatureChanged && (wasNearBottom || isUserMessage))
            ) {
                if (isUserMessage) {
                    userScrolledAway = false
                }
                listState.scrollToItem(bottomAnchorIndex)
                wasPinnedToBottom = true
            }
            seenLastSignature = lastMessageSignature
            prevBottomAnchorIndex = bottomAnchorIndex
        } else {
            seenLastSignature = null
            prevBottomAnchorIndex = 0
        }
    }

    val showScrollToBottom by remember(listState, density) {
        val minRemainingPx = with(density) { 24.dp.toPx() }
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0 || !listState.canScrollForward) {
                false
            } else {
                val lastVisible = info.visibleItemsInfo.lastOrNull()
                when {
                    lastVisible == null -> false
                    lastVisible.index < info.totalItemsCount - 1 -> true
                    else -> {
                        val remainingPx = (lastVisible.offset + lastVisible.size) -
                            (info.viewportEndOffset - info.afterContentPadding)
                        remainingPx > minRemainingPx
                    }
                }
            }
        }
    }

    val fontScale = settings.fontScalePercent / 100f

    AntigravityTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // LAYER 0: Headless WebView engine host.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (showWebOverlay) 3f else 0f)
            ) {
                if (showHeadlessWebForAuth) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sign in to Antigravity",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Authenticate with Google — native UI resumes automatically",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (settings.webInspectorMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    triggerHaptic()
                                    viewModel.setWebInspectorMode(false)
                                    bridgeEngine.syncNow()
                                }
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Native UI", style = MaterialTheme.typography.labelMedium)
                            }
                            FilledTonalButton(onClick = { copyDomSnapshot() }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy DOM", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(
                            if (showWebOverlay) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier
                                    .consumeWindowInsets(WindowInsets.ime)
                                    .navigationBarsPadding()
                            }
                        ),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            bridgeEngine.configureWebView(
                                view = this,
                                webViewClient = onBuildWebViewClient(),
                                webChromeClient = onBuildWebChromeClient(),
                                downloadHandler = onBuildDownloadHandler(),
                                initialUrl = initialUrl
                            )
                        }
                    }
                )
            }

            // LAYER 1: 100% Native Jetpack Compose Material 3 Chat Application
            AnimatedVisibility(
                visible = !showWebOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ChatDrawerSheet(
                                conversations = uiState.snapshot.conversations,
                                bridgeStatus = uiState.bridgeStatus,
                                activeModelLabel = uiState.activeModelLabel,
                                quota = uiState.snapshot.quota,
                                onNewChat = {
                                    triggerHaptic()
                                    dismissKeyboardAndFocus()
                                    scope.launch { drawerState.close() }
                                    viewModel.startNewChatOptimistic()
                                    bridgeEngine.startNewChat()
                                },
                                onSelectConversation = { conv ->
                                    triggerHaptic()
                                    dismissKeyboardAndFocus()
                                    scope.launch { drawerState.close() }
                                    val isAlreadyActive = conv.isActive ||
                                        (conv.url.isNotBlank() && currentUrl.isNotBlank() && conv.url.trim() == currentUrl.trim()) ||
                                        (conv.url.isBlank() && conv.title.isNotBlank() && conv.title == uiState.snapshot.conversationTitle)
                                    if (!isAlreadyActive) {
                                        viewModel.onStartConversationSwitch(conv.url, conv.title)
                                        bridgeEngine.selectConversation(conv.url, conv.title)
                                    }
                                },
                                onRefreshChats = {
                                    triggerHaptic()
                                    bridgeEngine.syncNow()
                                },
                                onOpenQuota = {
                                    triggerHaptic()
                                    dismissKeyboardAndFocus()
                                    scope.launch { drawerState.close() }
                                    viewModel.setQuotaSheetOpen(true)
                                },
                                onOpenSettings = {
                                    triggerHaptic()
                                    dismissKeyboardAndFocus()
                                    scope.launch { drawerState.close() }
                                    viewModel.setSettingsSheetOpen(true)
                                }
                            )
                        }
                    ) {
                        val isNewChat = displayedMessages.isEmpty() &&
                            !uiState.isSwitchingConversation &&
                            uiState.bridgeStatus != BridgeStatus.OFFLINE &&
                            uiState.bridgeStatus != BridgeStatus.ERROR

                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            topBar = {
                                ChatTopBar(
                                    conversationTitle = if (uiState.isSwitchingConversation && uiState.switchingTargetTitle.isNotBlank()) {
                                        uiState.switchingTargetTitle
                                    } else {
                                        uiState.snapshot.conversationTitle
                                    },
                                    activeProject = uiState.activeProjectLabel,
                                    isNewChat = isNewChat,
                                    bridgeStatus = uiState.bridgeStatus,
                                    quotaLeftPercent = uiState.snapshot.quota.headlineLeftPercent,
                                    onOpenDrawer = {
                                        triggerHaptic()
                                        dismissKeyboardAndFocus()
                                        scope.launch { drawerState.open() }
                                    },
                                    onOpenQuotaSheet = {
                                        triggerHaptic()
                                        dismissKeyboardAndFocus()
                                        viewModel.setQuotaSheetOpen(true)
                                    },
                                    onNewChat = {
                                        triggerHaptic()
                                        dismissKeyboardAndFocus()
                                        viewModel.startNewChatOptimistic()
                                        bridgeEngine.startNewChat()
                                    },
                                    onOpenWorkspaceSheet = {
                                        triggerHaptic()
                                        dismissKeyboardAndFocus()
                                        viewModel.setWorkspaceSheetOpen(true)
                                        if (!isNewChat) {
                                            viewModel.onStartWorkspaceArtifactsLoad()
                                            scope.launch {
                                                delay(180L)
                                                bridgeEngine.fetchWorkspaceOverview { items ->
                                                    viewModel.onWorkspaceArtifactsLoaded(items)
                                                }
                                            }
                                        }
                                    }
                                )
                            },
                            bottomBar = {
                                PromptComposerBar(
                                    text = composerText,
                                    activeProjectLabel = uiState.activeProjectLabel,
                                    activeModelLabel = if (uiState.isSwitchingConversation) {
                                        "Syncing…"
                                    } else {
                                        uiState.activeModelLabel
                                    },
                                    showNewChatContextRow = isNewChat,
                                    isStreaming = uiState.snapshot.isStreaming,
                                    isConnected = uiState.bridgeStatus == BridgeStatus.CONNECTED,
                                    containerColor = if (isNewChat) {
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    } else {
                                        MaterialTheme.colorScheme.background
                                    },
                                    onTextChange = { viewModel.updateComposerText(it) },
                                    onSend = { sendCurrentPrompt() },
                                    onStop = {
                                        triggerHaptic()
                                        bridgeEngine.stopGeneration()
                                    },
                                    onAttachClick = {
                                        triggerHaptic()
                                        bridgeEngine.triggerFileUpload { ok, reason ->
                                            if (!ok && reason.isNotBlank()) {
                                                showToast(reason)
                                            }
                                        }
                                    },
                                    onOpenModelSelector = {
                                        triggerHaptic()
                                        dismissKeyboardAndFocus()
                                        viewModel.setModelSheetOpen(true)
                                        if (uiState.snapshot.models.size <= 1) {
                                            viewModel.onStartModelRefresh()
                                            scope.launch {
                                                delay(180L)
                                                bridgeEngine.refreshModels()
                                            }
                                        }
                                    },
                                    onOpenProjectSelector = { openProjectSelector() }
                                )
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (displayedMessages.isEmpty()) {
                                    EmptyChatWelcome(
                                        bridgeStatus = uiState.bridgeStatus,
                                        activeProjectLabel = uiState.activeProjectLabel,
                                        activeModelLabel = uiState.activeModelLabel,
                                        isSwitchingConversation = uiState.isSwitchingConversation,
                                        errorMessage = uiState.errorMessage,
                                        onOpenProjectSelector = { openProjectSelector() },
                                        onOpenModelSelector = {
                                            triggerHaptic()
                                            dismissKeyboardAndFocus()
                                            viewModel.setModelSheetOpen(true)
                                            if (uiState.snapshot.models.size <= 1) {
                                                viewModel.onStartModelRefresh()
                                                scope.launch {
                                                    delay(180L)
                                                    bridgeEngine.refreshModels()
                                                }
                                            }
                                        },
                                        onRetryConnect = {
                                            triggerHaptic()
                                            viewModel.onBridgeReconnecting()
                                            bridgeEngine.reload()
                                        },
                                        onOpenSettings = {
                                            triggerHaptic()
                                            dismissKeyboardAndFocus()
                                            viewModel.setSettingsSheetOpen(true)
                                        }
                                    )
                                } else {
                                    val onToggleReasoning = remember(viewModel) {
                                        { blockId: String, defaultExpanded: Boolean ->
                                            triggerHaptic()
                                            viewModel.toggleReasoningBlock(blockId, defaultExpanded)
                                        }
                                    }
                                    val onExpandReasoningContent = remember(bridgeEngine) {
                                        { blockId: String, blockTitle: String ->
                                            bridgeEngine.expandReasoningBlock(blockId, blockTitle)
                                        }
                                    }
                                    val onExpandReasoningStep = remember(bridgeEngine) {
                                        { blockId: String, sectionId: String, stepId: String, stepTitle: String ->
                                            bridgeEngine.expandReasoningStep(blockId, sectionId, stepId, stepTitle)
                                        }
                                    }
                                    val onCopyMessageText = remember {
                                        { text: String ->
                                            triggerHaptic()
                                            currentOnCopyToClipboard("Antigravity", text)
                                            showToast("Copied to clipboard")
                                        }
                                    }
                                    val onShareMessageText = remember {
                                        { text: String ->
                                            triggerHaptic()
                                            currentOnShareText(text)
                                        }
                                    }
                                    val onMessageLinkClick = remember {
                                        { url: String -> handleLinkClick(url) }
                                    }
                                    val onMessageArtifactClick = remember(bridgeEngine) {
                                        { artifact: ArtifactCard ->
                                            val isDiffCard = artifact.addedLines != null ||
                                                artifact.actionLabel.equals("Review", ignoreCase = true) ||
                                                artifact.title.contains("files changed", ignoreCase = true) ||
                                                artifact.title.contains("file changed", ignoreCase = true)
                                            val isExternal = !isDiffCard && isExternalArtifactLink(
                                                title = artifact.title,
                                                subtitle = artifact.subtitle,
                                                url = artifact.url
                                            )
                                            when {
                                                isDiffCard -> openDiffReview(artifact.title)
                                                artifact.url.startsWith("http", ignoreCase = true) -> {
                                                    currentOnOpenExternalUrl(artifact.url)
                                                }
                                                isExternal -> {
                                                    triggerHaptic()
                                                    bridgeEngine.openExternalArtifact(artifact.title)
                                                }
                                                else -> openDocument(artifact.title)
                                            }
                                        }
                                    }

                                    val expandedReasoningState = remember(uiState.expandedReasoningIds) {
                                        ExpandedReasoningState(uiState.expandedReasoningIds)
                                    }
                                    LazyColumn(
                                        state = listState,
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        if (hasOlderHeader) {
                                            item(key = "load_older_header", contentType = "header") {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Surface(
                                                        onClick = {
                                                            if (!uiState.isLoadingOlderMessages) {
                                                                triggerHaptic()
                                                                val anchorItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                                                    val k = info.key as? String
                                                                    k != null && k != "load_older_header" && k != "bottom_anchor"
                                                                }
                                                                pendingPrependAnchorId = anchorItem?.key as? String
                                                                pendingPrependAnchorOffset = anchorItem?.offset ?: 0
                                                                pendingPrependPrevCount = displayedMessages.size
                                                                viewModel.onStartLoadOlderMessages()
                                                                bridgeEngine.loadOlderMessages()
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                        border = BorderStroke(
                                                            1.dp,
                                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                                        )
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                                        ) {
                                                            if (uiState.isLoadingOlderMessages) {
                                                                CircularProgressIndicator(
                                                                    strokeWidth = 2.dp,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            } else {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.History,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = if (uiState.isLoadingOlderMessages) {
                                                                    "Loading earlier messages…"
                                                                } else {
                                                                    "Load earlier messages"
                                                                },
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        items(
                                            items = displayedMessages,
                                            key = { it.id },
                                            contentType = { message ->
                                                if (message.role == MessageRole.USER) "user_message" else "assistant_message"
                                            }
                                        ) { message ->
                                            ChatMessageItem(
                                                message = message,
                                                fontScale = fontScale,
                                                expandedReasoningState = expandedReasoningState,
                                                autoExpandReasoning = settings.autoExpandReasoning,
                                                onToggleReasoning = onToggleReasoning,
                                                onExpandReasoningContent = onExpandReasoningContent,
                                                onExpandReasoningStep = onExpandReasoningStep,
                                                onCopyText = onCopyMessageText,
                                                onShareText = onShareMessageText,
                                                onLinkClick = onMessageLinkClick,
                                                onArtifactClick = onMessageArtifactClick
                                            )
                                        }
                                        item(key = "bottom_anchor", contentType = "bottom_anchor") {
                                            Spacer(modifier = Modifier.size(8.dp))
                                        }
                                    }

                                    // Bottom fade scrim + compact centered jump-to-bottom pill when scrolled up (never obscures right-edge message text)
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = showScrollToBottom,
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(54.dp)
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            MaterialTheme.colorScheme.background.copy(alpha = 0.88f)
                                                        )
                                                    )
                                                )
                                                .padding(bottom = 10.dp),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Surface(
                                                onClick = {
                                                    triggerHaptic()
                                                    userScrolledAway = false
                                                    wasPinnedToBottom = true
                                                    scope.launch {
                                                        listState.animateScrollToItem(bottomAnchorIndex)
                                                    }
                                                },
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme.onSurface,
                                                border = BorderStroke(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                                ),
                                                shadowElevation = 4.dp,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                                        contentDescription = "Scroll to bottom",
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Left-edge system gesture exclusion band so edge swipe opens drawer instead of triggering System Back
                                if (!drawerState.isOpen) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .width(32.dp)
                                            .fillMaxHeight(0.75f)
                                            .systemGestureExclusion { coords ->
                                                Rect(
                                                    left = 0f,
                                                    top = 0f,
                                                    right = coords.size.width.toFloat(),
                                                    bottom = coords.size.height.toFloat()
                                                )
                                            }
                                    )
                                }

                                // Dark-themed Snackbar positioned cleanly above the composer bar
                                SnackbarHost(
                                    hostState = snackbarHostState,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    snackbar = { data ->
                                        androidx.compose.material3.Snackbar(
                                            snackbarData = data,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            actionColor = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Native Model Selector Bottom Sheet
                    if (uiState.isModelSheetOpen) {
                        ModelSelectorSheet(
                            models = uiState.availableModels,
                            activeModelLabel = uiState.activeModelLabel,
                            isRefreshing = uiState.isRefreshingModels,
                            onSelectModel = { modelName, effortTier ->
                                triggerHaptic()
                                viewModel.onSelectModelOptimistic(modelName, effortTier)
                                bridgeEngine.selectModel(modelName, effortTier)
                            },
                            onRefreshModels = {
                                triggerHaptic()
                                viewModel.onStartModelRefresh()
                                bridgeEngine.refreshModels()
                            },
                            onDismiss = { viewModel.setModelSheetOpen(false) }
                        )
                    }

                    // Native Usage Quota Bottom Sheet (auto-loads on open, inline refresh feedback)
                    if (uiState.isQuotaSheetOpen) {
                        UsageQuotaSheet(
                            quota = uiState.snapshot.quota,
                            activeModelLabel = uiState.activeModelLabel,
                            messageCount = displayedMessages.size,
                            bridgeStatus = uiState.bridgeStatus,
                            isRefreshing = uiState.isRefreshingQuota,
                            statusMessage = uiState.quotaStatusMessage,
                            onRefresh = {
                                triggerHaptic()
                                viewModel.onStartQuotaRefresh()
                                bridgeEngine.refreshQuota()
                            },
                            onDismiss = { viewModel.setQuotaSheetOpen(false) }
                        )
                    }

                    // Native Quick Prompts Bottom Sheet
                    if (uiState.isQuickPromptsSheetOpen) {
                        QuickPromptsSheet(
                            prompts = quickPrompts,
                            onSelectPrompt = { prompt ->
                                triggerHaptic()
                                viewModel.appendPromptToComposer(prompt.body)
                            },
                            onAddPrompt = { title, body, category ->
                                triggerHaptic()
                                viewModel.addQuickPrompt(title, body, category)
                                showToast("Saved \"$title\"")
                            },
                            onDeletePrompt = { id ->
                                triggerHaptic()
                                viewModel.removeQuickPrompt(id)
                            },
                            onDismiss = { viewModel.setQuickPromptsSheetOpen(false) }
                        )
                    }

                    // Native Workspace & Thread Actions Bottom Sheet
                    if (uiState.isWorkspaceSheetOpen) {
                        val isNewChatForSheet = displayedMessages.isEmpty() &&
                            !uiState.isSwitchingConversation &&
                            uiState.bridgeStatus != BridgeStatus.OFFLINE &&
                            uiState.bridgeStatus != BridgeStatus.ERROR
                        WorkspaceAndActionsSheet(
                            conversationTitle = uiState.snapshot.conversationTitle,
                            permissionsPreset = uiState.snapshot.permissionsPreset,
                            artifacts = uiState.workspaceArtifacts,
                            isLoadingArtifacts = uiState.isLoadingWorkspaceArtifacts,
                            hasSidePanel = uiState.snapshot.hasSidePanel,
                            isNewChat = isNewChatForSheet,
                            webInspectorEnabled = settings.webInspectorMode,
                            onToggleWebInspector = { enabled ->
                                triggerHaptic()
                                viewModel.setWebInspectorMode(enabled)
                                if (enabled) {
                                    viewModel.setWorkspaceSheetOpen(false)
                                }
                            },
                            onOpenDiffs = { openDiffReview() },
                            onOpenArtifact = { item ->
                                when {
                                    item.url.startsWith("http", ignoreCase = true) -> {
                                        onOpenExternalUrl(item.url)
                                    }
                                    isExternalArtifactLink(item.title, item.subtitle, item.url) -> {
                                        triggerHaptic()
                                        bridgeEngine.openExternalArtifact(item.title)
                                    }
                                    else -> openDocument(item.title)
                                }
                            },
                            onOpenPermissions = {
                                triggerHaptic()
                                viewModel.setPermissionsSheetOpen(true)
                            },
                            onRenameThread = { newTitle ->
                                triggerHaptic()
                                bridgeEngine.runThreadAction("rename", newTitle)
                                showToast("Renaming conversation…")
                            },
                            onPinThread = {
                                triggerHaptic()
                                bridgeEngine.runThreadAction("pin")
                                showToast("Updated pin state")
                            },
                            onArchiveThread = {
                                triggerHaptic()
                                bridgeEngine.runThreadAction("archive")
                                showToast("Conversation archived")
                            },
                            onDismiss = { viewModel.setWorkspaceSheetOpen(false) }
                        )
                    }

                    // Native Project Selector Bottom Sheet
                    if (uiState.isProjectSheetOpen) {
                        ProjectSelectorSheet(
                            projects = uiState.snapshot.projects,
                            activeProject = uiState.activeProjectLabel,
                            onSelectProject = { projectName ->
                                triggerHaptic()
                                bridgeEngine.selectProject(projectName)
                                showToast("Project: $projectName")
                            },
                            onDismiss = { viewModel.setProjectSheetOpen(false) }
                        )
                    }

                    // Native Remote Permissions Selector Bottom Sheet
                    if (uiState.isPermissionsSheetOpen) {
                        PermissionsSelectorSheet(
                            currentPreset = uiState.snapshot.permissionsPreset,
                            onSelectPreset = { preset ->
                                triggerHaptic()
                                bridgeEngine.selectPermissions(preset)
                                showToast("Permissions: $preset")
                            },
                            onDismiss = { viewModel.setPermissionsSheetOpen(false) }
                        )
                    }

                    // Native File Diff Viewer Sheet
                    uiState.diffReviewData?.let { diffData ->
                        FileDiffViewerSheet(
                            data = diffData,
                            onRefresh = { openDiffReview(diffData.title) },
                            onDismiss = { viewModel.closeDiffReview() }
                        )
                    }

                    // Native Document / Artifact Viewer Sheet
                    uiState.documentViewerData?.let { docData ->
                        DocumentViewerSheet(
                            data = docData,
                            fontScale = fontScale,
                            onCopyText = { text ->
                                triggerHaptic()
                                onCopyToClipboard(docData.title.ifBlank { "Document" }, text)
                                showToast("Copied to clipboard")
                            },
                            onShareText = { text ->
                                triggerHaptic()
                                onShareText(text)
                            },
                            onLinkClick = { url -> handleLinkClick(url) },
                            onDismiss = { viewModel.closeDocumentViewer() }
                        )
                    }
                }
            }

            // LAYER 2: Full-Screen Native Settings Page — pure opaque slide (zero transparent bleed)
            AnimatedVisibility(
                visible = uiState.isSettingsSheetOpen && !showWebOverlay,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    SettingsFullScreen(
                        settings = settings,
                        bridgeStatus = uiState.bridgeStatus,
                        diagnosticsNote = uiState.snapshot.diagnosticsNote,
                        onThemeModeChange = {
                            triggerHaptic()
                            viewModel.updateThemeMode(it)
                        },
                        onDynamicColorChange = {
                            triggerHaptic()
                            viewModel.setDynamicColor(it)
                        },
                        onFontScaleChange = { viewModel.setFontScale(it) },
                        onAutoExpandReasoningChange = {
                            triggerHaptic()
                            viewModel.setAutoExpandReasoning(it)
                        },
                        onHapticFeedbackChange = { viewModel.setHapticFeedback(it) },
                        onKeepScreenAwakeChange = {
                            triggerHaptic()
                            viewModel.setKeepScreenAwake(it)
                        },
                        onWebInspectorChange = {
                            triggerHaptic()
                            viewModel.setWebInspectorMode(it)
                        },
                        onCopyDomSnapshot = {
                            triggerHaptic()
                            viewModel.setSettingsSheetOpen(false)
                            copyDomSnapshot()
                        },
                        onSignOut = {
                            triggerHaptic()
                            viewModel.clearOptimisticMessages()
                            viewModel.onBridgeNavigatingToAuth()
                            bridgeEngine.signOutAndReload()
                        },
                        onBack = { viewModel.setSettingsSheetOpen(false) }
                    )
                }
            }
        }
    }
}
