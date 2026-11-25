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

    private val dataStore = context.applicationContext.antigravityDataStore

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
