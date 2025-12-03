package io.github.doubi88.slideshowwallpaper.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.doubi88.slideshowwallpaper.preferences.SharedPreferencesManager
import io.github.doubi88.slideshowwallpaper.ui.components.MediaCard
import io.github.doubi88.slideshowwallpaper.ui.components.FullscreenImageDialog
import io.github.doubi88.slideshowwallpaper.ui.components.VideoPlayerDialog
import io.github.doubi88.slideshowwallpaper.ui.components.CropHelper
import com.yalantis.ucrop.UCrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    sharedUris: List<Uri>? = null,
    viewModel: GalleryViewModel = viewModel(
        factory = GalleryViewModelFactory(
            LocalContext.current,
            SharedPreferencesManager(
                LocalContext.current.getSharedPreferences(
                    "${LocalContext.current.packageName}_preferences",
                    Activity.MODE_PRIVATE
                )
            )
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Media viewer state
    var fullscreenImageUri by remember { mutableStateOf<Uri?>(null) }
    var videoPlayerUri by remember { mutableStateOf<Uri?>(null) }
    
    // Crop state
    var uriToCrop by remember { mutableStateOf<Uri?>(null) }
    
    val context = LocalContext.current
    
    // Media picker launcher
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.addMediaItems(uris)
            }
        }
    )
    
    // Handle shared media from intent
    LaunchedEffect(sharedUris) {
        if (!sharedUris.isNullOrEmpty()) {
            viewModel.addMediaItems(sharedUris)
        }
    }
    
    // Crop launcher
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            if (resultUri != null && uriToCrop != null) {
                viewModel.replaceMedia(uriToCrop!!, resultUri)
                uriToCrop = null
            }
        }
    }
    
    Scaffold(
        topBar = {
            // Filter chips
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.currentFilter == MediaFilter.ALL,
                            onClick = { viewModel.setFilter(MediaFilter.ALL) },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = uiState.currentFilter == MediaFilter.IMAGES_ONLY,
                            onClick = { viewModel.setFilter(MediaFilter.IMAGES_ONLY) },
                            label = { Text("Images") }
                        )
                        FilterChip(
                            selected = uiState.currentFilter == MediaFilter.VIDEOS_ONLY,
                            onClick = { viewModel.setFilter(MediaFilter.VIDEOS_ONLY) },
                            label = { Text("Videos") }
                        )
                    }
                    
                    // Select All / Deselect All buttons (always visible)
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.selectedItems.isEmpty()) {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Text("Select All")
                            }
                        }
                        else {
                            TextButton(onClick = { viewModel.deselectAll() }) {
                                Text("Deselect All")
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Delete FAB (only show when items selected)
                if (uiState.selectedItems.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { viewModel.removeSelectedItems() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                    }
                }
                
                // Add FAB
                FloatingActionButton(
                    onClick = {
                        pickMediaLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add media")
                }
            }
        }
    ) { paddingValues ->
        val filteredItems = remember(uiState.mediaItems, uiState.currentFilter) {
            when (uiState.currentFilter) {
                MediaFilter.IMAGES_ONLY -> uiState.mediaItems.filter { !it.isVideo }
                MediaFilter.VIDEOS_ONLY -> uiState.mediaItems.filter { it.isVideo }
                MediaFilter.ALL -> uiState.mediaItems
            }
        }
        
        if (filteredItems.isEmpty()) { // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (uiState.currentFilter) {
                        MediaFilter.IMAGES_ONLY -> "No images found"
                        MediaFilter.VIDEOS_ONLY -> "No videos found"
                        MediaFilter.ALL -> "No media. Tap + to add images or videos"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Gallery grid - read settings
            val prefs = LocalContext.current.getSharedPreferences(
                "${LocalContext.current.packageName}_preferences",
                Activity.MODE_PRIVATE
            )
            val columns = prefs.getInt("gallery_columns", 3)
            val thumbnailRatio = prefs.getString("thumbnail_ratio", "3:4") ?: "3:4"
            
            val columnSize = when (columns) {
                2 -> 180.dp
                3 -> 120.dp
                4 -> 90.dp
                5 -> 72.dp
                else -> 120.dp
            }
            
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(minSize = columnSize),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                    modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filteredItems,
                    key = { it.uri }
                ) { mediaItem ->
                        MediaCard(
                            uri = mediaItem.uri,
                            isSelected = mediaItem.uri in uiState.selectedItems,
                            isVideo = mediaItem.isVideo,
                            onClick = {
                                // Tap = select/unselect
                                viewModel.toggleSelection(mediaItem.uri)
                            },
                            onLongClick = {
                                // No longer needed - handled by MediaCard
                            },
                            onFullscreenClick = {
                                // Long press opens fullscreen
                                if (mediaItem.isVideo) {
                                    videoPlayerUri = mediaItem.uri
                                } else {
                                    fullscreenImageUri = mediaItem.uri
                                }
                            },
                            onCropClick = if (!mediaItem.isVideo) {
                                {
                                    uriToCrop = mediaItem.uri
                                    CropHelper.launchCrop(context, mediaItem.uri, cropLauncher)
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                }
            }
        }
        
        // Selection counter
        if (uiState.selectedItems.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 6.dp
            ) {
                Text(
                    text = "${uiState.selectedItems.size} selected",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
    
    // Fullscreen image viewer
    fullscreenImageUri?.let { uri ->
        FullscreenImageDialog(
            uri = uri,
            onDismiss = { fullscreenImageUri = null }
        )
    }
    
    // Video player
    videoPlayerUri?.let { uri ->
        VideoPlayerDialog(
            uri = uri,
            onDismiss = { videoPlayerUri = null }
        )
    }
}
