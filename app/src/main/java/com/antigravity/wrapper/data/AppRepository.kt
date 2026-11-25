package com.antigravity.wrapper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.antigravity.wrapper.web.UrlRouter
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
            textZoomPercent = (prefs[KEY_TEXT_ZOOM] ?: 100).coerceIn(80, 150),
            desktopMode = prefs[KEY_DESKTOP_MODE] ?: false,
            mobileUiPolish = prefs[KEY_MOBILE_UI_POLISH] ?: true,
            pullToRefreshEnabled = prefs[KEY_PULL_TO_REFRESH] ?: true,
            hapticFeedbackEnabled = prefs[KEY_HAPTIC_FEEDBACK] ?: true,
            keepScreenAwake = prefs[KEY_KEEP_SCREEN_AWAKE] ?: false,
            autoHideChrome = prefs[KEY_AUTO_HIDE_CHROME] ?: false
        )
    }

    val bookmarks: Flow<List<BookmarkItem>> = dataStore.data.map { prefs ->
        val stored = prefs[KEY_BOOKMARKS]
        if (stored == null) {
            DEFAULT_BOOKMARKS
        } else {
            RecordCodec.decodeBookmarks(stored)
        }
    }

    val downloads: Flow<List<DownloadRecord>> = dataStore.data.map { prefs ->
        RecordCodec.decodeDownloads(prefs[KEY_DOWNLOADS])
            .sortedByDescending { it.downloadedAtMillis }
    }

    val quickPrompts: Flow<List<QuickPrompt>> = dataStore.data.map { prefs ->
        val stored = prefs[KEY_QUICK_PROMPTS]
        if (stored == null) {
            DEFAULT_PROMPTS
        } else {
            RecordCodec.decodePrompts(stored)
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.storageKey }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setTextZoom(percent: Int) {
        dataStore.edit { it[KEY_TEXT_ZOOM] = percent.coerceIn(80, 150) }
    }

    suspend fun setDesktopMode(enabled: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_MODE] = enabled }
    }

    suspend fun setMobileUiPolish(enabled: Boolean) {
        dataStore.edit { it[KEY_MOBILE_UI_POLISH] = enabled }
    }

    suspend fun setPullToRefresh(enabled: Boolean) {
        dataStore.edit { it[KEY_PULL_TO_REFRESH] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        dataStore.edit { it[KEY_HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) {
        dataStore.edit { it[KEY_KEEP_SCREEN_AWAKE] = enabled }
    }

    suspend fun setAutoHideChrome(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_HIDE_CHROME] = enabled }
    }

    suspend fun toggleBookmark(title: String, url: String): Boolean {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return false
        var added = false
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOOKMARKS]?.let { RecordCodec.decodeBookmarks(it) }
                ?: DEFAULT_BOOKMARKS
            val existing = current.firstOrNull { it.url == normalizedUrl }
            val updated = if (existing != null) {
                added = false
                current.filterNot { it.id == existing.id }
            } else {
                added = true
                val newItem = BookmarkItem(
                    id = UUID.randomUUID().toString(),
                    title = title.ifBlank { normalizedUrl },
                    url = normalizedUrl,
                    createdAtMillis = System.currentTimeMillis(),
                    pinned = false
                )
                listOf(newItem) + current
            }
            prefs[KEY_BOOKMARKS] = RecordCodec.encodeBookmarks(updated)
        }
        return added
    }

    suspend fun addBookmark(title: String, url: String, pinned: Boolean = false) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOOKMARKS]?.let { RecordCodec.decodeBookmarks(it) }
                ?: DEFAULT_BOOKMARKS
            val filtered = current.filterNot { it.url == normalizedUrl }
            val newItem = BookmarkItem(
                id = UUID.randomUUID().toString(),
                title = title.trim().ifBlank { normalizedUrl },
                url = normalizedUrl,
                createdAtMillis = System.currentTimeMillis(),
                pinned = pinned
            )
            prefs[KEY_BOOKMARKS] = RecordCodec.encodeBookmarks(listOf(newItem) + filtered)
        }
    }

    suspend fun renameBookmark(id: String, newTitle: String) {
        val cleaned = newTitle.trim()
        if (cleaned.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOOKMARKS]?.let { RecordCodec.decodeBookmarks(it) }
                ?: DEFAULT_BOOKMARKS
            val updated = current.map { item ->
                if (item.id == id) item.copy(title = cleaned) else item
            }
            prefs[KEY_BOOKMARKS] = RecordCodec.encodeBookmarks(updated)
        }
    }

    suspend fun togglePinBookmark(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOOKMARKS]?.let { RecordCodec.decodeBookmarks(it) }
                ?: DEFAULT_BOOKMARKS
            val updated = current.map { item ->
                if (item.id == id) item.copy(pinned = !item.pinned) else item
            }
            prefs[KEY_BOOKMARKS] = RecordCodec.encodeBookmarks(updated)
        }
    }

    suspend fun removeBookmark(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOOKMARKS]?.let { RecordCodec.decodeBookmarks(it) }
                ?: DEFAULT_BOOKMARKS
            val updated = current.filterNot { it.id == id }
            prefs[KEY_BOOKMARKS] = RecordCodec.encodeBookmarks(updated)
        }
    }

    suspend fun recordDownload(id: Long, fileName: String, url: String, mimeType: String) {
        dataStore.edit { prefs ->
            val current = RecordCodec.decodeDownloads(prefs[KEY_DOWNLOADS])
            val entry = DownloadRecord(
                id = id,
                fileName = fileName,
                url = url,
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                downloadedAtMillis = System.currentTimeMillis()
            )
            val updated = (listOf(entry) + current.filterNot { it.id == id }).take(MAX_DOWNLOADS)
            prefs[KEY_DOWNLOADS] = RecordCodec.encodeDownloads(updated)
        }
    }

    suspend fun removeDownload(id: Long) {
        dataStore.edit { prefs ->
            val current = RecordCodec.decodeDownloads(prefs[KEY_DOWNLOADS])
            prefs[KEY_DOWNLOADS] = RecordCodec.encodeDownloads(current.filterNot { it.id == id })
        }
    }

    suspend fun clearDownloads() {
        dataStore.edit { prefs ->
            prefs[KEY_DOWNLOADS] = ""
        }
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
        private const val MAX_DOWNLOADS = 100

        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_TEXT_ZOOM = intPreferencesKey("text_zoom")
        private val KEY_DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        private val KEY_MOBILE_UI_POLISH = booleanPreferencesKey("mobile_ui_polish")
        private val KEY_PULL_TO_REFRESH = booleanPreferencesKey("pull_to_refresh")
        private val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        private val KEY_KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        private val KEY_AUTO_HIDE_CHROME = booleanPreferencesKey("auto_hide_chrome")
        private val KEY_BOOKMARKS = stringPreferencesKey("bookmarks_v1")
        private val KEY_DOWNLOADS = stringPreferencesKey("downloads_v1")
        private val KEY_QUICK_PROMPTS = stringPreferencesKey("quick_prompts_v1")

        val DEFAULT_BOOKMARKS = listOf(
            BookmarkItem(
                id = "home",
                title = "Antigravity Home",
                url = UrlRouter.START_URL,
                createdAtMillis = 0L,
                pinned = true
            )
        )

        val DEFAULT_PROMPTS = listOf(
            QuickPrompt(
                id = "review",
                title = "Code Review",
                body = "Review my recent changes for bugs, edge cases, and maintainability improvements. Be concise and list findings by severity.",
                category = "Engineering"
            ),
            QuickPrompt(
                id = "debug",
                title = "Root-Cause Debug",
                body = "Diagnose the root cause of this issue, explain why it happens, and propose the minimal safe fix with tests.",
                category = "Engineering"
            ),
            QuickPrompt(
                id = "plan",
                title = "Architecture Plan",
                body = "Propose a step-by-step implementation plan with trade-offs before editing any code.",
                category = "Planning"
            ),
            QuickPrompt(
                id = "summarize",
                title = "Concise Summary",
                body = "Summarize what changed, what was verified, and any open next steps in bullet points.",
                category = "Summary"
            )
        )
    }
}
