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
    val errorMessage: String? = null,
    val lastSyncTimeMillis: Long = 0L
) {
    val displayedMessages: List<ChatMessage>
        get() {
            if (isSwitchingConversation) return emptyList()
            if (optimisticMessages.isEmpty()) return snapshot.messages
            val serverUserTexts = snapshot.messages
                .filter { it.role == MessageRole.USER }
                .map { it.markdownContent.trim() }
                .toSet()
            val pending = optimisticMessages.filterNot { it.markdownContent.trim() in serverUserTexts }
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

    fun onBridgeSnapshot(snapshot: BridgeSnapshot) {
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
        _uiState.update { current ->
            val nextStatus = when {
                snapshot.authRequired -> BridgeStatus.AUTH_REQUIRED
                else -> BridgeStatus.CONNECTED
            }
            val serverUserTexts = snapshot.messages
                .filter { it.role == MessageRole.USER }
                .map { it.markdownContent.trim() }
                .toSet()
            val remainingOptimistic = current.optimisticMessages.filterNot {
                it.markdownContent.trim() in serverUserTexts
            }
            val expectedOptimistic = current.optimisticModelLabel?.trim().orEmpty()
            val incomingMatchesOptimistic = expectedOptimistic.isNotEmpty() &&
                snapshot.activeModelName.equals(expectedOptimistic, ignoreCase = true)
            val clearOptimisticModel = expectedOptimistic.isNotEmpty() &&
                (incomingMatchesOptimistic || (!modelHoldActive && snapshot.activeModelName.isNotBlank()))

            val arrivedAtNewThread = current.isSwitchingConversation &&
                snapshot.messages.isNotEmpty() &&
                (current.switchingFromUrl.isNullOrBlank() || snapshot.currentUrl != current.switchingFromUrl)
            val stillSwitching = if (arrivedAtNewThread) false else current.isSwitchingConversation

            // Preserve last non-empty conversations, models, projects, and quota across partial route snapshots
            val cleanUrl = snapshot.currentUrl.substringBefore('?').substringBefore('#')
            val mergedConversations = if (snapshot.conversations.isNotEmpty()) {
                snapshot.conversations
            } else {
                current.snapshot.conversations.map { conv ->
                    val convUrl = conv.url.substringBefore('?').substringBefore('#')
                    val active = (convUrl.isNotBlank() && convUrl == cleanUrl) ||
                        (snapshot.conversationTitle != "Antigravity" && conv.title == snapshot.conversationTitle)
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
                mergedModels.map { m ->
                    val sel = expectedOptimistic.startsWith(m.name, ignoreCase = true)
                    val effort = if (sel) {
                        Regex("\\b(Low|Medium|High|Extra|Fast)\\b", RegexOption.IGNORE_CASE)
                            .find(expectedOptimistic)?.groupValues?.get(1) ?: m.tier
                    } else {
                        m.tier
                    }
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
                isLoadingOlderMessages = false,
                errorMessage = null,
                lastSyncTimeMillis = now
            )
        }
    }

    fun onBridgeNavigatingToAuth() {
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
        _uiState.update {
            it.copy(
                bridgeStatus = if (isOffline) BridgeStatus.OFFLINE else BridgeStatus.ERROR,
                isRefreshingModels = false,
                isRefreshingQuota = false,
                isSwitchingConversation = false,
                switchingFromUrl = null,
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

    fun onStartConversationSwitch() {
        _uiState.update {
            it.copy(
                optimisticMessages = emptyList(),
                optimisticModelLabel = null,
                isSwitchingConversation = true,
                switchingFromUrl = it.snapshot.currentUrl
            )
        }
        viewModelScope.launch {
            delay(3_500L)
            _uiState.update {
                it.copy(
                    isSwitchingConversation = false,
                    switchingFromUrl = null
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
        viewModelScope.launch {
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
        val optimistic = ChatMessage(
            id = "opt-${UUID.randomUUID()}",
            role = MessageRole.USER,
            markdownContent = clean,
            isOptimistic = true
        )
        _composerText.value = ""
        _uiState.update {
            it.copy(
                optimisticMessages = it.optimisticMessages + optimistic
            )
        }
    }

    fun startNewChatOptimistic() {
        _composerText.value = ""
        _uiState.update {
            it.copy(
                optimisticMessages = emptyList(),
                isSwitchingConversation = false,
                switchingFromUrl = null,
                snapshot = it.snapshot.copy(
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
        _uiState.update {
            it.copy(
                isLoadingOlderMessages = true,
                snapshot = it.snapshot.copy(hasOlderMessages = false)
            )
        }
        viewModelScope.launch {
            delay(3_500L)
            _uiState.update { it.copy(isLoadingOlderMessages = false) }
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
}
