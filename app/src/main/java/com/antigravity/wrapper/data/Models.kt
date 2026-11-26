package com.antigravity.wrapper.data

import androidx.compose.runtime.Immutable

enum class ThemeMode(
    val storageKey: String,
    val label: String,
    val isDark: Boolean,
    val swatchArgb: Long
) {
    ANTIGRAVITY_DARK("antigravity_dark", "Antigravity Dark", true, 0xFFA8C7FAL),
    PURE_OLED_BLACK("pure_oled_black", "Pure OLED Black", true, 0xFFE2E8F0L),
    ANTIGRAVITY_LIGHT("antigravity_light", "Antigravity Light", false, 0xFF1A56DBL),
    CATPPUCCIN_MOCHA("catppuccin_mocha", "Catppuccin Mocha", true, 0xFFCBA6F7L),
    CATPPUCCIN_LATTE("catppuccin_latte", "Catppuccin Latte", false, 0xFF8839EFL),
    DRACULA("dracula", "Dracula", true, 0xFFBD93F9L),
    NORD("nord", "Nord", true, 0xFF88C0D0L),
    TOKYO_NIGHT("tokyo_night", "Tokyo Night", true, 0xFF7AA2F7L),
    GRUVBOX_DARK("gruvbox_dark", "Gruvbox Dark", true, 0xFFFABD2FL),
    GRUVBOX_LIGHT("gruvbox_light", "Gruvbox Light", false, 0xFFD79921L),
    ONE_DARK_PRO("one_dark_pro", "One Dark Pro", true, 0xFF61AFEFL),
    SOLARIZED_DARK("solarized_dark", "Solarized Dark", true, 0xFF268BD2L),
    SOLARIZED_LIGHT("solarized_light", "Solarized Light", false, 0xFF2AA198L),
    MONOKAI_PRO("monokai_pro", "Monokai Pro", true, 0xFFFFD866L),
    ROSE_PINE("rose_pine", "Rosé Pine", true, 0xFFEBBCBAL),
    ROSE_PINE_DAWN("rose_pine_dawn", "Rosé Pine Dawn", false, 0xFFD7827EL),
    KANAGAWA_WAVE("kanagawa_wave", "Kanagawa Wave", true, 0xFF7E9CD8L),
    EVERFOREST_DARK("everforest_dark", "Everforest Dark", true, 0xFFA7C080L),
    AYU_MIRAGE("ayu_mirage", "Ayu Mirage", true, 0xFFFFCC66L),
    GITHUB_DARK_DEFAULT("github_dark_default", "GitHub Dark Default", true, 0xFF58A6FFL),
    SLATE("slate", "Slate", true, 0xFFE2C290L),
    SYSTEM("system", "System", true, 0xFF8AB4F8L);

    companion object {
        fun fromKey(key: String?): ThemeMode = when (key) {
            "dark" -> ANTIGRAVITY_DARK
            "amoled" -> PURE_OLED_BLACK
            "light" -> ANTIGRAVITY_LIGHT
            else -> entries.firstOrNull { it.storageKey == key } ?: ANTIGRAVITY_DARK
        }
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
    val themeMode: ThemeMode = ThemeMode.ANTIGRAVITY_DARK,
    val dynamicColor: Boolean = false,
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

enum class ReasoningStepKind {
    COMMAND,
    FILE,
    SECTION,
    NOTE
}

@Immutable
data class ReasoningStepItem(
    val id: String,
    val title: String,
    val kind: ReasoningStepKind = ReasoningStepKind.NOTE,
    val detail: String = "",
    val fileName: String = "",
    val lineRange: String = "",
    val badgeText: String = "",
    val actionVerb: String = "",
    val isExpandable: Boolean = false
)

@Immutable
data class ReasoningSection(
    val id: String,
    val title: String,
    val steps: List<ReasoningStepItem> = emptyList(),
    val defaultExpanded: Boolean = false
)

@Immutable
data class ReasoningBlock(
    val id: String,
    val title: String,
    val durationLabel: String = "",
    val content: String = "",
    val sections: List<ReasoningSection> = emptyList(),
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

enum class AssistantSegmentKind {
    REASONING,
    MARKDOWN
}

@Immutable
data class AssistantSegment(
    val id: String,
    val kind: AssistantSegmentKind,
    val markdown: String = "",
    val reasoningBlock: ReasoningBlock? = null
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
    val isOptimistic: Boolean = false,
    val optimisticBaselineCount: Int = 0,
    val segments: List<AssistantSegment> = emptyList()
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
