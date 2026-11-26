package com.antigravity.wrapper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.antigravityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "antigravity_native_prefs"
)

class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.antigravityDataStore
    private val fastCache = appContext.getSharedPreferences("antigravity_fast_cache", Context.MODE_PRIVATE)

    fun readCachedProject(): String = fastCache.getString(CACHE_LAST_PROJECT, "").orEmpty()

    fun readCachedModel(): String = fastCache.getString(CACHE_LAST_MODEL, "").orEmpty()

    fun readCachedQuota(): QuotaInfo {
        val left = if (fastCache.contains(CACHE_QUOTA_LEFT)) fastCache.getFloat(CACHE_QUOTA_LEFT, -1f).takeIf { it >= 0f } else null
        val fiveH = if (fastCache.contains(CACHE_QUOTA_5H)) fastCache.getFloat(CACHE_QUOTA_5H, -1f).takeIf { it >= 0f } else null
        val weekly = if (fastCache.contains(CACHE_QUOTA_WEEKLY)) fastCache.getFloat(CACHE_QUOTA_WEEKLY, -1f).takeIf { it >= 0f } else null
        if (left == null && fiveH == null && weekly == null) return QuotaInfo()
        return QuotaInfo(
            leftPercent = left ?: fiveH ?: weekly,
            fiveHourLeftPercent = fiveH,
            fiveHourResetLabel = fastCache.getString(CACHE_QUOTA_5H_RESET, "").orEmpty(),
            weeklyLeftPercent = weekly,
            weeklyResetLabel = fastCache.getString(CACHE_QUOTA_WEEKLY_RESET, "").orEmpty(),
            remainingLabel = fastCache.getString(CACHE_QUOTA_REMAINING, "").orEmpty(),
            planLabel = fastCache.getString(CACHE_QUOTA_PLAN, "Antigravity").orEmpty().ifBlank { "Antigravity" },
            isLiveFromServer = false
        )
    }

    fun readCachedConversations(): List<ChatConversation> {
        val raw = fastCache.getString(CACHE_CONVERSATIONS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split('\u001E').mapNotNull { row ->
            val parts = row.split('\u001F')
            if (parts.size < 4) return@mapNotNull null
            val title = parts[1].trim()
            if (title.isBlank()) return@mapNotNull null
            ChatConversation(
                id = parts[0],
                title = title,
                url = parts[2],
                updatedLabel = parts[3]
            )
        }
    }

    fun saveCachedProject(project: String) {
        val clean = project.trim()
        if (clean.isNotEmpty() && clean != readCachedProject()) {
            fastCache.edit().putString(CACHE_LAST_PROJECT, clean).apply()
        }
    }

    fun saveCachedModel(model: String) {
        val clean = model.trim()
        if (clean.isNotEmpty() && clean != readCachedModel()) {
            fastCache.edit().putString(CACHE_LAST_MODEL, clean).apply()
        }
    }

    fun saveCachedQuota(quota: QuotaInfo) {
        val left = quota.headlineLeftPercent ?: return
        val editor = fastCache.edit()
            .putFloat(CACHE_QUOTA_LEFT, left.coerceIn(0f, 100f))
            .putString(CACHE_QUOTA_REMAINING, quota.remainingLabel)
            .putString(CACHE_QUOTA_PLAN, quota.planLabel)
            .putString(CACHE_QUOTA_5H_RESET, quota.fiveHourResetLabel)
            .putString(CACHE_QUOTA_WEEKLY_RESET, quota.weeklyResetLabel)
        if (quota.fiveHourLeftPercent != null) {
            editor.putFloat(CACHE_QUOTA_5H, quota.fiveHourLeftPercent.coerceIn(0f, 100f))
        }
        if (quota.weeklyLeftPercent != null) {
            editor.putFloat(CACHE_QUOTA_WEEKLY, quota.weeklyLeftPercent.coerceIn(0f, 100f))
        }
        editor.apply()
    }

    fun saveCachedConversations(conversations: List<ChatConversation>) {
        if (conversations.isEmpty()) return
        val encoded = conversations.take(40).joinToString("\u001E") { c ->
            listOf(
                c.id.replace("\u001F", " ").replace("\u001E", " "),
                c.title.replace("\u001F", " ").replace("\u001E", " "),
                c.url.replace("\u001F", " ").replace("\u001E", " "),
                c.updatedLabel.replace("\u001F", " ").replace("\u001E", " ")
            ).joinToString("\u001F")
        }
        fastCache.edit().putString(CACHE_CONVERSATIONS, encoded).apply()
    }

    fun clearSessionCache() {
        fastCache.edit()
            .remove(CACHE_LAST_PROJECT)
            .remove(CACHE_LAST_MODEL)
            .remove(CACHE_QUOTA_LEFT)
            .remove(CACHE_QUOTA_5H)
            .remove(CACHE_QUOTA_WEEKLY)
            .remove(CACHE_QUOTA_5H_RESET)
            .remove(CACHE_QUOTA_WEEKLY_RESET)
            .remove(CACHE_QUOTA_REMAINING)
            .remove(CACHE_QUOTA_PLAN)
            .remove(CACHE_CONVERSATIONS)
            .apply()
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromKey(prefs[KEY_THEME_MODE]),
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            fontScalePercent = (prefs[KEY_FONT_SCALE] ?: 100).coerceIn(85, 135),
            hapticFeedbackEnabled = prefs[KEY_HAPTIC_FEEDBACK] ?: true,
            autoExpandReasoning = prefs[KEY_AUTO_EXPAND_REASONING] ?: false,
            keepScreenAwake = prefs[KEY_KEEP_SCREEN_AWAKE] ?: false,
            webInspectorMode = prefs[KEY_WEB_INSPECTOR] ?: false
        )
    }

    val quickPrompts: Flow<List<QuickPrompt>> = dataStore.data.map { prefs ->
        val stored = prefs[KEY_QUICK_PROMPTS]
        if (stored == null) DEFAULT_PROMPTS else RecordCodec.decodePrompts(stored)
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.storageKey }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setFontScale(percent: Int) {
        dataStore.edit { it[KEY_FONT_SCALE] = percent.coerceIn(85, 135) }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        dataStore.edit { it[KEY_HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setAutoExpandReasoning(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_EXPAND_REASONING] = enabled }
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) {
        dataStore.edit { it[KEY_KEEP_SCREEN_AWAKE] = enabled }
    }

    suspend fun setWebInspectorMode(enabled: Boolean) {
        dataStore.edit { it[KEY_WEB_INSPECTOR] = enabled }
    }

    suspend fun addQuickPrompt(title: String, body: String, category: String) {
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        if (cleanTitle.isBlank() || cleanBody.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_QUICK_PROMPTS]?.let { RecordCodec.decodePrompts(it) }
                ?: DEFAULT_PROMPTS
            val prompt = QuickPrompt(
                id = UUID.randomUUID().toString(),
                title = cleanTitle,
                body = cleanBody,
                category = category.trim().ifBlank { "Custom" }
            )
            prefs[KEY_QUICK_PROMPTS] = RecordCodec.encodePrompts(listOf(prompt) + current)
        }
    }

    suspend fun removeQuickPrompt(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_QUICK_PROMPTS]?.let { RecordCodec.decodePrompts(it) }
                ?: DEFAULT_PROMPTS
            prefs[KEY_QUICK_PROMPTS] = RecordCodec.encodePrompts(current.filterNot { it.id == id })
        }
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_FONT_SCALE = intPreferencesKey("font_scale_v2")
        private val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        private val KEY_AUTO_EXPAND_REASONING = booleanPreferencesKey("auto_expand_reasoning")
        private val KEY_KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        private val KEY_WEB_INSPECTOR = booleanPreferencesKey("web_inspector_mode")
        private val KEY_QUICK_PROMPTS = stringPreferencesKey("quick_prompts_v1")
        private const val CACHE_LAST_PROJECT = "last_project"
        private const val CACHE_LAST_MODEL = "last_model"
        private const val CACHE_QUOTA_LEFT = "last_quota_left"
        private const val CACHE_QUOTA_5H = "last_quota_5h"
        private const val CACHE_QUOTA_WEEKLY = "last_quota_weekly"
        private const val CACHE_QUOTA_5H_RESET = "last_quota_5h_reset"
        private const val CACHE_QUOTA_WEEKLY_RESET = "last_quota_weekly_reset"
        private const val CACHE_QUOTA_REMAINING = "last_quota_remaining"
        private const val CACHE_QUOTA_PLAN = "last_quota_plan"
        private const val CACHE_CONVERSATIONS = "last_conversations_v1"

        val DEFAULT_PROMPTS = listOf(
            QuickPrompt(
                id = "review",
                title = "Code Review",
                body = "Review my recent changes for bugs, edge cases, and maintainability improvements. List findings by severity.",
                category = "Code"
            ),
            QuickPrompt(
                id = "debug",
                title = "Root-Cause Debug",
                body = "Diagnose the root cause of this issue, explain why it happens, and propose the minimal safe fix with tests.",
                category = "Debug"
            ),
            QuickPrompt(
                id = "plan",
                title = "Architecture Plan",
                body = "Propose a step-by-step implementation plan with trade-offs before making any edits.",
                category = "Plan"
            ),
            QuickPrompt(
                id = "math",
                title = "Explain Step-by-Step",
                body = "Explain this concept clearly with formulas in LaTeX and a concise worked example.",
                category = "Learn"
            )
        )
    }
}
