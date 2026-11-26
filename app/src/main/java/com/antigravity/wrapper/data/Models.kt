package com.antigravity.wrapper.data

import androidx.compose.runtime.Immutable

enum class ThemeMode(val storageKey: String, val label: String) {
    SYSTEM("system", "System"),
    DARK("dark", "Dark"),
    SLATE("slate", "Slate"),
    AMOLED("amoled", "AMOLED"),
    LIGHT("light", "Light");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: SLATE
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
    val themeMode: ThemeMode = ThemeMode.SLATE,
    val dynamicColor: Boolean = true,
    val fontScalePercent: Int = 100,
    val hapticFeedbackEnabled: Boolean = true,
    val autoExpandReasoning: Boolean = false,
    val keepScreenAwake: Boolean = false,
    val webInspectorMode: Boolean = false
)

@Immutable
data class ChatConversation(
    val id: String,
    val title: String,
    val url: String = "",
    val projectName: String = "",
    val preview: String = "",
    val updatedLabel: String = "",
    val isActive: Boolean = false
)

@Immutable
data class ReasoningBlock(
    val id: String,
    val title: String,
    val durationLabel: String = "",
    val content: String,
    val isStreaming: Boolean = false
)

@Immutable
data class ArtifactCard(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val actionLabel: String = "Open",
    val url: String = "",
    val addedLines: Int? = null,
    val removedLines: Int? = null
)

@Immutable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val markdownContent: String,
    val timestampLabel: String = "",
    val reasoningBlocks: List<ReasoningBlock> = emptyList(),
    val artifacts: List<ArtifactCard> = emptyList(),
    val isStreaming: Boolean = false,
    val isOptimistic: Boolean = false
)

@Immutable
data class ModelOption(
    val id: String,
    val name: String,
    val tier: String = "",
    val description: String = "",
    val hasEffortLevels: Boolean = false,
    val effortOptions: List<String> = emptyList(),
    val isSelected: Boolean = false
) {
    val displayLabel: String
        get() = if (tier.isNotBlank() && !name.endsWith(tier, ignoreCase = true)) {
            "$name $tier"
        } else {
            name
        }
}

@Immutable
data class QuotaLimitItem(
    val id: String,
    val groupName: String,
    val windowLabel: String,
    val leftPercent: Float? = null,
    val resetLabel: String = ""
)

@Immutable
data class QuotaInfo(
    val usedPercent: Float? = null,
    val leftPercent: Float? = null,
    val fiveHourLeftPercent: Float? = null,
    val fiveHourResetLabel: String = "",
    val weeklyLeftPercent: Float? = null,
    val weeklyResetLabel: String = "",
    val limits: List<QuotaLimitItem> = emptyList(),
    val remainingLabel: String = "",
    val resetLabel: String = "",
    val planLabel: String = "Antigravity",
    val rawSummary: String = "",
    val isLiveFromServer: Boolean = false
) {
    val headlineLeftPercent: Float?
        get() = fiveHourLeftPercent ?: leftPercent ?: weeklyLeftPercent ?: usedPercent?.let { (100f - it).coerceIn(0f, 100f) }
}

@Immutable
data class ProjectOption(
    val id: String,
    val name: String,
    val isSelected: Boolean = false
)

@Immutable
data class WorkspaceArtifactItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val url: String = "",
    val kind: String = "artifact"
)

enum class DiffLineKind {
    CONTEXT,
    ADDED,
    REMOVED,
    HUNK
}

@Immutable
data class DiffLine(
    val lineNumber: String = "",
    val text: String,
    val kind: DiffLineKind = DiffLineKind.CONTEXT
)

@Immutable
data class DiffFile(
    val id: String,
    val fileName: String,
    val path: String = "",
    val addedLines: Int = 0,
    val removedLines: Int = 0,
    val lines: List<DiffLine> = emptyList()
)

@Immutable
data class DiffReviewData(
    val title: String = "Review Changes",
    val subtitle: String = "",
    val files: List<DiffFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@Immutable
data class DocumentViewerData(
    val title: String = "",
    val markdownContent: String = "",
    val rawContent: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@Immutable
data class BridgeSnapshot(
    val currentUrl: String = "",
    val conversationTitle: String = "Antigravity",
    val authRequired: Boolean = false,
    val isStreaming: Boolean = false,
    val activeModelName: String = "",
    val activeProject: String = "",
    val permissionsPreset: String = "",
    val models: List<ModelOption> = emptyList(),
    val projects: List<ProjectOption> = emptyList(),
    val conversations: List<ChatConversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val quota: QuotaInfo = QuotaInfo(),
    val hasOlderMessages: Boolean = false,
    val hasSidePanel: Boolean = false,
    val composerReady: Boolean = false,
    val diagnosticsNote: String = ""
)

data class QuickPrompt(
    val id: String,
    val title: String,
    val body: String,
    val category: String
)
