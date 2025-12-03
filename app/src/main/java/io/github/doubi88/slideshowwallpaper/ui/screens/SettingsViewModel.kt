package io.github.doubi88.slideshowwallpaper.ui.screens

import androidx.lifecycle.ViewModel
import io.github.doubi88.slideshowwallpaper.preferences.SharedPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val interval: Int = 5,
    val muteVideos: Boolean = false,
    val playbackOrder: String = "Sequential",
    val displayMode: String = "Fit",
    val swipeToChange: Boolean = false,
    val galleryColumns: Int = 3,
    val thumbnailRatio: String = "3:4"
)

class SettingsViewModel(
    private val preferencesManager: SharedPreferencesManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        val prefs = preferencesManager.preferences
        _uiState.value = _uiState.value.copy(
            interval = preferencesManager.secondsBetweenImages,
            muteVideos = preferencesManager.muteVideos,
            playbackOrder = "Sequential", // Simplified for now
            displayMode = "Fit", // Simplified for now
            swipeToChange = preferencesManager.swipeToChange,
            galleryColumns = prefs.getInt("gallery_columns", 3),
            thumbnailRatio = prefs.getString("thumbnail_ratio", "3:4") ?: "3:4"
        )
    }
    
    fun setInterval(interval: Int) {
        _uiState.value = _uiState.value.copy(interval = interval)
        preferencesManager.secondsBetweenImages = interval
    }
    
    fun setMuteVideos(muted: Boolean) {
        _uiState.value = _uiState.value.copy(muteVideos = muted)
        preferencesManager.preferences.edit()
            .putBoolean("mute_videos", muted)
            .apply()
    }
    
    fun setPlaybackOrder(order: String) {
        _uiState.value = _uiState.value.copy(playbackOrder = order)
        val value = when (order) {
            "Random" -> "random"
            else -> "selection"
        }
        preferencesManager.preferences.edit()
            .putString("ordering", value)
            .apply()
    }
    
    fun setDisplayMode(mode: String) {
        _uiState.value = _uiState.value.copy(displayMode = mode)
        // This would need proper mapping to resource values
        // For now, just updating state
    }
    
    fun setSwipeToChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(swipeToChange = enabled)
        preferencesManager.preferences.edit()
            .putBoolean("swipe", enabled)
            .apply()
    }
    
    fun setGalleryColumns(columns: Int) {
        _uiState.value = _uiState.value.copy(galleryColumns = columns)
        preferencesManager.preferences.edit()
            .putInt("gallery_columns", columns)
            .apply()
    }
    
    fun setThumbnailRatio(ratio: String) {
        _uiState.value = _uiState.value.copy(thumbnailRatio = ratio)
        preferencesManager.preferences.edit()
            .putString("thumbnail_ratio", ratio)
            .apply()
    }
}
