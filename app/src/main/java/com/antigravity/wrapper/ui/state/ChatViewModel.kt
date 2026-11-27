package com.antigravity.wrapper.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.wrapper.data.AppRepository
import com.antigravity.wrapper.data.AppSettings
import com.antigravity.wrapper.data.BridgeSnapshot
import com.antigravity.wrapper.data.BridgeStatus
import com.antigravity.wrapper.data.ChatMessage
import com.antigravity.wrapper.data.DiffReviewData
import com.antigravity.wrapper.data.DocumentViewerData
import com.antigravity.wrapper.data.MessageRole
import com.antigravity.wrapper.data.ModelOption
import com.antigravity.wrapper.data.QuickPrompt
import com.antigravity.wrapper.data.ThemeMode
import com.antigravity.wrapper.data.WorkspaceArtifactItem
import com.antigravity.wrapper.web.UrlRouter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val bridgeStatus: BridgeStatus = BridgeStatus.CONNECTING,
    val snapshot: BridgeSnapshot = BridgeSnapshot(),
    val optimisticMessages: List<ChatMessage> = emptyList(),
    val optimisticModelLabel: String? = null,
    val cachedProject: String = "",
    val cachedModelLabel: String = "",
    val expandedReasoningIds: Map<String, Boolean> = emptyMap(),
    val isModelSheetOpen: Boolean = false,
    val isQuotaSheetOpen: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val isQuickPromptsSheetOpen: Boolean = false,
    val isWorkspaceSheetOpen: Boolean = false,
    val isProjectSheetOpen: Boolean = false,
    val isPermissionsSheetOpen: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val diffReviewData: DiffReviewData? = null,
    val documentViewerData: DocumentViewerData? = null,
    val workspaceArtifacts: List<WorkspaceArtifactItem> = emptyList(),
    val isLoadingWorkspaceArtifacts: Boolean = false,
    val isRefreshingModels: Boolean = false,
    val isRefreshingQuota: Boolean = false,
    val quotaStatusMessage: String? = null,
    val isSwitchingConversation: Boolean = false,
    val switchingFromUrl: String? = null,
    val switchingTargetTitle: String = "",
    val errorMessage: String? = null,
    val lastSyncTimeMillis: Long = 0L
) {
    val displayedMessages: List<ChatMessage>
        get() {
            if (isSwitchingConversation) return emptyList()
            if (optimisticMessages.isEmpty()) return snapshot.messages
            val serverUserCounts = snapshot.messages
                .asSequence()
                .filter { it.role == MessageRole.USER }
                .groupingBy { it.markdownContent.trim() }
                .eachCount()
            val pending = optimisticMessages.filter { opt ->
                val count = serverUserCounts[opt.markdownContent.trim()] ?: 0
                count <= opt.optimisticBaselineCount
            }
            return snapshot.messages + pending
        }

    val activeModelLabel: String
        get() = optimisticModelLabel
            ?: snapshot.activeModelName.ifBlank {
                snapshot.models.firstOrNull { it.isSelected }?.displayLabel
                    ?: cachedModelLabel
            }

    val activeProjectLabel: String
        get() = snapshot.activeProject.ifBlank { cachedProject }

    val availableModels: List<ModelOption>
        get() = if (snapshot.models.isNotEmpty()) {
            snapshot.models
        } else if (snapshot.activeModelName.isNotBlank()) {
            listOf(
                ModelOption(
                    id = snapshot.activeModelName,
                    name = snapshot.activeModelName,
                    isSelected = true
                )
            )
        } else {
            emptyList()
        }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AppSettings()
    )

    val quickPrompts: StateFlow<List<QuickPrompt>> = repository.quickPrompts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AppRepository.DEFAULT_PROMPTS
    )

    private val _uiState = MutableStateFlow(
        ChatUiState(
            snapshot = BridgeSnapshot(
                conversations = repository.readCachedConversations(),
                quota = repository.readCachedQuota()
            ),
            cachedProject = repository.readCachedProject(),
            cachedModelLabel = repository.readCachedModel()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    @Volatile
    private var modelMutationLockUntilMs: Long = 0L

    private var conversationSwitchJob: Job? = null
    private var loadOlderJob: Job? = null
    private var modelOptimisticJob: Job? = null

    @Volatile
    private var loadOlderBaselineCount: Int = 0

    @Volatile
    private var loadOlderBaselineFirstSig: String = ""

    @Volatile
    private var switchingTargetUrl: String = ""

    @Volatile
    private var switchingFromTitle: String = ""

    @Volatile
    private var switchingFromMessagesSig: String = ""

    @Volatile
    private var switchingUrlChangedAtMs: Long = 0L

    private fun messagesSignature(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) return ""
        val first = messages.first()
        val last = messages.last()
        return "${messages.size}:${first.id}:${first.markdownContent.take(48)}:${last.id}:${last.markdownContent.take(48)}"
    }

    private fun isIntermediateHomeUrl(url: String): Boolean {
        val clean = url.trim().substringBefore('?').substringBefore('#').trimEnd('/').lowercase()
        return clean == UrlRouter.START_URL.trimEnd('/').lowercase() ||
            clean == "https://antigravity.google.com/remote"
    }

    @Volatile
    private var isListScrolling: Boolean = false

    private var deferredScrollSnapshot: BridgeSnapshot? = null

    fun setListScrolling(scrolling: Boolean) {
        if (isListScrolling == scrolling) return
        isListScrolling = scrolling
        if (!scrolling) {
            val deferred = deferredScrollSnapshot
            deferredScrollSnapshot = null
            if (deferred != null) {
                onBridgeSnapshot(deferred)
            }
        }
    }

    fun onBridgeSnapshot(snapshot: BridgeSnapshot) {
        val currentState = _uiState.value
        val shouldDeferDuringScroll = isListScrolling &&
            !currentState.isSwitchingConversation &&
            !currentState.isLoadingOlderMessages &&
            currentState.optimisticMessages.isEmpty() &&
            !snapshot.authRequired
        if (shouldDeferDuringScroll) {
            deferredScrollSnapshot = snapshot
            return
        }
        deferredScrollSnapshot = null
        if (snapshot.activeProject.isNotBlank()) {
            repository.saveCachedProject(snapshot.activeProject)
        }
        val resolvedModel = snapshot.activeModelName.ifBlank {
            snapshot.models.firstOrNull { it.isSelected }?.displayLabel.orEmpty()
        }
        val now = System.currentTimeMillis()
        val modelHoldActive = now < modelMutationLockUntilMs
        if (resolvedModel.isNotBlank() && !modelHoldActive) {
            repository.saveCachedModel(resolvedModel)
        }
        if (snapshot.conversations.isNotEmpty()) {
            repository.saveCachedConversations(snapshot.conversations)
        }
        if (snapshot.quota.isLiveFromServer && snapshot.quota.headlineLeftPercent != null) {
            repository.saveCachedQuota(snapshot.quota)
        }
        var shouldCancelSwitchJob = false
        var shouldCancelLoadOlderJob = false
        var shouldCancelModelJob = false
        _uiState.update { current ->
            val nextStatus = when {
                snapshot.authRequired -> BridgeStatus.AUTH_REQUIRED
                else -> BridgeStatus.CONNECTED
            }
            val serverUserCounts = snapshot.messages
                .asSequence()
                .filter { it.role == MessageRole.USER }
                .groupingBy { it.markdownContent.trim() }
                .eachCount()
            val remainingOptimistic = current.optimisticMessages.filter { opt ->
                val count = serverUserCounts[opt.markdownContent.trim()] ?: 0
                count <= opt.optimisticBaselineCount
            }
            val expectedOptimistic = current.optimisticModelLabel?.trim().orEmpty()
            val incomingMatchesOptimistic = expectedOptimistic.isNotEmpty() &&
                snapshot.activeModelName.equals(expectedOptimistic, ignoreCase = true)
            val clearOptimisticModel = expectedOptimistic.isNotEmpty() &&
                (incomingMatchesOptimistic || (!modelHoldActive && snapshot.activeModelName.isNotBlank()))
            if (clearOptimisticModel) {
                shouldCancelModelJob = true
            }

            val fromUrl = current.switchingFromUrl?.trim().orEmpty()
            val incomingUrl = snapshot.currentUrl.trim()
            val targetUrl = switchingTargetUrl
            val targetTitle = current.switchingTargetTitle.trim()
            val incomingTitle = snapshot.conversationTitle.trim()
            val incomingMessagesSig = messagesSignature(snapshot.messages)
            val isTargetSpecific = targetUrl.isNotEmpty() || targetTitle.isNotEmpty()
            val isIntermediateHome = isTargetSpecific && isIntermediateHomeUrl(incomingUrl)

            val reachedTargetUrl = targetUrl.isNotEmpty() && incomingUrl == targetUrl
            val titleChangedToTarget = targetTitle.isNotEmpty() &&
                incomingTitle.equals(targetTitle, ignoreCase = true) &&
                !incomingTitle.equals(switchingFromTitle, ignoreCase = true)
            val urlChangedToNonHome = fromUrl.isNotEmpty() &&
                incomingUrl.isNotEmpty() &&
                incomingUrl != fromUrl &&
                !isIntermediateHome

            val arrivedAtTargetRoute = when {
                targetUrl.isNotEmpty() -> reachedTargetUrl || (titleChangedToTarget && !isIntermediateHome)
                targetTitle.isNotEmpty() -> titleChangedToTarget || urlChangedToNonHome
                else -> urlChangedToNonHome
            }
            if (current.isSwitchingConversation && arrivedAtTargetRoute && switchingUrlChangedAtMs == 0L) {
                switchingUrlChangedAtMs = now
            }
            val routeSettleElapsed = switchingUrlChangedAtMs > 0L && (now - switchingUrlChangedAtMs) >= 2_500L
            val messagesDifferFromSource = switchingFromMessagesSig.isEmpty() ||
                incomingMessagesSig != switchingFromMessagesSig

            val arrivedAtNewThread = current.isSwitchingConversation && !isIntermediateHome && (
                (arrivedAtTargetRoute && (
                    (snapshot.messages.isNotEmpty() && messagesDifferFromSource) ||
                        routeSettleElapsed
                    )) ||
                    (fromUrl.isEmpty() && targetUrl.isEmpty() && snapshot.messages.isNotEmpty() && messagesDifferFromSource)
                )
            if (arrivedAtNewThread) {
                shouldCancelSwitchJob = true
                switchingTargetUrl = ""
                switchingFromTitle = ""
                switchingFromMessagesSig = ""
                switchingUrlChangedAtMs = 0L
            }
            val stillSwitching = if (arrivedAtNewThread) false else current.isSwitchingConversation

            val incomingFirstSig = snapshot.messages.firstOrNull()?.let { first ->
                "${first.id}:${first.markdownContent.length}:${first.reasoningBlocks.sumOf { it.sections.size }}"
            }.orEmpty()
            val loadingOlderResolved = current.isLoadingOlderMessages &&
                (arrivedAtNewThread ||
                    snapshot.messages.size > loadOlderBaselineCount ||
                    (loadOlderBaselineFirstSig.isNotEmpty() && incomingFirstSig != loadOlderBaselineFirstSig) ||
                    !snapshot.hasOlderMessages)
            if (loadingOlderResolved) {
                shouldCancelLoadOlderJob = true
            }
            val stillLoadingOlder = if (current.isLoadingOlderMessages) !loadingOlderResolved else false

            // Preserve last non-empty conversations, models, projects, and quota across partial route snapshots
            val cleanUrl = snapshot.currentUrl.trim()
            val mergedConversations = if (snapshot.conversations.isNotEmpty()) {
                snapshot.conversations
            } else {
                current.snapshot.conversations.map { conv ->
                    val convUrl = conv.url.trim()
                    val active = when {
                        convUrl.isNotEmpty() && cleanUrl.isNotEmpty() -> convUrl == cleanUrl
                        else -> snapshot.conversationTitle != "Antigravity" && conv.title == snapshot.conversationTitle
                    }
                    if (conv.isActive != active) conv.copy(isActive = active) else conv
                }
            }
            val mergedModels = if (snapshot.models.isNotEmpty()) {
                snapshot.models
            } else {
                current.snapshot.models
            }
            val mergedProjects = if (snapshot.projects.isNotEmpty()) {
                snapshot.projects
            } else {
                current.snapshot.projects
            }
            val mergedQuota = if (snapshot.quota.isLiveFromServer || snapshot.quota.limits.isNotEmpty() || snapshot.quota.headlineLeftPercent != null) {
                snapshot.quota
            } else {
                current.snapshot.quota
            }
            val effectiveActiveModel = if (modelHoldActive && expectedOptimistic.isNotEmpty()) {
                expectedOptimistic
            } else {
                snapshot.activeModelName
            }
            val effectiveModels = if (modelHoldActive && expectedOptimistic.isNotEmpty()) {
                val effortMatch = TRAILING_EFFORT_REGEX.find(expectedOptimistic)
                val parsedEffort = effortMatch?.let { m ->
                    m.groupValues[1].ifBlank { m.groupValues[2] }
                }?.takeIf { it.isNotBlank() }
                val expectedBaseName = TRAILING_EFFORT_REGEX.replace(expectedOptimistic, "").trim()
                mergedModels.map { m ->
                    val sel = m.name.equals(expectedBaseName, ignoreCase = true) ||
                        m.name.equals(expectedOptimistic, ignoreCase = true)
                    val effort = if (sel) (parsedEffort ?: m.tier) else m.tier
                    m.copy(isSelected = sel, tier = effort)
                }
            } else {
                mergedModels
            }
            val mergedSnapshot = snapshot.copy(
                activeModelName = effectiveActiveModel,
                models = effectiveModels,
                projects = mergedProjects,
                conversations = mergedConversations,
                quota = mergedQuota
            )

            current.copy(
                bridgeStatus = nextStatus,
                snapshot = mergedSnapshot,
                cachedProject = snapshot.activeProject.ifBlank { current.cachedProject },
                cachedModelLabel = if (modelHoldActive) current.cachedModelLabel else resolvedModel.ifBlank { current.cachedModelLabel },
                optimisticMessages = remainingOptimistic,
                optimisticModelLabel = if (clearOptimisticModel) null else current.optimisticModelLabel,
                isRefreshingModels = if (mergedSnapshot.models.isNotEmpty()) false else current.isRefreshingModels,
                isRefreshingQuota = false,
                quotaStatusMessage = if (current.isRefreshingQuota) {
                    if (mergedSnapshot.quota.isLiveFromServer) "Updated just now" else "Synced with session"
                } else {
                    current.quotaStatusMessage
                },
                isSwitchingConversation = stillSwitching,
                switchingFromUrl = if (stillSwitching) current.switchingFromUrl else null,
                switchingTargetTitle = if (stillSwitching) current.switchingTargetTitle else "",
                isLoadingOlderMessages = stillLoadingOlder,
                errorMessage = null,
                lastSyncTimeMillis = now
            )
        }
        if (shouldCancelSwitchJob) conversationSwitchJob?.cancel()
        if (shouldCancelLoadOlderJob) loadOlderJob?.cancel()
        if (shouldCancelModelJob) modelOptimisticJob?.cancel()
    }

    fun onBridgeNavigatingToAuth() {
        deferredScrollSnapshot = null
        repository.clearSessionCache()
        _uiState.update {
            it.copy(
                bridgeStatus = BridgeStatus.AUTH_REQUIRED,
                cachedProject = "",
                cachedModelLabel = "",
                snapshot = BridgeSnapshot(authRequired = true),
                errorMessage = null
            )
        }
    }

    fun onBridgeError(description: String?, isOffline: Boolean) {
        deferredScrollSnapshot = null
        conversationSwitchJob?.cancel()
        loadOlderJob?.cancel()
        switchingTargetUrl = ""
        switchingFromTitle = ""
        switchingFromMessagesSig = ""
        switchingUrlChangedAtMs = 0L
        _uiState.update {
            it.copy(
                bridgeStatus = if (isOffline) BridgeStatus.OFFLINE else BridgeStatus.ERROR,
                isRefreshingModels = false,
                isRefreshingQuota = false,
                isSwitchingConversation = false,
                isLoadingOlderMessages = false,
                workspaceArtifacts = emptyList(),
                switchingFromUrl = null,
                switchingTargetTitle = "",
                errorMessage = description ?: if (isOffline) "No internet connection" else "Unable to connect to Antigravity"
            )
        }
    }

    fun onBridgeReconnecting() {
        _uiState.update {
            it.copy(
                bridgeStatus = BridgeStatus.CONNECTING,
                errorMessage = null
            )
        }
    }

    fun onStartConversationSwitch(targetUrl: String = "", targetTitle: String = "") {
        deferredScrollSnapshot = null
        conversationSwitchJob?.cancel()
        loadOlderJob?.cancel()
        switchingTargetUrl = targetUrl.trim()
        switchingFromTitle = _uiState.value.snapshot.conversationTitle.trim()
        switchingFromMessagesSig = messagesSignature(_uiState.value.snapshot.messages)
        switchingUrlChangedAtMs = 0L
        _uiState.update {
            it.copy(
                optimisticMessages = emptyList(),
                optimisticModelLabel = null,
                isSwitchingConversation = true,
                isLoadingOlderMessages = false,
                workspaceArtifacts = emptyList(),
                switchingFromUrl = it.snapshot.currentUrl,
                switchingTargetTitle = targetTitle.trim()
            )
        }
        conversationSwitchJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            while (true) {
                delay(250L)
                val nowMs = System.currentTimeMillis()
                val routeAt = switchingUrlChangedAtMs
                val routeSettled = routeAt > 0L && (nowMs - routeAt) >= 2_500L
                if (routeSettled || (nowMs - startedAt) >= 6_500L) {
                    break
                }
            }
            switchingTargetUrl = ""
            switchingFromTitle = ""
            switchingFromMessagesSig = ""
            switchingUrlChangedAtMs = 0L
            _uiState.update {
                it.copy(
                    isSwitchingConversation = false,
                    switchingFromUrl = null,
                    switchingTargetTitle = ""
                )
            }
        }
    }

    fun onStartModelRefresh() {
        _uiState.update { it.copy(isRefreshingModels = true) }
        viewModelScope.launch {
            delay(2_000L)
            _uiState.update { it.copy(isRefreshingModels = false) }
        }
    }

    fun onSelectModelOptimistic(modelName: String, effortTier: String) {
        val label = if (effortTier.isNotBlank() && !modelName.endsWith(effortTier, ignoreCase = true)) {
            "$modelName $effortTier"
        } else {
            modelName
        }
        modelMutationLockUntilMs = System.currentTimeMillis() + 2_200L
        modelOptimisticJob?.cancel()
        repository.saveCachedModel(label)
        _uiState.update { state ->
            val updatedModels = state.snapshot.models.map { m ->
                val match = m.name.equals(modelName, ignoreCase = true)
                m.copy(
                    isSelected = match,
                    tier = if (match && effortTier.isNotBlank()) effortTier else m.tier
                )
            }
            state.copy(
                optimisticModelLabel = label,
                cachedModelLabel = label,
                snapshot = state.snapshot.copy(
                    activeModelName = label,
                    models = updatedModels
                )
            )
        }
        modelOptimisticJob = viewModelScope.launch {
            delay(2_400L)
            _uiState.update { it.copy(optimisticModelLabel = null) }
        }
    }

    fun onStartQuotaRefresh() {
        _uiState.update {
            it.copy(
                isRefreshingQuota = true,
                quotaStatusMessage = "Syncing usage…"
            )
        }
        viewModelScope.launch {
            delay(4_500L)
            _uiState.update { state ->
                if (state.isRefreshingQuota) {
                    state.copy(
                        isRefreshingQuota = false,
                        quotaStatusMessage = "Synced with session"
                    )
                } else {
                    state
                }
            }
        }
    }

    fun updateComposerText(text: String) {
        _composerText.value = text
    }

    fun appendPromptToComposer(promptText: String) {
        val current = _composerText.value
        _composerText.value = if (current.isBlank()) {
            promptText
        } else {
            current.trimEnd() + "\n" + promptText
        }
    }

    fun addOptimisticUserMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val optId = "opt-${UUID.randomUUID()}"
        _composerText.value = ""
        _uiState.update { current ->
            val serverCount = current.snapshot.messages.count {
                it.role == MessageRole.USER && it.markdownContent.trim() == clean
            }
            val pendingCount = current.optimisticMessages.count {
                it.markdownContent.trim() == clean
            }
            val optimistic = ChatMessage(
                id = optId,
                role = MessageRole.USER,
                markdownContent = clean,
                isOptimistic = true,
                optimisticBaselineCount = serverCount + pendingCount
            )
            current.copy(
                optimisticMessages = current.optimisticMessages + optimistic
            )
        }
        viewModelScope.launch {
            delay(20_000L)
            _uiState.update { current ->
                if (current.optimisticMessages.any { it.id == optId }) {
                    current.copy(
                        optimisticMessages = current.optimisticMessages.filterNot { it.id == optId }
                    )
                } else {
                    current
                }
            }
        }
    }

    fun startNewChatOptimistic() {
        deferredScrollSnapshot = null
        conversationSwitchJob?.cancel()
        loadOlderJob?.cancel()
        switchingTargetUrl = ""
        switchingFromTitle = ""
        switchingFromMessagesSig = ""
        switchingUrlChangedAtMs = 0L
        _composerText.value = ""
        _uiState.update {
            it.copy(
                optimisticMessages = emptyList(),
                isSwitchingConversation = false,
                isLoadingOlderMessages = false,
                workspaceArtifacts = emptyList(),
                switchingFromUrl = null,
                switchingTargetTitle = "",
                snapshot = it.snapshot.copy(
                    currentUrl = "https://antigravity.google.com/remote",
                    conversationTitle = "Antigravity",
                    messages = emptyList(),
                    isStreaming = false,
                    conversations = it.snapshot.conversations.map { conv ->
                        if (conv.isActive) conv.copy(isActive = false) else conv
                    }
                )
            )
        }
    }

    fun clearOptimisticMessages() {
        _uiState.update { it.copy(optimisticMessages = emptyList()) }
    }

    fun toggleReasoningBlock(blockId: String, defaultExpanded: Boolean) {
        _uiState.update { state ->
            val current = state.expandedReasoningIds[blockId] ?: defaultExpanded
            state.copy(
                expandedReasoningIds = state.expandedReasoningIds + (blockId to !current)
            )
        }
    }

    fun setModelSheetOpen(open: Boolean) = _uiState.update { it.copy(isModelSheetOpen = open) }
    fun setQuotaSheetOpen(open: Boolean) = _uiState.update {
        it.copy(
            isQuotaSheetOpen = open,
            quotaStatusMessage = if (!open) null else it.quotaStatusMessage
        )
    }
    fun setSettingsSheetOpen(open: Boolean) = _uiState.update { it.copy(isSettingsSheetOpen = open) }
    fun setQuickPromptsSheetOpen(open: Boolean) = _uiState.update { it.copy(isQuickPromptsSheetOpen = open) }
    fun setWorkspaceSheetOpen(open: Boolean) = _uiState.update {
        it.copy(
            isWorkspaceSheetOpen = open,
            isLoadingWorkspaceArtifacts = if (open) true else it.isLoadingWorkspaceArtifacts
        )
    }
    fun setProjectSheetOpen(open: Boolean) = _uiState.update { it.copy(isProjectSheetOpen = open) }
    fun setPermissionsSheetOpen(open: Boolean) = _uiState.update { it.copy(isPermissionsSheetOpen = open) }

    fun onStartLoadOlderMessages() {
        deferredScrollSnapshot = null
        loadOlderJob?.cancel()
        val currentMsgs = _uiState.value.snapshot.messages
        loadOlderBaselineCount = currentMsgs.size
        loadOlderBaselineFirstSig = currentMsgs.firstOrNull()?.let { first ->
            "${first.id}:${first.markdownContent.length}:${first.reasoningBlocks.sumOf { it.sections.size }}"
        }.orEmpty()
        _uiState.update {
            it.copy(isLoadingOlderMessages = true)
        }
        loadOlderJob = viewModelScope.launch {
            delay(5_000L)
            _uiState.update {
                if (it.isLoadingOlderMessages) {
                    it.copy(isLoadingOlderMessages = false)
                } else {
                    it
                }
            }
        }
    }

    fun openDiffReviewLoading(title: String = "Review Changes") {
        _uiState.update { it.copy(diffReviewData = DiffReviewData(title = title, isLoading = true)) }
    }

    fun onDiffReviewLoaded(data: DiffReviewData) {
        _uiState.update { it.copy(diffReviewData = data) }
    }

    fun closeDiffReview() {
        _uiState.update { it.copy(diffReviewData = null) }
    }

    fun openDocumentLoading(title: String) {
        _uiState.update { it.copy(documentViewerData = DocumentViewerData(title = title, isLoading = true)) }
    }

    fun onDocumentLoaded(data: DocumentViewerData) {
        _uiState.update { it.copy(documentViewerData = data) }
    }

    fun closeDocumentViewer() {
        _uiState.update { it.copy(documentViewerData = null) }
    }

    fun onStartWorkspaceArtifactsLoad() {
        _uiState.update { it.copy(isLoadingWorkspaceArtifacts = true) }
    }

    fun onWorkspaceArtifactsLoaded(items: List<WorkspaceArtifactItem>) {
        _uiState.update { it.copy(workspaceArtifacts = items, isLoadingWorkspaceArtifacts = false) }
    }

    // Settings mutations
    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.updateThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }
    fun setFontScale(percent: Int) = viewModelScope.launch { repository.setFontScale(percent) }
    fun setHapticFeedback(enabled: Boolean) = viewModelScope.launch { repository.setHapticFeedback(enabled) }
    fun setAutoExpandReasoning(enabled: Boolean) = viewModelScope.launch { repository.setAutoExpandReasoning(enabled) }
    fun setKeepScreenAwake(enabled: Boolean) = viewModelScope.launch { repository.setKeepScreenAwake(enabled) }
    fun setWebInspectorMode(enabled: Boolean) = viewModelScope.launch { repository.setWebInspectorMode(enabled) }

    // Quick Prompts
    fun addQuickPrompt(title: String, body: String, category: String) = viewModelScope.launch {
        repository.addQuickPrompt(title, body, category)
    }

    fun removeQuickPrompt(id: String) = viewModelScope.launch {
        repository.removeQuickPrompt(id)
    }

    companion object {
        private val TRAILING_EFFORT_REGEX = Regex(
            "\\s*(?:\\((Minimal|Low|Medium|High|Extra|Fast)\\)|\\b(Minimal|Low|Medium|High|Extra|Fast))\\s*$",
            RegexOption.IGNORE_CASE
        )
    }
}
