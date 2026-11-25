package com.antigravity.wrapper.data

enum class ThemeMode(val storageKey: String, val label: String) {
    SYSTEM("system", "System"),
    DARK("dark", "Dark"),
    AMOLED("amoled", "AMOLED"),
    LIGHT("light", "Light");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: DARK
    }
}

enum class BridgeStatus(val label: String) {
    CONNECTING("Connecting…"),
    AUTH_REQUIRED("Sign in required"),
    CONNECTED("Synced"),
    OFFLINE("Offline"),
    ERROR("Connection error")
}

enum class MessageRole {
    USER,
    ASSISTANT
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = true,
    val fontScalePercent: Int = 100,
    val hapticFeedbackEnabled: Boolean = true,
    val autoExpandReasoning: Boolean = false,
    val keepScreenAwake: Boolean = false,
    val webInspectorMode: Boolean = false
)

data class ChatConversation(
    val id: String,
    val title: String,
    val url: String = "",
    val preview: String = "",
    val updatedLabel: String = "",
    val isActive: Boolean = false
)

data class ReasoningBlock(
    val id: String,
    val title: String,
    val durationLabel: String = "",
    val content: String,
    val isStreaming: Boolean = false
)

data class ArtifactCard(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val actionLabel: String = "Open",
    val url: String = "",
    val addedLines: Int? = null,
    val removedLines: Int? = null
)

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val markdownContent: String,
    val reasoningBlocks: List<ReasoningBlock> = emptyList(),
    val artifacts: List<ArtifactCard> = emptyList(),
    val isStreaming: Boolean = false,
    val isOptimistic: Boolean = false
)

data class ModelOption(
    val id: String,
    val name: String,
    val tier: String = "",
    val description: String = "",
    val isSelected: Boolean = false
)

data class QuotaInfo(
    val usedPercent: Float? = null,
    val remainingLabel: String = "",
    val resetLabel: String = "",
    val planLabel: String = "Antigravity",
    val rawSummary: String = "",
    val isLiveFromServer: Boolean = false
)

data class BridgeSnapshot(
    val currentUrl: String = "",
    val conversationTitle: String = "New Chat",
    val authRequired: Boolean = false,
    val isStreaming: Boolean = false,
    val activeModelName: String = "",
    val models: List<ModelOption> = emptyList(),
    val conversations: List<ChatConversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val quota: QuotaInfo = QuotaInfo(),
    val composerReady: Boolean = false,
    val diagnosticsNote: String = ""
)

data class QuickPrompt(
    val id: String,
    val title: String,
    val body: String,
    val category: String
)
