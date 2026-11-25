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
    val composerText: String = "",
    val expandedReasoningIds: Map<String, Boolean> = emptyMap(),
    val isModelSheetOpen: Boolean = false,
    val isQuotaSheetOpen: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val isQuickPromptsSheetOpen: Boolean = false,
    val errorMessage: String? = null,
    val lastSyncTimeMillis: Long = 0L
) {
    val displayedMessages: List<ChatMessage>
        get() {
            if (optimisticMessages.isEmpty()) return snapshot.messages
            val serverUserTexts = snapshot.messages
                .filter { it.role == MessageRole.USER }
                .map { it.markdownContent.trim() }
                .toSet()
            val pending = optimisticMessages.filterNot { it.markdownContent.trim() in serverUserTexts }
            return snapshot.messages + pending
        }

    val activeModelLabel: String
        get() = snapshot.activeModelName.ifBlank {
            snapshot.models.firstOrNull { it.isSelected }?.name ?: "Antigravity Model"
        }

    val availableModels: List<ModelOption>
        get() = if (snapshot.models.isNotEmpty()) {
            snapshot.models
        } else if (snapshot.activeModelName.isNotBlank()) {
            listOf(
                ModelOption(
                    id = snapshot.activeModelName,
                    name = snapshot.activeModelName,
                    description = "Currently active model",
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
            current.copy(
                bridgeStatus = nextStatus,
                snapshot = snapshot,
                optimisticMessages = remainingOptimistic,
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
    fun setQuotaSheetOpen(open: Boolean) = _uiState.update { it.copy(isQuotaSheetOpen = open) }
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
