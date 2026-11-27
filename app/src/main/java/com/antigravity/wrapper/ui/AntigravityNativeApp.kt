package com.antigravity.wrapper.ui

import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import com.antigravity.wrapper.ui.components.ChatFeedRowItem
import com.antigravity.wrapper.ui.components.ExpandedReasoningState
import com.antigravity.wrapper.ui.components.flattenChatMessages
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
import com.antigravity.wrapper.ui.markdown.rememberMarkdownRenderStyles
import com.antigravity.wrapper.ui.state.ChatViewModel
import com.antigravity.wrapper.ui.theme.AntigravityTheme
import com.antigravity.wrapper.ui.theme.isEffectiveDarkTheme
import com.antigravity.wrapper.web.AntigravityWebChromeClient
import com.antigravity.wrapper.web.AntigravityWebViewClient
import com.antigravity.wrapper.web.DownloadHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val DRAWER_SETTLE_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@OptIn(ExperimentalFoundationApi::class)
private class BeyondViewportPrefetchStrategy(
    private val beyondCount: Int = 2
) : LazyListPrefetchStrategy {
    private val activeHandles = mutableListOf<LazyLayoutPrefetchState.PrefetchHandle>()
    private var lastRangeStart = -1
    private var lastRangeEnd = -1

    override fun LazyListPrefetchScope.onScroll(
        delta: Float,
        layoutInfo: LazyListLayoutInfo
    ) {
        if (layoutInfo.visibleItemsInfo.isEmpty()) return
        val scrollingForward = delta < 0f
        val targetStart: Int
        val targetEnd: Int
        if (scrollingForward) {
            val lastVisible = layoutInfo.visibleItemsInfo.last().index
            targetStart = lastVisible + 1
            targetEnd = minOf(lastVisible + beyondCount, layoutInfo.totalItemsCount - 1)
        } else {
            val firstVisible = layoutInfo.visibleItemsInfo.first().index
            targetStart = maxOf(0, firstVisible - beyondCount)
            targetEnd = firstVisible - 1
        }
        updatePrefetchRange(targetStart, targetEnd)
    }

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {
        if (layoutInfo.visibleItemsInfo.isEmpty()) {
            clearHandles()
            return
        }
        if (lastRangeStart == -1) {
            val firstVisible = layoutInfo.visibleItemsInfo.first().index
            val lastVisible = layoutInfo.visibleItemsInfo.last().index
            val start = maxOf(0, firstVisible - 1)
            val end = minOf(layoutInfo.totalItemsCount - 1, lastVisible + 1)
            if (start <= end) {
                updatePrefetchRange(start, end)
            }
        }
    }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) = Unit

    private fun LazyListPrefetchScope.updatePrefetchRange(start: Int, end: Int) {
        if (start > end) {
            clearHandles()
            return
        }
        if (start == lastRangeStart && end == lastRangeEnd) return
        clearHandles()
        lastRangeStart = start
        lastRangeEnd = end
        for (idx in start..end) {
            activeHandles.add(schedulePrefetch(idx))
        }
    }

    private fun clearHandles() {
        for (i in activeHandles.indices) {
            activeHandles[i].cancel()
        }
        activeHandles.clear()
        lastRangeStart = -1
        lastRangeEnd = -1
    }
}

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
    val drawerOffset = remember { Animatable(0f) }
    var currentDrawerWidthPx by remember { mutableFloatStateOf(1f) }
    val isDrawerVisible by remember { derivedStateOf { drawerOffset.value > 0.5f } }
    val displayedMessages = uiState.displayedMessages
    val feedItems = remember(displayedMessages) {
        flattenChatMessages(displayedMessages)
    }
    val currentUrl = uiState.snapshot.currentUrl
    val conversationKey = remember(currentUrl) {
        currentUrl.trim()
    }
    val hasOlderHeader = (uiState.snapshot.hasOlderMessages || uiState.isLoadingOlderMessages) &&
        feedItems.isNotEmpty()
    @OptIn(ExperimentalFoundationApi::class)
    val listState = key(conversationKey) {
        val prefetchStrategy = remember { BeyondViewportPrefetchStrategy(beyondCount = 2) }
        val initialIndex = if (feedItems.isNotEmpty()) {
            feedItems.size + (if (hasOlderHeader) 1 else 0)
        } else {
            0
        }
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialIndex,
            prefetchStrategy = prefetchStrategy
        )
    }
    var pendingPrependAnchorId by remember(conversationKey) { mutableStateOf<String?>(null) }
    var pendingPrependAnchorOffset by remember(conversationKey) { mutableIntStateOf(0) }
    var pendingPrependPrevAnchorFeedIdx by remember(conversationKey) { mutableIntStateOf(-1) }
    var pendingPrependPrevHasOlderHeader by remember(conversationKey) { mutableStateOf(false) }
    var pendingPrependPrevCount by remember(conversationKey) { mutableIntStateOf(0) }
    var pendingPrependNeedsOffsetAdjust by remember(conversationKey) { mutableStateOf(false) }
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
        bridgeEngine.openReviewDiffs(cardTitle) { diffData ->
            viewModel.onDiffReviewLoaded(diffData)
        }
    }

    fun openDocument(docTitle: String) {
        triggerHaptic()
        dismissKeyboardAndFocus()
        viewModel.openDocumentLoading(docTitle)
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
        bridgeEngine.refreshProjects()
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
    val density = LocalDensity.current
    val drawerOpenThresholdPx = with(density) { 24.dp.toPx() }
    LaunchedEffect(drawerOffset, drawerOpenThresholdPx) {
        snapshotFlow { drawerOffset.value > drawerOpenThresholdPx }
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
        enabled = isDrawerVisible &&
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
        scope.launch { drawerOffset.animateTo(0f, DRAWER_SETTLE_SPRING) }
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

    val bottomAnchorIndex = feedItems.size + (if (hasOlderHeader) 1 else 0)
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
                .collect { scrolling ->
                    viewModel.setListScrolling(scrolling)
                    bridgeEngine.setScrolling(scrolling)
                }
        } finally {
            viewModel.setListScrolling(false)
            bridgeEngine.setScrolling(false)
        }
    }

    LaunchedEffect(listState, conversationKey) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                userScrolledAway = true
                pendingPrependAnchorId = null
                pendingPrependNeedsOffsetAdjust = false
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
            if (height > 0 && prevHeight > 0 && height < prevHeight && wasPinnedToBottom && !listState.isScrollInProgress && totalItems > 0) {
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
        val anchorKey = pendingPrependAnchorId
        if (anchorKey != null && !listState.isScrollInProgress) {
            val anchorIdx = feedItems.indexOfFirst { it.key == anchorKey }
            if (anchorIdx >= 0 &&
                (anchorIdx != pendingPrependPrevAnchorFeedIdx || hasOlderHeader != pendingPrependPrevHasOlderHeader)
            ) {
                val targetIdx = anchorIdx + (if (hasOlderHeader) 1 else 0)
                if (pendingPrependAnchorOffset <= 0) {
                    listState.requestScrollToItem(targetIdx, -pendingPrependAnchorOffset)
                    pendingPrependNeedsOffsetAdjust = false
                } else if (targetIdx > 0 && anchorIdx > pendingPrependPrevAnchorFeedIdx) {
                    listState.requestScrollToItem(targetIdx - 1, 0)
                    pendingPrependNeedsOffsetAdjust = true
                } else if (targetIdx == 0) {
                    listState.requestScrollToItem(0, 0)
                    pendingPrependNeedsOffsetAdjust = false
                }
                pendingPrependPrevAnchorFeedIdx = anchorIdx
                pendingPrependPrevHasOlderHeader = hasOlderHeader
                pendingPrependPrevCount = feedItems.size
            }
        }
    }

    // Clear prepend anchor after older batch settles; snap to bottom for initial hydration and tail/streaming updates.
    LaunchedEffect(conversationKey, feedItems.size, lastMessageSignature, hasOlderHeader, uiState.isLoadingOlderMessages) {
        val anchorKey = pendingPrependAnchorId
        if (anchorKey != null) {
            if (pendingPrependNeedsOffsetAdjust && !listState.isScrollInProgress) {
                val anchorInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == anchorKey }
                if (anchorInfo != null) {
                    val delta = anchorInfo.offset - pendingPrependAnchorOffset
                    if (kotlin.math.abs(delta) > 2) {
                        listState.scrollBy(delta.toFloat())
                    }
                }
                pendingPrependNeedsOffsetAdjust = false
            }
            seenLastSignature = lastMessageSignature
            prevBottomAnchorIndex = bottomAnchorIndex
            initialSnapDeadlineMs = 0L
            if (!uiState.isLoadingOlderMessages) {
                delay(1_200L)
                pendingPrependAnchorId = null
                pendingPrependNeedsOffsetAdjust = false
            }
            return@LaunchedEffect
        }

        if (feedItems.isNotEmpty() && !listState.isScrollInProgress) {
            val now = System.currentTimeMillis()
            if (seenLastSignature == null) {
                initialSnapDeadlineMs = now + 2_000L
                userScrolledAway = false
            }
            val withinInitialHydration = !userScrolledAway && now <= initialSnapDeadlineMs
            val isUserMessage = displayedMessages.lastOrNull()?.role == MessageRole.USER
            val wasNearBottom = !listState.canScrollForward || wasPinnedToBottom
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
        } else if (feedItems.isEmpty()) {
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
                    val allowDrawerSwipe by rememberUpdatedState(
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
                    )
                    val openDrawer = remember(scope, drawerOffset) {
                        {
                            dismissKeyboardAndFocus()
                            val target = currentDrawerWidthPx
                            scope.launch { drawerOffset.animateTo(target, DRAWER_SETTLE_SPRING) }
                            Unit
                        }
                    }
                    val closeDrawer = remember(scope, drawerOffset) {
                        {
                            scope.launch { drawerOffset.animateTo(0f, DRAWER_SETTLE_SPRING) }
                            Unit
                        }
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val drawerWidthDp = minOf(316.dp, maxWidth * 0.84f)
                        val drawerWidthPx = with(density) { drawerWidthDp.toPx() }.coerceAtLeast(1f)
                        currentDrawerWidthPx = drawerWidthPx
                        LaunchedEffect(drawerWidthPx) {
                            drawerOffset.updateBounds(0f, drawerWidthPx)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(drawerWidthPx) {
                                    val velocityTracker = VelocityTracker()
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        if (!allowDrawerSwipe) return@awaitEachGesture
                                        val wasOpen = drawerOffset.value >= drawerWidthPx * 0.5f
                                        var isCapturing = false
                                        val dragChange = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                                            val shouldCapture = if (drawerOffset.value <= 0.5f) {
                                                overSlop > 0f
                                            } else {
                                                true
                                            }
                                            if (shouldCapture) {
                                                change.consume()
                                                isCapturing = true
                                                velocityTracker.resetTracking()
                                                velocityTracker.addPosition(down.uptimeMillis, down.position)
                                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                                val initialNext = (drawerOffset.value + overSlop).coerceIn(0f, drawerWidthPx)
                                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                    drawerOffset.snapTo(initialNext)
                                                }
                                            }
                                        }
                                        if (!isCapturing || dragChange == null) return@awaitEachGesture

                                        val completed = horizontalDrag(dragChange.id) { change ->
                                            val dx = change.positionChange().x
                                            change.consume()
                                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                                            val next = (drawerOffset.value + dx).coerceIn(0f, drawerWidthPx)
                                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                drawerOffset.snapTo(next)
                                            }
                                        }
                                        val vx = velocityTracker.calculateVelocity().x
                                        val target = when {
                                            !completed -> if (drawerOffset.value >= drawerWidthPx * 0.5f) drawerWidthPx else 0f
                                            vx > 420f -> drawerWidthPx
                                            vx < -420f -> 0f
                                            wasOpen -> if (drawerOffset.value < drawerWidthPx * 0.85f) 0f else drawerWidthPx
                                            else -> if (drawerOffset.value > drawerWidthPx * 0.35f) drawerWidthPx else 0f
                                        }
                                        scope.launch {
                                            drawerOffset.animateTo(
                                                targetValue = target,
                                                animationSpec = DRAWER_SETTLE_SPRING,
                                                initialVelocity = vx
                                            )
                                        }
                                    }
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
                                            openDrawer()
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
                                            bridgeEngine.fetchWorkspaceOverview { items ->
                                                viewModel.onWorkspaceArtifactsLoaded(items)
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
                                            bridgeEngine.refreshModels()
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
                                                bridgeEngine.refreshModels()
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
                                    val markdownStyles = rememberMarkdownRenderStyles(fontScale = fontScale)
                                    LazyColumn(
                                        state = listState,
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
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
                                                                val anchorKey = anchorItem?.key as? String
                                                                val anchorFeedIdx = if (anchorKey != null) {
                                                                    feedItems.indexOfFirst { it.key == anchorKey }
                                                                } else {
                                                                    -1
                                                                }
                                                                pendingPrependAnchorId = if (anchorFeedIdx >= 0) anchorKey else null
                                                                pendingPrependAnchorOffset = anchorItem?.offset ?: 0
                                                                pendingPrependPrevAnchorFeedIdx = anchorFeedIdx
                                                                pendingPrependPrevHasOlderHeader = hasOlderHeader
                                                                pendingPrependPrevCount = feedItems.size
                                                                pendingPrependNeedsOffsetAdjust = false
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
                                            items = feedItems,
                                            key = { it.key },
                                            contentType = { it.contentType }
                                        ) { item ->
                                            ChatFeedRowItem(
                                                item = item,
                                                fontScale = fontScale,
                                                markdownStyles = markdownStyles,
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

                                // Left-edge system gesture exclusion band (kept <=180.dp so Android honors the full strip around thumb height)
                                if (!isDrawerVisible) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .width(32.dp)
                                            .height(180.dp)
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

                        // Draw-phase backdrop scrim (zero layout or drawer recompositions while dragging)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val progress = (drawerOffset.value / drawerWidthPx).coerceIn(0f, 1f)
                                    if (progress > 0.001f) {
                                        drawRect(Color.Black.copy(alpha = 0.54f * progress))
                                    }
                                }
                                .then(
                                    if (isDrawerVisible) {
                                        Modifier.pointerInput(closeDrawer) {
                                            detectTapGestures { closeDrawer() }
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        )

                        // Draw-phase GPU-translated sidebar panel (kept warm off-screen, zero mid-drag recomposition)
                        val onDrawerNewChat = remember(closeDrawer, viewModel, bridgeEngine) {
                            {
                                triggerHaptic()
                                dismissKeyboardAndFocus()
                                closeDrawer()
                                viewModel.startNewChatOptimistic()
                                bridgeEngine.startNewChat()
                            }
                        }
                        val onDrawerSelectConversation = remember(closeDrawer, viewModel, bridgeEngine, currentUrl, uiState.snapshot.conversationTitle) {
                            { conv: com.antigravity.wrapper.data.ChatConversation ->
                                triggerHaptic()
                                dismissKeyboardAndFocus()
                                closeDrawer()
                                val isAlreadyActive = conv.isActive ||
                                    (conv.url.isNotBlank() && currentUrl.isNotBlank() && conv.url.trim() == currentUrl.trim()) ||
                                    (conv.url.isBlank() && conv.title.isNotBlank() && conv.title == uiState.snapshot.conversationTitle)
                                if (!isAlreadyActive) {
                                    viewModel.onStartConversationSwitch(conv.url, conv.title)
                                    bridgeEngine.selectConversation(conv.url, conv.title)
                                }
                            }
                        }
                        val onDrawerRefreshChats = remember(bridgeEngine) {
                            {
                                triggerHaptic()
                                bridgeEngine.syncNow()
                            }
                        }
                        val onDrawerOpenQuota = remember(closeDrawer, viewModel) {
                            {
                                triggerHaptic()
                                dismissKeyboardAndFocus()
                                closeDrawer()
                                viewModel.setQuotaSheetOpen(true)
                            }
                        }
                        val onDrawerOpenSettings = remember(closeDrawer, viewModel) {
                            {
                                triggerHaptic()
                                dismissKeyboardAndFocus()
                                closeDrawer()
                                viewModel.setSettingsSheetOpen(true)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(drawerWidthDp)
                                .focusProperties { canFocus = isDrawerVisible }
                                .graphicsLayer {
                                    translationX = -drawerWidthPx + drawerOffset.value
                                    alpha = if (drawerOffset.value > 0.5f) 1f else 0f
                                }
                                .then(
                                    if (isDrawerVisible) {
                                        Modifier.pointerInput(Unit) {
                                            detectTapGestures { }
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            ChatDrawerSheet(
                                conversations = uiState.snapshot.conversations,
                                bridgeStatus = uiState.bridgeStatus,
                                activeModelLabel = uiState.activeModelLabel,
                                quota = uiState.snapshot.quota,
                                onNewChat = onDrawerNewChat,
                                onSelectConversation = onDrawerSelectConversation,
                                onRefreshChats = onDrawerRefreshChats,
                                onOpenQuota = onDrawerOpenQuota,
                                onOpenSettings = onDrawerOpenSettings,
                                modifier = Modifier.fillMaxWidth()
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
