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

enum class AppDestination(val label: String) {
    WORKSPACE("Workspace"),
    SAVED("Saved"),
    DOWNLOADS("Downloads"),
    SETTINGS("Settings")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = true,
    val textZoomPercent: Int = 100,
    val desktopMode: Boolean = false,
    val mobileUiPolish: Boolean = true,
    val pullToRefreshEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val keepScreenAwake: Boolean = false,
    val autoHideChrome: Boolean = false
)

data class BookmarkItem(
    val id: String,
    val title: String,
    val url: String,
    val createdAtMillis: Long,
    val pinned: Boolean = false
)

data class DownloadRecord(
    val id: Long,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val downloadedAtMillis: Long
)

data class QuickPrompt(
    val id: String,
    val title: String,
    val body: String,
    val category: String
)
