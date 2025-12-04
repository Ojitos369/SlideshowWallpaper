package io.github.doubi88.slideshowwallpaper.ui.screens

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.doubi88.slideshowwallpaper.preferences.SharedPreferencesManager
import io.github.doubi88.slideshowwallpaper.ui.utils.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

data class MediaItem(
        val uri: Uri,
        val isVideo: Boolean = false,
        val name: String = "",
        val lastModified: Long = 0
)

enum class SortOption {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC
}

enum class MediaFilter {
    ALL,
    IMAGES_ONLY,
    VIDEOS_ONLY
}

data class GalleryUiState(
        val mediaItems: List<MediaItem> = emptyList(),
        val selectedItems: Set<Uri> = emptySet(),
        val currentFilter: MediaFilter = MediaFilter.ALL,
        val isLoading: Boolean = false,
        val sortOption: SortOption = SortOption.DATE_DESC,
        // Enhanced loading progress tracking
        val loadingProgress: Float = 0f,
        val loadingMessage: String = "",
        val currentFileName: String = "",
        val totalFiles: Int = 0,
        val processedFiles: Int = 0
)

class GalleryViewModel(
        private val context: Context,
        private val preferencesManager: SharedPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    // Track processed shared URIs to prevent re-adding on navigation
    private var processedSharedUris: Set<Uri> = emptySet()

    // ContentObserver for external changes to LumaLoop album
    private val albumContentObserver =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    Log.d("GalleryViewModel", "MediaStore changed, refreshing gallery")
                    syncWithAlbum()
                }
            }

    init {
        // Register ContentObserver for MediaStore changes
        context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                albumContentObserver
        )
        context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                albumContentObserver
        )

        loadMediaItems()
        // Migrate from private storage to public album if needed
        migrateToPublicAlbum()
        // Clean up duplicates and invalid URIs
        cleanupDuplicates()
    }

    override fun onCleared() {
        super.onCleared()
        context.contentResolver.unregisterContentObserver(albumContentObserver)
    }

    fun loadMediaItems() {
        viewModelScope.launch {
            val uris =
                    preferencesManager.getImageUris(
                            io.github.doubi88.slideshowwallpaper.preferences
                                    .SharedPreferencesManager.Ordering.SELECTION
                    )

            // Validate URIs - remove if file doesn't exist
            val validUris =
                    uris.filter { uri ->
                        try {
                            context.contentResolver.openInputStream(uri)?.use { true } ?: false
                        } catch (e: Exception) {
                            Log.w("GalleryViewModel", "Invalid URI, removing: $uri")
                            preferencesManager.removeUri(uri)
                            false
                        }
                    }

            val mediaItems =
                    validUris.map { uri ->
                        var name = "Unknown"
                        var lastModified = 0L
                        try {
                            context.contentResolver.query(
                                            uri,
                                            arrayOf(
                                                    MediaStore.MediaColumns.DISPLAY_NAME,
                                                    MediaStore.MediaColumns.DATE_MODIFIED
                                            ),
                                            null,
                                            null,
                                            null
                                    )
                                    ?.use { cursor ->
                                        if (cursor.moveToFirst()) {
                                            val nameIndex =
                                                    cursor.getColumnIndex(
                                                            MediaStore.MediaColumns.DISPLAY_NAME
                                                    )
                                            val dateIndex =
                                                    cursor.getColumnIndex(
                                                            MediaStore.MediaColumns.DATE_MODIFIED
                                                    )
                                            if (nameIndex != -1)
                                                    name = cursor.getString(nameIndex) ?: "Unknown"
                                            if (dateIndex != -1)
                                                    lastModified = cursor.getLong(dateIndex)
                                        }
                                    }
                        } catch (e: Exception) {
                            Log.e("GalleryViewModel", "Error getting file info for $uri", e)
                        }

                        MediaItem(
                                uri = uri,
                                isVideo = isVideoUri(uri),
                                name = name,
                                lastModified = lastModified
                        )
                    }

            val sortedItems = sortMediaItems(mediaItems, _uiState.value.sortOption)

            _uiState.value =
                    _uiState.value.copy(mediaItems = sortedItems, selectedItems = emptySet())
        }
    }

    fun setSortOption(option: SortOption) {
        val sortedItems = sortMediaItems(_uiState.value.mediaItems, option)
        _uiState.value = _uiState.value.copy(sortOption = option, mediaItems = sortedItems)
    }

    private fun sortMediaItems(items: List<MediaItem>, option: SortOption): List<MediaItem> {
        return when (option) {
            SortOption.DATE_DESC -> items.sortedByDescending { it.lastModified }
            SortOption.DATE_ASC -> items.sortedBy { it.lastModified }
            SortOption.NAME_ASC -> items.sortedBy { it.name }
            SortOption.NAME_DESC -> items.sortedByDescending { it.name }
        }
    }

    fun toggleSelection(uri: Uri) {
        val currentSelected = _uiState.value.selectedItems
        _uiState.value =
                _uiState.value.copy(
                        selectedItems =
                                if (uri in currentSelected) {
                                    currentSelected - uri
                                } else {
                                    currentSelected + uri
                                }
                )
    }

    fun removeItemByUri(uri: Uri) {
        viewModelScope.launch {
            preferencesManager.removeUri(uri)
            loadMediaItems()
        }
    }

    fun selectAll() {
        viewModelScope.launch {
            _uiState.value =
                    _uiState.value.copy(
                            selectedItems = _uiState.value.mediaItems.map { it.uri }.toSet()
                    )
        }
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedItems = emptySet())
    }

    fun setFilter(filter: MediaFilter) {
        _uiState.value =
                _uiState.value.copy(
                        currentFilter = filter,
                        selectedItems = emptySet() // Clear selection when filter changes
                )
    }

    fun addMediaItems(uris: List<Uri>) {
        viewModelScope.launch {
            val totalCount = uris.size
            var processedCount = 0

            // Initialize loading state with progress info
            _uiState.value =
                    _uiState.value.copy(
                            isLoading = true,
                            totalFiles = totalCount,
                            processedFiles = 0,
                            loadingProgress = 0f,
                            currentFileName = ""
                    )

            try {
                // Get current URIs to prevent duplicates
                val currentUris =
                        preferencesManager
                                .getImageUris(
                                        io.github.doubi88.slideshowwallpaper.preferences
                                                .SharedPreferencesManager.Ordering.SELECTION
                                )
                                .toSet()

                // Filter out duplicates first
                val urisToProcess = uris.filterNot { it in currentUris }
                val actualTotal = urisToProcess.size

                if (actualTotal == 0) {
                    Log.d("GalleryViewModel", "All URIs already exist, skipping")
                    return@launch
                }

                // Update total with actual count (excluding duplicates)
                _uiState.value = _uiState.value.copy(totalFiles = actualTotal)

                // Use Semaphore for parallel processing with max 3 concurrent
                val semaphore = Semaphore(3)
                val addedUris = java.util.concurrent.ConcurrentLinkedQueue<Uri>()

                // Process files in parallel using coroutineScope
                kotlinx.coroutines.coroutineScope {
                    val jobs =
                            urisToProcess.mapIndexed { index, originalUri ->
                                async(Dispatchers.IO) {
                                    semaphore.acquire()
                                    try {
                                        // Get file name for display
                                        var originalName: String? = null
                                        var displayName = "archivo ${index + 1}"
                                        try {
                                            context.contentResolver.query(
                                                            originalUri,
                                                            arrayOf(
                                                                    MediaStore.MediaColumns
                                                                            .DISPLAY_NAME
                                                            ),
                                                            null,
                                                            null,
                                                            null
                                                    )
                                                    ?.use { cursor ->
                                                        if (cursor.moveToFirst()) {
                                                            val nameIndex =
                                                                    cursor.getColumnIndex(
                                                                            MediaStore.MediaColumns
                                                                                    .DISPLAY_NAME
                                                                    )
                                                            if (nameIndex != -1) {
                                                                val fullName =
                                                                        cursor.getString(nameIndex)
                                                                displayName =
                                                                        fullName ?: displayName
                                                                originalName =
                                                                        fullName?.substringBeforeLast(
                                                                                "."
                                                                        )
                                                            }
                                                        }
                                                    }
                                        } catch (e: Exception) {
                                            Log.w(
                                                    "GalleryViewModel",
                                                    "Could not get original name",
                                                    e
                                            )
                                        }

                                        // Update UI with current file
                                        withContext(Dispatchers.Main) {
                                            _uiState.value =
                                                    _uiState.value.copy(
                                                            currentFileName = displayName
                                                    )
                                        }

                                        // Copy to public MediaStore album
                                        val albumUri =
                                                MediaStoreHelper.copyToPublicAlbum(
                                                        context,
                                                        originalUri,
                                                        originalName
                                                )
                                        val uriToSave = albumUri ?: originalUri

                                        if (uriToSave !in currentUris) {
                                            addedUris.add(uriToSave)
                                        }

                                        if (albumUri == null) {
                                            Log.w(
                                                    "GalleryViewModel",
                                                    "Failed to copy to album, using original: $originalUri"
                                            )
                                        } else {
                                            Log.d("GalleryViewModel", "Added URI: $uriToSave")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                                "GalleryViewModel",
                                                "Error adding media: $originalUri",
                                                e
                                        )
                                        if (originalUri !in currentUris) {
                                            addedUris.add(originalUri)
                                        }
                                        Unit
                                    } finally {
                                        // Update progress on main thread
                                        withContext(Dispatchers.Main) {
                                            processedCount++
                                            val progress = processedCount.toFloat() / actualTotal
                                            _uiState.value =
                                                    _uiState.value.copy(
                                                            processedFiles = processedCount,
                                                            loadingProgress = progress
                                                    )
                                        }
                                        semaphore.release()
                                    }
                                }
                            }
                    // Wait for all to complete
                    jobs.forEach { it.await() }
                }

                // Add all URIs to preferences
                addedUris.forEach { preferencesManager.addUri(it) }

                loadMediaItems()
            } finally {
                _uiState.value =
                        _uiState.value.copy(
                                isLoading = false,
                                totalFiles = 0,
                                processedFiles = 0,
                                loadingProgress = 0f,
                                currentFileName = ""
                        )
            }
        }
    }

    // Process shared media with duplication prevention
    fun processSharedMedia(sharedUris: List<Uri>) {
        val newUris = sharedUris.filterNot { it in processedSharedUris }
        if (newUris.isNotEmpty()) {
            processedSharedUris = processedSharedUris + newUris.toSet()
            Log.d("GalleryViewModel", "Processing ${newUris.size} new shared URIs")
            addMediaItems(newUris)
        } else {
            Log.d("GalleryViewModel", "All ${sharedUris.size} URIs already processed, skipping")
        }
    }

    private fun migrateToPublicAlbum() {
        viewModelScope.launch {
            try {
                val privateDir = java.io.File(context.filesDir, "slideshow_media")
                if (!privateDir.exists() || privateDir.listFiles()?.isEmpty() == true) {
                    Log.d("GalleryViewModel", "No private files to migrate")
                    return@launch
                }

                Log.d("GalleryViewModel", "Starting migration from private storage...")
                val migratedUris = mutableListOf<Uri>()

                privateDir.listFiles()?.forEach { file ->
                    try {
                        val fileUri = Uri.fromFile(file)
                        val albumUri = MediaStoreHelper.copyToPublicAlbum(context, fileUri)

                        if (albumUri != null) {
                            migratedUris.add(albumUri)
                            file.delete() // Remove from private storage
                            Log.d("GalleryViewModel", "Migrated: ${file.name}")
                        }
                    } catch (e: Exception) {
                        Log.e("GalleryViewModel", "Failed to migrate: ${file.name}", e)
                    }
                }

                // Update SharedPreferences with new URIs
                if (migratedUris.isNotEmpty()) {
                    // Remove old private file:// URIs
                    val oldUris =
                            preferencesManager.getImageUris(
                                    io.github.doubi88.slideshowwallpaper.preferences
                                            .SharedPreferencesManager.Ordering.SELECTION
                            )
                    oldUris.forEach { preferencesManager.removeUri(it) }

                    // Add new album URIs
                    migratedUris.forEach { preferencesManager.addUri(it) }
                    loadMediaItems()
                    Log.d("GalleryViewModel", "Migration complete: ${migratedUris.size} files")
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Migration error", e)
            }
        }
    }

    private fun syncWithAlbum() {
        viewModelScope.launch {
            try {
                // Get all files currently in LumaLoop album
                val albumUris = MediaStoreHelper.getAlbumContent(context)
                val savedUris =
                        preferencesManager.getImageUris(
                                io.github.doubi88.slideshowwallpaper.preferences
                                        .SharedPreferencesManager.Ordering.SELECTION
                        )

                // Remove URIs that are no longer in album
                savedUris.forEach { uri ->
                    if (uri !in albumUris && uri.toString().contains("LumaLoop")) {
                        Log.d("GalleryViewModel", "File deleted externally, removing: $uri")
                        preferencesManager.removeUri(uri)
                    }
                }

                // Optionally: auto-add new files found in album
                // (Commented out - user can manually add if needed)
                // albumUris.forEach { uri ->
                //     if (uri !in savedUris) {
                //         preferencesManager.addUri(uri)
                //     }
                // }

                loadMediaItems()
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Sync error", e)
            }
        }
    }

    private fun cleanupDuplicates() {
        viewModelScope.launch {
            try {
                val savedUris =
                        preferencesManager
                                .getImageUris(
                                        io.github.doubi88.slideshowwallpaper.preferences
                                                .SharedPreferencesManager.Ordering.SELECTION
                                )
                                .toSet() // Convert to Set to find duplicates

                val originalSize =
                        preferencesManager.getImageUris(
                                        io.github.doubi88.slideshowwallpaper.preferences
                                                .SharedPreferencesManager.Ordering.SELECTION
                                )
                                .size

                if (savedUris.size < originalSize) {
                    Log.d("GalleryViewModel", "Found duplicates, cleaning up...")
                    // Remove all and re-add unique ones
                    val allUris =
                            preferencesManager.getImageUris(
                                    io.github.doubi88.slideshowwallpaper.preferences
                                            .SharedPreferencesManager.Ordering.SELECTION
                            )
                    allUris.forEach { preferencesManager.removeUri(it) }
                    savedUris.forEach { preferencesManager.addUri(it) }
                    loadMediaItems()
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Cleanup error", e)
            }
        }
    }

    fun removeSelectedItems() {
        viewModelScope.launch {
            _uiState.value.selectedItems.forEach { uri ->
                try {
                    // Delete physical file from MediaStore/LumaLoop
                    context.contentResolver.delete(uri, null, null)
                    Log.d("GalleryViewModel", "Deleted file from storage: $uri")
                } catch (e: Exception) {
                    Log.e("GalleryViewModel", "Failed to delete file: $uri", e)
                }

                // Remove from preferences
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

    fun refreshMediaItem(uri: Uri) {
        viewModelScope.launch {
            val currentItems = _uiState.value.mediaItems.toMutableList()
            val index = currentItems.indexOfFirst { it.uri == uri }

            if (index != -1) {
                // Refresh specific item
                var name = currentItems[index].name
                var lastModified =
                        System.currentTimeMillis() // Assume modified now if we can't get it

                try {
                    context.contentResolver.query(
                                    uri,
                                    arrayOf(
                                            MediaStore.MediaColumns.DISPLAY_NAME,
                                            MediaStore.MediaColumns.DATE_MODIFIED
                                    ),
                                    null,
                                    null,
                                    null
                            )
                            ?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIndex =
                                            cursor.getColumnIndex(
                                                    MediaStore.MediaColumns.DISPLAY_NAME
                                            )
                                    val dateIndex =
                                            cursor.getColumnIndex(
                                                    MediaStore.MediaColumns.DATE_MODIFIED
                                            )
                                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                                    if (dateIndex != -1) lastModified = cursor.getLong(dateIndex)
                                }
                            }
                } catch (e: Exception) {
                    Log.e("GalleryViewModel", "Error refreshing file info for $uri", e)
                }

                val updatedItem = currentItems[index].copy(name = name, lastModified = lastModified)
                currentItems[index] = updatedItem

                // Re-sort if necessary (optional, but good for consistency)
                val sortedItems = sortMediaItems(currentItems, _uiState.value.sortOption)

                _uiState.value = _uiState.value.copy(mediaItems = sortedItems)
                Log.d("GalleryViewModel", "Refreshed single item: $uri")
            } else {
                Log.w("GalleryViewModel", "Item to refresh not found: $uri, reloading all")
                loadMediaItems()
            }
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
            @Suppress("UNCHECKED_CAST") return GalleryViewModel(context, preferencesManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
