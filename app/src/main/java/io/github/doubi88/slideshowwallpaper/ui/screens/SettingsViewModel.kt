package io.github.doubi88.slideshowwallpaper.ui.screens

import androidx.lifecycle.ViewModel
import io.github.doubi88.slideshowwallpaper.preferences.SharedPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val interval: Int = 10,
    val isMuted: Boolean = false,
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
        // Load actual values from SharedPreferences
        val interval = try {
            preferencesManager.secondsBetweenImages
        } catch (e: Exception) {
            10
        }
        
        val isMuted = preferencesManager.muteVideos
        val swipeToChange = preferencesManager.swipeToChange
        
        // For now, playbackOrder and displayMode don't have direct getters in Java code
        // They use resource-based values, so we'll use defaults
        // The wallpaper service uses getCurrentOrdering() and getTooWideImagesRule()
        
        val galleryColumns = preferencesManager.preferences.getInt("gallery_columns", 3)
        val thumbnailRatio = preferencesManager.preferences.getString("thumbnail_ratio", "3:4") ?: "3:4"
        
        _uiState.value = SettingsUiState(
            interval = interval,
            isMuted = isMuted,
            playbackOrder = "Sequential", // Would need to map from getCurrentOrdering()
            displayMode = "Fit", // Would need to map from getTooWideImagesRule()
            swipeToChange = swipeToChange,
            galleryColumns = galleryColumns,
            thumbnailRatio = thumbnailRatio
        )
    }
    
    fun setInterval(interval: Int) {
        _uiState.value = _uiState.value.copy(interval = interval)
        preferencesManager.secondsBetweenImages = interval
    }
    
    fun setMuted(muted: Boolean) {
        _uiState.value = _uiState.value.copy(isMuted = muted)
        // Note: SharedPreferencesManager doesn't have setMuteVideos()
        // Would need to add it or use direct SharedPreferences access
        preferencesManager.preferences.edit()
            .putBoolean("mute_videos", muted)
            .apply()
    }
    
    fun setPlaybackOrder(order: String) {
        _uiState.value = _uiState.value.copy(playbackOrder = order)
        // Map to the resource value used by SharedPreferencesManager
        // Sequential = "selection", Random = "random"
        val value = when (order) {
            "Random" -> "random"
            "Shuffle" -> "random" // Treating shuffle as random for now
            else -> "selection"
        }
        preferencesManager.preferences.edit()
            .putString("ordering", value)
            .apply()
    }
    
    fun setDisplayMode(mode: String) {
        _uiState.value = _uiState.value.copy(displayMode = mode)
        // Map to TooWideImagesRule values
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
