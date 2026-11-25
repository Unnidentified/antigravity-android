package com.antigravity.wrapper.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.wrapper.data.AppRepository
import com.antigravity.wrapper.data.AppSettings
import com.antigravity.wrapper.data.BridgeSnapshot
import com.antigravity.wrapper.data.BridgeStatus
import com.antigravity.wrapper.data.ChatMessage
import com.antigravity.wrapper.data.MessageRole
import com.antigravity.wrapper.data.ModelOption
import com.antigravity.wrapper.data.QuickPrompt
import com.antigravity.wrapper.data.ThemeMode
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
    val composerText: String = "",
    val expandedReasoningIds: Map<String, Boolean> = emptyMap(),
    val isModelSheetOpen: Boolean = false,
    val isQuotaSheetOpen: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val isQuickPromptsSheetOpen: Boolean = false,
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
                snapshot.models.firstOrNull { it.isSelected }?.displayLabel ?: ""
            }

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

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onBridgeSnapshot(snapshot: BridgeSnapshot) {
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
            val clearOptimisticModel = current.optimisticModelLabel != null &&
                snapshot.activeModelName.isNotBlank() &&
                snapshot.activeModelName.contains(
                    current.optimisticModelLabel.substringBefore(" "),
                    ignoreCase = true
                )
            val arrivedAtNewThread = current.isSwitchingConversation &&
                snapshot.messages.isNotEmpty() &&
                (current.switchingFromUrl.isNullOrBlank() || snapshot.currentUrl != current.switchingFromUrl)
            val stillSwitching = if (arrivedAtNewThread) false else current.isSwitchingConversation
            current.copy(
                bridgeStatus = nextStatus,
                snapshot = snapshot,
                optimisticMessages = remainingOptimistic,
                optimisticModelLabel = if (clearOptimisticModel) null else current.optimisticModelLabel,
                isRefreshingModels = if (snapshot.models.isNotEmpty()) false else current.isRefreshingModels,
                isRefreshingQuota = false,
                quotaStatusMessage = if (current.isRefreshingQuota) {
                    if (snapshot.quota.isLiveFromServer) "Updated just now" else "Synced with session"
                } else {
                    current.quotaStatusMessage
                },
                isSwitchingConversation = stillSwitching,
                switchingFromUrl = if (stillSwitching) current.switchingFromUrl else null,
                errorMessage = null,
                lastSyncTimeMillis = System.currentTimeMillis()
            )
        }
    }

    fun onBridgeNavigatingToAuth() {
        _uiState.update {
            it.copy(
                bridgeStatus = BridgeStatus.AUTH_REQUIRED,
                snapshot = it.snapshot.copy(authRequired = true),
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
        _uiState.update { it.copy(optimisticModelLabel = label) }
        viewModelScope.launch {
            delay(4_000L)
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
        _uiState.update { it.copy(composerText = text) }
    }

    fun appendPromptToComposer(promptText: String) {
        _uiState.update { state ->
            val next = if (state.composerText.isBlank()) {
                promptText
            } else {
                state.composerText.trimEnd() + "\n" + promptText
            }
            state.copy(composerText = next)
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
        _uiState.update {
            it.copy(
                composerText = "",
                optimisticMessages = it.optimisticMessages + optimistic
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
