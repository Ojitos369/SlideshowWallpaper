package io.github.doubi88.slideshowwallpaper.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yalantis.ucrop.UCrop
import io.github.doubi88.slideshowwallpaper.preferences.SharedPreferencesManager
import io.github.doubi88.slideshowwallpaper.ui.components.CropHelper
import io.github.doubi88.slideshowwallpaper.ui.components.FullscreenImageDialog
import io.github.doubi88.slideshowwallpaper.ui.components.LoadingOverlay
import io.github.doubi88.slideshowwallpaper.ui.components.MediaCard
import io.github.doubi88.slideshowwallpaper.ui.components.VideoPlayerDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
        sharedUris: List<Uri>? = null,
        viewModel: GalleryViewModel =
                viewModel(
                        factory =
                                GalleryViewModelFactory(
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
    var uriToCrop by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Delete confirmation state
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Sorting menu state
    var showSortMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Media picker launcher
    val pickMediaLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia(),
                    onResult = { uris ->
                        if (uris.isNotEmpty()) {
                            viewModel.addMediaItems(uris)
                        }
                    }
            )

    // Permission launcher
    val permissionLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allGranted = permissions.values.all { it }
                if (allGranted) {
                    Log.d("GalleryScreen", "Storage permissions granted")
                } else {
                    Log.w("GalleryScreen", "Storage permissions denied")
                    // TODO: Show explanation to user
                }
            }

    // Request permissions on first launch
    val permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

    LaunchedEffect(Unit) {
        // Check if permissions already granted
        val needsPermission =
                permissions.any {
                    context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                }

        if (needsPermission) {
            permissionLauncher.launch(permissions)
        }
    }

    // Handle shared media from intent - delegate to ViewModel
    LaunchedEffect(sharedUris) {
        if (!sharedUris.isNullOrEmpty()) {
            viewModel.processSharedMedia(sharedUris)
        }
    }
    val coroutineScope = rememberCoroutineScope()

    // Pending overwrite state for permission requests
    var pendingOverwrite by remember { mutableStateOf<Pair<Uri, Uri>?>(null) }

    // Intent sender launcher for RecoverableSecurityException
    val intentSenderLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    pendingOverwrite?.let { (sourceUri, targetUri) ->
                        coroutineScope.launch {
                            try {
                                // Retry overwrite
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(sourceUri)?.use { input
                                        ->
                                        context.contentResolver.openOutputStream(targetUri, "w")
                                                ?.use { output -> input.copyTo(output) }
                                    }
                                    // Delete temp file
                                    try {
                                        File(sourceUri.path!!).delete()
                                    } catch (e: Exception) {}
                                }

                                // Force update
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val values =
                                                ContentValues().apply {
                                                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                                                }
                                        context.contentResolver.update(
                                                targetUri,
                                                values,
                                                null,
                                                null
                                        )
                                        values.clear()
                                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                        context.contentResolver.update(
                                                targetUri,
                                                values,
                                                null,
                                                null
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("GalleryScreen", "Failed to update metadata", e)
                                }

                                delay(500)
                                viewModel.loadMediaItems()
                                Log.d(
                                        "GalleryScreen",
                                        "Overwrite successful after permission grant"
                                )
                            } catch (e: Exception) {
                                Log.e(
                                        "GalleryScreen",
                                        "Failed to overwrite after permission grant",
                                        e
                                )
                            }
                            pendingOverwrite = null
                        }
                    }
                } else {
                    pendingOverwrite = null
                }
            }

    // Launcher for MANAGE_EXTERNAL_STORAGE intent
    val manageStorageLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) {
                // Check if permission is granted after returning from settings
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (android.os.Environment.isExternalStorageManager()) {
                        Log.d("GalleryScreen", "MANAGE_EXTERNAL_STORAGE granted")
                    } else {
                        Log.d("GalleryScreen", "MANAGE_EXTERNAL_STORAGE denied")
                    }
                }
            }

    // Crop launcher
    val cropLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                Log.d("GalleryScreen", "Crop result code: ${result.resultCode}")
                if (result.resultCode == Activity.RESULT_OK) {
                    val resultUri = UCrop.getOutput(result.data!!)
                    Log.d("GalleryScreen", "Crop result URI: $resultUri, Target URI: $uriToCrop")

                    if (resultUri != null && uriToCrop != null) {
                        val targetUri = uriToCrop!!
                        coroutineScope.launch {
                            try {
                                Log.d("GalleryScreen", "Starting overwrite operation...")
                                // Overwrite original file
                                withContext(Dispatchers.IO) {
                                    try {
                                        val inputStream =
                                                context.contentResolver.openInputStream(resultUri)
                                        var outputStream: java.io.OutputStream? = null
                                        try {
                                            outputStream =
                                                    context.contentResolver.openOutputStream(
                                                            targetUri,
                                                            "w"
                                                    )
                                        } catch (e: Exception) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                                            e is
                                                                    android.app.RecoverableSecurityException
                                            ) {
                                                // If we have MANAGE_EXTERNAL_STORAGE, this
                                                // shouldn't happen, but just in case
                                                if (Build.VERSION.SDK_INT >=
                                                                Build.VERSION_CODES.R &&
                                                                !android.os.Environment
                                                                        .isExternalStorageManager()
                                                ) {
                                                    Log.d(
                                                            "GalleryScreen",
                                                            "Caught RecoverableSecurityException, requesting MANAGE_EXTERNAL_STORAGE"
                                                    )
                                                    val intent =
                                                            Intent(
                                                                    android.provider.Settings
                                                                            .ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                                            )
                                                    intent.data =
                                                            Uri.parse(
                                                                    "package:${context.packageName}"
                                                            )
                                                    manageStorageLauncher.launch(intent)
                                                    return@withContext
                                                }

                                                Log.d(
                                                        "GalleryScreen",
                                                        "Caught RecoverableSecurityException, requesting permission"
                                                )
                                                pendingOverwrite = resultUri to targetUri
                                                val intentSender =
                                                        e.userAction.actionIntent.intentSender
                                                val intent =
                                                        androidx.activity.result.IntentSenderRequest
                                                                .Builder(intentSender)
                                                                .build()
                                                intentSenderLauncher.launch(intent)
                                                return@withContext
                                            } else {
                                                Log.w(
                                                        "GalleryScreen",
                                                        "Failed to open output stream with 'w', trying 'wt'",
                                                        e
                                                )
                                                try {
                                                    outputStream =
                                                            context.contentResolver
                                                                    .openOutputStream(
                                                                            targetUri,
                                                                            "wt"
                                                                    )
                                                } catch (e2: Exception) {
                                                    Log.e(
                                                            "GalleryScreen",
                                                            "Failed to open output stream with 'wt'",
                                                            e2
                                                    )
                                                }
                                            }
                                        }

                                        if (inputStream != null && outputStream != null) {
                                            inputStream.use { input ->
                                                outputStream.use { output -> input.copyTo(output) }
                                            }
                                            Log.d("GalleryScreen", "File overwritten successfully")

                                            // Delete temp file
                                            try {
                                                File(resultUri.path!!).delete()
                                            } catch (e: Exception) {}

                                            // Force update
                                            withContext(Dispatchers.Main) {
                                                // Force MediaStore update for the original URI
                                                // Use MediaScannerConnection to force a re-scan of
                                                // the file
                                                val path =
                                                        io.github.doubi88.slideshowwallpaper.ui
                                                                .utils.MediaStoreHelper
                                                                .getRealPathFromUri(
                                                                        context,
                                                                        targetUri
                                                                )
                                                if (path != null) {
                                                    android.media.MediaScannerConnection.scanFile(
                                                            context,
                                                            arrayOf(path),
                                                            arrayOf(
                                                                    "image/jpeg"
                                                            ), // Assuming JPEG for now, ideally get
                                                            // from MIME type
                                                            null
                                                    )
                                                    Log.d(
                                                            "GalleryScreen",
                                                            "Triggered MediaScannerConnection for $path"
                                                    )
                                                } else {
                                                    Log.w(
                                                            "GalleryScreen",
                                                            "Could not get real path for $targetUri, fallback to reload"
                                                    )
                                                }

                                                // Clear Coil cache to force immediate update
                                                val imageLoader = coil.Coil.imageLoader(context)
                                                imageLoader.memoryCache?.remove(
                                                        coil.memory.MemoryCache.Key(
                                                                targetUri.toString()
                                                        )
                                                )
                                                imageLoader.diskCache?.remove(targetUri.toString())
                                                Log.d(
                                                        "GalleryScreen",
                                                        "Cleared Coil cache for $targetUri"
                                                )

                                                delay(1000) // Wait a bit longer for the scanner
                                                viewModel.loadMediaItems()
                                            }
                                        } else {
                                            Log.e(
                                                    "GalleryScreen",
                                                    "Failed to open streams. Input: $inputStream, Output: $outputStream"
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.e("GalleryScreen", "Error in crop flow", e)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("GalleryScreen", "Error in crop flow", e)
                            }
                        }
                    } else {
                        Log.e(
                                "GalleryScreen",
                                "Missing URIs. Result: $resultUri, Target: $uriToCrop"
                        )
                    }
                    uriToCrop = null
                } else if (result.resultCode == Activity.RESULT_CANCELED) {
                    Log.d("GalleryScreen", "Crop cancelled")
                    val resultUri = UCrop.getOutput(result.data ?: Intent())
                    if (resultUri != null) {
                        try {
                            File(resultUri.path!!).delete()
                        } catch (e: Exception) {}
                    }
                    uriToCrop = null
                } else {
                    Log.e("GalleryScreen", "Crop failed with result code: ${result.resultCode}")
                    uriToCrop = null
                }
            }
    // Loading overlay with progress
    LoadingOverlay(
            isVisible = uiState.isLoading,
            currentFileName = uiState.currentFileName,
            processedFiles = uiState.processedFiles,
            totalFiles = uiState.totalFiles,
            progress = uiState.loadingProgress
    )

    Scaffold(
            topBar = {
                // Filter chips
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
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

                        // Sorting and Selection
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sort Button
                            Box {
                                TextButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.List, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Sort")
                                }
                                DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                            text = { Text("Date (Newest)") },
                                            onClick = {
                                                viewModel.setSortOption(SortOption.DATE_DESC)
                                                showSortMenu = false
                                            }
                                    )
                                    DropdownMenuItem(
                                            text = { Text("Date (Oldest)") },
                                            onClick = {
                                                viewModel.setSortOption(SortOption.DATE_ASC)
                                                showSortMenu = false
                                            }
                                    )
                                    DropdownMenuItem(
                                            text = { Text("Name (A-Z)") },
                                            onClick = {
                                                viewModel.setSortOption(SortOption.NAME_ASC)
                                                showSortMenu = false
                                            }
                                    )
                                    DropdownMenuItem(
                                            text = { Text("Name (Z-A)") },
                                            onClick = {
                                                viewModel.setSortOption(SortOption.NAME_DESC)
                                                showSortMenu = false
                                            }
                                    )
                                }
                            }

                            // Select/Deselect Buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.selectAll() }) {
                                    Text("Select All")
                                }
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
                                onClick = { showDeleteDialog = true },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) { Icon(Icons.Default.Delete, contentDescription = "Delete selected") }
                    }

                    // Add FAB
                    FloatingActionButton(
                            onClick = {
                                pickMediaLauncher.launch(
                                        PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia
                                                        .ImageAndVideo
                                        )
                                )
                            }
                    ) { Icon(Icons.Default.Add, contentDescription = "Add media") }
                }
            }
    ) { paddingValues ->
        val filteredItems =
                remember(uiState.mediaItems, uiState.currentFilter) {
                    when (uiState.currentFilter) {
                        MediaFilter.IMAGES_ONLY -> uiState.mediaItems.filter { !it.isVideo }
                        MediaFilter.VIDEOS_ONLY -> uiState.mediaItems.filter { it.isVideo }
                        MediaFilter.ALL -> uiState.mediaItems
                    }
                }

        if (filteredItems.isEmpty()) { // Empty state
            Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
            ) {
                Text(
                        text =
                                when (uiState.currentFilter) {
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
            val prefs =
                    LocalContext.current.getSharedPreferences(
                            "${LocalContext.current.packageName}_preferences",
                            Activity.MODE_PRIVATE
                    )
            val columns = prefs.getInt("gallery_columns", 3)
            val thumbnailRatio = prefs.getString("thumbnail_ratio", "3:4") ?: "3:4"

            val columnSize =
                    when (columns) {
                        2 -> 180.dp
                        3 -> 120.dp
                        4 -> 90.dp
                        5 -> 72.dp
                        else -> 120.dp
                    }
            // Media grid
            LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = columnSize),
                    contentPadding =
                            PaddingValues(
                                    start = 8.dp,
                                    end = 8.dp,
                                    top =
                                            if (uiState.selectedItems.isNotEmpty()) {
                                                paddingValues.calculateTopPadding() + 68.dp
                                            } else {
                                                paddingValues.calculateTopPadding() + 8.dp
                                            },
                                    bottom = paddingValues.calculateBottomPadding() + 8.dp
                            ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                    modifier = Modifier.fillMaxSize()
            ) {
                items(items = filteredItems, key = { it.uri }) { mediaItem ->
                    MediaCard(
                            uri = mediaItem.uri,
                            isSelected = mediaItem.uri in uiState.selectedItems,
                            isVideo = mediaItem.isVideo,
                            thumbnailRatio = thumbnailRatio,
                            lastModified = mediaItem.lastModified,
                            onClick = {
                                // Tap = select/unselect
                                viewModel.toggleSelection(mediaItem.uri)
                            },
                            onLongClick = {
                                // Long press opens fullscreen
                                if (mediaItem.isVideo) {
                                    videoPlayerUri = mediaItem.uri
                                } else {
                                    fullscreenImageUri = mediaItem.uri
                                }
                            },
                            onCropClick =
                                    if (!mediaItem.isVideo) {
                                        {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                                            !android.os.Environment
                                                                    .isExternalStorageManager()
                                            ) {
                                                val intent =
                                                        Intent(
                                                                android.provider.Settings
                                                                        .ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                                        )
                                                intent.data =
                                                        Uri.parse("package:${context.packageName}")
                                                manageStorageLauncher.launch(intent)
                                            } else {
                                                uriToCrop = mediaItem.uri
                                                CropHelper.launchCrop(
                                                        context,
                                                        mediaItem.uri,
                                                        cropLauncher
                                                )
                                            }
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
                    modifier = Modifier.fillMaxWidth().padding(paddingValues).padding(top = 8.dp),
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
        FullscreenImageDialog(uri = uri, onDismiss = { fullscreenImageUri = null })
    }

    // Video player
    videoPlayerUri?.let { uri ->
        VideoPlayerDialog(uri = uri, onDismiss = { videoPlayerUri = null })
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Media") },
                text = {
                    Text("Are you sure you want to delete ${uiState.selectedItems.size} items?")
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                viewModel.removeSelectedItems()
                                showDeleteDialog = false
                            }
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
        )
    }
}
