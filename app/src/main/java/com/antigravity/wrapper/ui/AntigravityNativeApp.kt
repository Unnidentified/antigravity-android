package com.antigravity.wrapper.ui

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.wrapper.bridge.AntigravityBridgeEngine
import com.antigravity.wrapper.data.BridgeStatus
import com.antigravity.wrapper.ui.components.ChatDrawerSheet
import com.antigravity.wrapper.ui.components.ChatMessageItem
import com.antigravity.wrapper.ui.components.ChatTopBar
import com.antigravity.wrapper.ui.components.EmptyChatWelcome
import com.antigravity.wrapper.ui.components.ModelSelectorSheet
import com.antigravity.wrapper.ui.components.PromptComposerBar
import com.antigravity.wrapper.ui.components.QuickPromptsSheet
import com.antigravity.wrapper.ui.components.SettingsSheet
import com.antigravity.wrapper.ui.components.UsageQuotaSheet
import com.antigravity.wrapper.ui.state.ChatViewModel
import com.antigravity.wrapper.ui.theme.AntigravityTheme
import com.antigravity.wrapper.ui.theme.isEffectiveDarkTheme
import com.antigravity.wrapper.web.AntigravityWebChromeClient
import com.antigravity.wrapper.web.AntigravityWebViewClient
import com.antigravity.wrapper.web.DownloadHandler
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
    val quickPrompts by viewModel.quickPrompts.collectAsStateWithLifecycle()

    val isDarkEffective = isEffectiveDarkTheme(settings.themeMode)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun triggerHaptic() {
        if (settings.hapticFeedbackEnabled) {
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
                onCopyToClipboard("Antigravity DOM Snapshot", snapshotJson)
                showToast("Sanitized DOM snapshot copied (${snapshotJson.length} chars)")
            } else {
                showToast("Session still loading — try again in a moment")
            }
        }
    }

    fun sendCurrentPrompt(overrideText: String? = null) {
        val textToSend = (overrideText ?: uiState.composerText).trim()
        if (textToSend.isEmpty()) return
        triggerHaptic()
        viewModel.addOptimisticUserMessage(textToSend)
        bridgeEngine.sendPrompt(textToSend) { dispatched ->
            if (!dispatched) {
                showToast("Session input not ready yet — tap Retry or check Diagnostics")
            }
        }
    }

    LaunchedEffect(isDarkEffective) {
        onConfigureSystemBars(isDarkEffective)
    }

    LaunchedEffect(settings.keepScreenAwake, uiState.snapshot.isStreaming) {
        onKeepScreenAwakeChanged(settings.keepScreenAwake && uiState.snapshot.isStreaming)
    }

    val displayedMessages = uiState.displayedMessages
    LaunchedEffect(displayedMessages.size, uiState.snapshot.isStreaming) {
        if (displayedMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayedMessages.lastIndex)
        }
    }

    val showHeadlessWebForAuth = uiState.bridgeStatus == BridgeStatus.AUTH_REQUIRED
    val showWebOverlay = showHeadlessWebForAuth || settings.webInspectorMode
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
            // Kept at real viewport dimensions behind the opaque native Compose UI so Chromium
            // continues laying out and streaming live DOM updates; brought forward ONLY for
            // Google Sign-In or explicit debug Web Inspector fallback.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (showWebOverlay) 2f else 0f)
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
                        .navigationBarsPadding(),
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
                                    scope.launch { drawerState.close() }
                                    viewModel.clearOptimisticMessages()
                                    bridgeEngine.startNewChat()
                                },
                                onSelectConversation = { conv ->
                                    triggerHaptic()
                                    scope.launch { drawerState.close() }
                                    viewModel.clearOptimisticMessages()
                                    bridgeEngine.selectConversation(conv.url, conv.title)
                                },
                                onRefreshChats = {
                                    triggerHaptic()
                                    bridgeEngine.syncNow()
                                    showToast("Refreshing conversations…")
                                },
                                onOpenQuota = {
                                    triggerHaptic()
                                    scope.launch { drawerState.close() }
                                    viewModel.setQuotaSheetOpen(true)
                                },
                                onOpenSettings = {
                                    triggerHaptic()
                                    scope.launch { drawerState.close() }
                                    viewModel.setSettingsSheetOpen(true)
                                }
                            )
                        }
                    ) {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                            topBar = {
                                ChatTopBar(
                                    conversationTitle = uiState.snapshot.conversationTitle,
                                    activeModelLabel = uiState.activeModelLabel,
                                    bridgeStatus = uiState.bridgeStatus,
                                    quotaUsedPercent = uiState.snapshot.quota.usedPercent,
                                    onOpenDrawer = {
                                        triggerHaptic()
                                        scope.launch { drawerState.open() }
                                    },
                                    onOpenModelSelector = {
                                        triggerHaptic()
                                        viewModel.setModelSheetOpen(true)
                                        bridgeEngine.refreshModels()
                                    },
                                    onOpenQuotaSheet = {
                                        triggerHaptic()
                                        viewModel.setQuotaSheetOpen(true)
                                    },
                                    onNewChat = {
                                        triggerHaptic()
                                        viewModel.clearOptimisticMessages()
                                        bridgeEngine.startNewChat()
                                    }
                                )
                            },
                            bottomBar = {
                                PromptComposerBar(
                                    text = uiState.composerText,
                                    activeModelLabel = uiState.activeModelLabel,
                                    isStreaming = uiState.snapshot.isStreaming,
                                    enabled = uiState.bridgeStatus == BridgeStatus.CONNECTED,
                                    onTextChange = { viewModel.updateComposerText(it) },
                                    onSend = { sendCurrentPrompt() },
                                    onStop = {
                                        triggerHaptic()
                                        bridgeEngine.stopGeneration()
                                    },
                                    onAttachClick = {
                                        triggerHaptic()
                                        bridgeEngine.triggerFileUpload()
                                    },
                                    onOpenModelSelector = {
                                        triggerHaptic()
                                        viewModel.setModelSheetOpen(true)
                                        bridgeEngine.refreshModels()
                                    },
                                    onOpenQuickPrompts = {
                                        triggerHaptic()
                                        viewModel.setQuickPromptsSheetOpen(true)
                                    }
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
                                        activeModelLabel = uiState.activeModelLabel,
                                        errorMessage = uiState.errorMessage,
                                        onSuggestionClick = { suggestion ->
                                            triggerHaptic()
                                            viewModel.updateComposerText(suggestion)
                                        },
                                        onRetryConnect = {
                                            triggerHaptic()
                                            viewModel.onBridgeReconnecting()
                                            bridgeEngine.reload()
                                        },
                                        onOpenSettings = {
                                            triggerHaptic()
                                            viewModel.setSettingsSheetOpen(true)
                                        }
                                    )
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(displayedMessages, key = { it.id }) { message ->
                                            ChatMessageItem(
                                                message = message,
                                                fontScale = fontScale,
                                                expandedReasoningIds = uiState.expandedReasoningIds,
                                                autoExpandReasoning = settings.autoExpandReasoning,
                                                onToggleReasoning = { blockId, defaultExpanded ->
                                                    triggerHaptic()
                                                    viewModel.toggleReasoningBlock(blockId, defaultExpanded)
                                                },
                                                onCopyText = { text ->
                                                    triggerHaptic()
                                                    onCopyToClipboard("Antigravity", text)
                                                    showToast("Copied to clipboard")
                                                },
                                                onShareText = { text ->
                                                    triggerHaptic()
                                                    onShareText(text)
                                                },
                                                onLinkClick = { url ->
                                                    onOpenExternalUrl(url)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Native Model Selector Bottom Sheet
                    if (uiState.isModelSheetOpen) {
                        ModelSelectorSheet(
                            models = uiState.availableModels,
                            activeModelLabel = uiState.activeModelLabel,
                            onSelectModel = { option ->
                                triggerHaptic()
                                bridgeEngine.selectModel(option.name)
                                showToast("Switching to ${option.name}")
                            },
                            onRefreshModels = {
                                triggerHaptic()
                                bridgeEngine.refreshModels()
                                showToast("Syncing models…")
                            },
                            onDismiss = { viewModel.setModelSheetOpen(false) }
                        )
                    }

                    // Native Usage Quota Bottom Sheet
                    if (uiState.isQuotaSheetOpen) {
                        UsageQuotaSheet(
                            quota = uiState.snapshot.quota,
                            activeModelLabel = uiState.activeModelLabel,
                            messageCount = displayedMessages.size,
                            conversationCount = uiState.snapshot.conversations.size,
                            bridgeStatus = uiState.bridgeStatus,
                            onRefresh = {
                                triggerHaptic()
                                bridgeEngine.syncNow()
                                showToast("Refreshing quota…")
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

                    // Native Settings & Diagnostics Sheet
                    if (uiState.isSettingsSheetOpen) {
                        SettingsSheet(
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
                                copyDomSnapshot()
                            },
                            onSignOut = {
                                triggerHaptic()
                                viewModel.clearOptimisticMessages()
                                viewModel.onBridgeNavigatingToAuth()
                                bridgeEngine.signOutAndReload()
                            },
                            onDismiss = { viewModel.setSettingsSheetOpen(false) }
                        )
                    }
                }
            }
        }
    }
}
