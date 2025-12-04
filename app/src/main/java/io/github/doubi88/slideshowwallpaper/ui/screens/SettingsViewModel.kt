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

class SettingsViewModel(private val preferencesManager: SharedPreferencesManager) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val prefs = preferencesManager.preferences

        // Read ordering from preferences (defaults to "selection")
        val orderingValue = prefs.getString("ordering", "selection") ?: "selection"
        val playbackOrderDisplay =
                when (orderingValue) {
                    "random" -> "Shuffle" // Map random to Shuffle for UI display
                    else -> "Sequential"
                }

        // Read display mode from preferences (uses too_wide_images_rule key)
        val displayModeValue = prefs.getString("too_wide_images_rule", "scale_down") ?: "scale_down"
        val displayModeDisplay =
                when (displayModeValue) {
                    "scroll_forward" -> "Scroll Forward"
                    "scroll_backward" -> "Scroll Backward"
                    "scale_up" -> "Fill"
                    else -> "Fit"
                }

        _uiState.value =
                _uiState.value.copy(
                        interval = preferencesManager.secondsBetweenImages,
                        muteVideos = preferencesManager.muteVideos,
                        playbackOrder = playbackOrderDisplay,
                        displayMode = displayModeDisplay,
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
        preferencesManager.preferences.edit().putBoolean("mute_videos", muted).apply()
    }

    fun setPlaybackOrder(order: String) {
        _uiState.value = _uiState.value.copy(playbackOrder = order)
        // Both "Random" and "Shuffle" map to "random" preference value
        val value =
                when (order) {
                    "Random", "Shuffle" -> "random"
                    else -> "selection"
                }
        preferencesManager.preferences.edit().putString("ordering", value).apply()
    }

    fun setDisplayMode(mode: String) {
        _uiState.value = _uiState.value.copy(displayMode = mode)
        val value =
                when (mode) {
                    "Scroll Forward" -> "scroll_forward"
                    "Scroll Backward" -> "scroll_backward"
                    "Fill" -> "scale_up"
                    else -> "scale_down" // Fit
                }
        preferencesManager.preferences.edit().putString("too_wide_images_rule", value).apply()
    }

    fun setSwipeToChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(swipeToChange = enabled)
        preferencesManager.preferences.edit().putBoolean("swipe", enabled).apply()
    }

    fun setGalleryColumns(columns: Int) {
        _uiState.value = _uiState.value.copy(galleryColumns = columns)
        preferencesManager.preferences.edit().putInt("gallery_columns", columns).apply()
    }

    fun setThumbnailRatio(ratio: String) {
        _uiState.value = _uiState.value.copy(thumbnailRatio = ratio)
        preferencesManager.preferences.edit().putString("thumbnail_ratio", ratio).apply()
    }
}
