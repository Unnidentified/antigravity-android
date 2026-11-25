package com.antigravity.wrapper.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.wrapper.data.AppDestination
import com.antigravity.wrapper.data.AppRepository
import com.antigravity.wrapper.data.AppSettings
import com.antigravity.wrapper.data.BookmarkItem
import com.antigravity.wrapper.data.DownloadRecord
import com.antigravity.wrapper.data.QuickPrompt
import com.antigravity.wrapper.data.ThemeMode
import com.antigravity.wrapper.web.UrlRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebErrorState(
    val failedUrl: String,
    val errorCode: Int,
    val description: String?,
    val isOffline: Boolean
)

data class WorkspaceUiState(
    val activeDestination: AppDestination = AppDestination.WORKSPACE,
    val currentUrl: String = UrlRouter.START_URL,
    val pageTitle: String = "Antigravity",
    val loadingProgress: Int = 0,
    val isLoading: Boolean = true,
    val hasCompletedInitialLoad: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val error: WebErrorState? = null,
    val isImmersiveMode: Boolean = false,
    val isFindBarOpen: Boolean = false,
    val findQuery: String = "",
    val findActiveMatch: Int = 0,
    val findTotalMatches: Int = 0,
    val isQuickActionsOpen: Boolean = false,
    val isFullscreenWebView: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AppSettings()
    )

    val bookmarks: StateFlow<List<BookmarkItem>> = repository.bookmarks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AppRepository.DEFAULT_BOOKMARKS
    )

    val downloads: StateFlow<List<DownloadRecord>> = repository.downloads.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val quickPrompts: StateFlow<List<QuickPrompt>> = repository.quickPrompts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AppRepository.DEFAULT_PROMPTS
    )

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    fun selectDestination(destination: AppDestination) {
        _uiState.update {
            it.copy(
                activeDestination = destination,
                isFindBarOpen = if (destination != AppDestination.WORKSPACE) false else it.isFindBarOpen
            )
        }
    }

    fun onPageStarted(url: String?) {
        _uiState.update {
            it.copy(
                currentUrl = url ?: it.currentUrl,
                isLoading = true,
                loadingProgress = it.loadingProgress.coerceAtLeast(10),
                error = null
            )
        }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update {
            val done = progress >= 100
            it.copy(
                loadingProgress = progress,
                isLoading = !done,
                hasCompletedInitialLoad = it.hasCompletedInitialLoad || progress >= 75
            )
        }
    }

    fun onPageFinished(url: String?, hadMainFrameError: Boolean) {
        _uiState.update {
            it.copy(
                currentUrl = url?.takeIf { u -> !u.startsWith("chrome-error://") } ?: it.currentUrl,
                isLoading = false,
                loadingProgress = 100,
                hasCompletedInitialLoad = true,
                error = if (hadMainFrameError) it.error else null
            )
        }
    }

    fun onNavigationStateChanged(url: String?, canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update {
            it.copy(
                currentUrl = url?.takeIf { u -> !u.startsWith("chrome-error://") } ?: it.currentUrl,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun onTitleChanged(title: String?) {
        val cleaned = title?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("http://") && !it.startsWith("https://") }
            ?: "Antigravity"
        _uiState.update { it.copy(pageTitle = cleaned) }
    }

    fun onMainFrameError(failedUrl: String?, errorCode: Int, description: String?, isOffline: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = false,
                hasCompletedInitialLoad = true,
                error = WebErrorState(
                    failedUrl = failedUrl ?: it.currentUrl,
                    errorCode = errorCode,
                    description = description,
                    isOffline = isOffline
                )
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setImmersiveMode(enabled: Boolean) {
        _uiState.update { it.copy(isImmersiveMode = enabled) }
    }

    fun setQuickActionsOpen(open: Boolean) {
        _uiState.update { it.copy(isQuickActionsOpen = open) }
    }

    fun setFindBarOpen(open: Boolean) {
        _uiState.update {
            it.copy(
                isFindBarOpen = open,
                findQuery = if (!open) "" else it.findQuery,
                findActiveMatch = if (!open) 0 else it.findActiveMatch,
                findTotalMatches = if (!open) 0 else it.findTotalMatches
            )
        }
    }

    fun updateFindQuery(query: String) {
        _uiState.update { it.copy(findQuery = query) }
    }

    fun updateFindMatches(activeOrdinal: Int, numberOfMatches: Int) {
        _uiState.update {
            it.copy(
                findActiveMatch = if (numberOfMatches > 0) activeOrdinal + 1 else 0,
                findTotalMatches = numberOfMatches
            )
        }
    }

    fun setFullscreenWebView(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreenWebView = fullscreen) }
    }

    // Settings mutations
    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.updateThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }
    fun setTextZoom(percent: Int) = viewModelScope.launch { repository.setTextZoom(percent) }
    fun setDesktopMode(enabled: Boolean) = viewModelScope.launch { repository.setDesktopMode(enabled) }
    fun setMobileUiPolish(enabled: Boolean) = viewModelScope.launch { repository.setMobileUiPolish(enabled) }
    fun setPullToRefresh(enabled: Boolean) = viewModelScope.launch { repository.setPullToRefresh(enabled) }
    fun setHapticFeedback(enabled: Boolean) = viewModelScope.launch { repository.setHapticFeedback(enabled) }
    fun setKeepScreenAwake(enabled: Boolean) = viewModelScope.launch { repository.setKeepScreenAwake(enabled) }
    fun setAutoHideChrome(enabled: Boolean) = viewModelScope.launch { repository.setAutoHideChrome(enabled) }

    // Bookmark mutations
    fun toggleCurrentBookmark(onResult: (added: Boolean) -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            val added = repository.toggleBookmark(state.pageTitle, state.currentUrl)
            onResult(added)
        }
    }

    fun addBookmark(title: String, url: String, pinned: Boolean = false) = viewModelScope.launch {
        repository.addBookmark(title, url, pinned)
    }

    fun renameBookmark(id: String, newTitle: String) = viewModelScope.launch {
        repository.renameBookmark(id, newTitle)
    }

    fun togglePinBookmark(id: String) = viewModelScope.launch {
        repository.togglePinBookmark(id)
    }

    fun removeBookmark(id: String) = viewModelScope.launch {
        repository.removeBookmark(id)
    }

    // Download mutations
    fun recordDownload(id: Long, fileName: String, url: String, mimeType: String) = viewModelScope.launch {
        repository.recordDownload(id, fileName, url, mimeType)
    }

    fun removeDownload(id: Long) = viewModelScope.launch {
        repository.removeDownload(id)
    }

    fun clearDownloads() = viewModelScope.launch {
        repository.clearDownloads()
    }

    // Quick prompt mutations
    fun addQuickPrompt(title: String, body: String, category: String) = viewModelScope.launch {
        repository.addQuickPrompt(title, body, category)
    }

    fun removeQuickPrompt(id: String) = viewModelScope.launch {
        repository.removeQuickPrompt(id)
    }
}
