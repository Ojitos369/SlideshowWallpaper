package io.github.doubi88.slideshowwallpaper.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.doubi88.slideshowwallpaper.preferences.SharedPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaItem(
    val uri: Uri,
    val isVideo: Boolean = false
)

enum class MediaFilter {
    ALL, IMAGES_ONLY, VIDEOS_ONLY
}

data class GalleryUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val selectedItems: Set<Uri> = emptySet(),
    val currentFilter: MediaFilter = MediaFilter.ALL,
    val isLoading: Boolean = false
)

class GalleryViewModel(
    private val context: Context,
    private val preferencesManager: SharedPreferencesManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()
    
    init {
        loadMediaItems()
    }
    
    fun loadMediaItems() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val uris = preferencesManager.getImageUris(SharedPreferencesManager.Ordering.SELECTION)
            val mediaItems = uris.map { uri ->
                MediaItem(uri = uri, isVideo = isVideoUri(uri))
            }
            _uiState.value = _uiState.value.copy(
                mediaItems = mediaItems,
                isLoading = false
            )
        }
    }
    
    fun toggleSelection(uri: Uri) {
        val currentSelected = _uiState.value.selectedItems
        _uiState.value = _uiState.value.copy(
            selectedItems = if (uri in currentSelected) {
                currentSelected - uri
            } else {
                currentSelected + uri
            }
        )
    }
    
    fun selectAll() {
        val visibleItems = getFilteredMediaItems()
        _uiState.value = _uiState.value.copy(
            selectedItems = visibleItems.map { it.uri }.toSet()
        )
    }
    
    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedItems = emptySet())
    }
    
    fun setFilter(filter: MediaFilter) {
        _uiState.value = _uiState.value.copy(
            currentFilter = filter,
            selectedItems = emptySet() // Clear selection when filter changes
        )
    }
    
    fun addMediaItems(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { originalUri ->
                // Copy to private storage first
                val privateCopy = io.github.doubi88.slideshowwallpaper.ui.utils.FileHelper.copyToPrivateStorage(context, originalUri)
                if (privateCopy != null) {
                    preferencesManager.addUri(privateCopy)
                } else {
                    Log.e("GalleryViewModel", "Failed to copy file: $originalUri")
                }
            }
            loadMediaItems()
        }
    }
    
    fun removeSelectedItems() {
        viewModelScope.launch {
            _uiState.value.selectedItems.forEach { uri ->
                preferencesManager.removeUri(uri)
            }
            loadMediaItems()
            deselectAll()
        }
    }
    
    fun replaceMedia(oldUri: Uri, newUri: Uri) {
        viewModelScope.launch {
            preferencesManager.removeUri(oldUri)
            preferencesManager.addUri(newUri)
            loadMediaItems()
            deselectAll()
        }
    }
    
    private fun getFilteredMediaItems(): List<MediaItem> {
        return when (_uiState.value.currentFilter) {
            MediaFilter.IMAGES_ONLY -> _uiState.value.mediaItems.filter { !it.isVideo }
            MediaFilter.VIDEOS_ONLY -> _uiState.value.mediaItems.filter { it.isVideo }
            MediaFilter.ALL -> _uiState.value.mediaItems
        }
    }
    
    private fun isVideoUri(uri: Uri): Boolean {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            mimeType?.startsWith("video/") == true
        } catch (e: Exception) {
            false
        }
    }
}

class GalleryViewModelFactory(
    private val context: Context,
    private val preferencesManager: SharedPreferencesManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GalleryViewModel(context, preferencesManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
