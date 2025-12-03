package io.github.doubi88.slideshowwallpaper.ui.screens

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.doubi88.slideshowwallpaper.preferences.SharedPreferencesManager
import io.github.doubi88.slideshowwallpaper.ui.components.IntervalBottomSheet
import io.github.doubi88.slideshowwallpaper.ui.components.PlaybackOrderBottomSheet
import io.github.doubi88.slideshowwallpaper.ui.components.DisplayModeBottomSheet
import io.github.doubi88.slideshowwallpaper.ui.components.ThumbnailRatioBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(
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
    
    var showIntervalSheet by remember { mutableStateOf(false) }
    var showPlaybackOrderSheet by remember { mutableStateOf(false) }
    var showDisplayModeSheet by remember { mutableStateOf(false) }
    var showThumbnailRatioSheet by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        
        Divider()
        
        // Interval Setting
        ListItem(
            headlineContent = { Text("Slideshow Interval") },
            supportingContent = { Text("${uiState.interval} seconds between images") },
            leadingContent = {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            },
            modifier = Modifier.clickable {
                showIntervalSheet = true
            }
        )
        
        Divider()
        
        // Mute Setting
        ListItem(
            headlineContent = { Text("Mute Videos") },
            supportingContent = { Text("Play videos without sound") },
            leadingContent = {
                Icon(
                    if (uiState.muteVideos) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Switch(
                    checked = uiState.muteVideos,
                    onCheckedChange = { viewModel.setMuteVideos(it) }
                )
            }
        )
        
        Divider()
        
        // Order Setting
        ListItem(
            headlineContent = { Text("Playback Order") },
            supportingContent = { Text(uiState.playbackOrder) },
            leadingContent = {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            },
            modifier = Modifier.clickable {
                showPlaybackOrderSheet = true
            }
        )
        
        Divider()
        
        // Display Mode Setting
        ListItem(
            headlineContent = { Text("Display Mode") },
            supportingContent = { Text(uiState.displayMode) },
            leadingContent = {
                Icon(
                    Icons.Default.AspectRatio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            },
            modifier = Modifier.clickable {
                showDisplayModeSheet = true
            }
        )
        
        
        HorizontalDivider()
        
        // Swipe to Change Setting
        ListItem(
            headlineContent = { Text("Swipe to Change") },
            supportingContent = { Text("Swipe screen to change wallpaper") },
            leadingContent = {
                Icon(
                    Icons.Default.Swipe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Switch(
                    checked = uiState.swipeToChange,
                    onCheckedChange = { viewModel.setSwipeToChange(it) }
                )
            }
        )
        
        HorizontalDivider()
        
        // Gallery Section Header
        Text(
            text = "Gallery",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        
        // Gallery Columns Setting
        ListItem(
            headlineContent = { Text("Grid Columns") },
            supportingContent = { Text("${uiState.galleryColumns} columns") },
            leadingContent = {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = null
                )
            }
        )
        Slider(
            value = uiState.galleryColumns.toFloat(),
            onValueChange = { viewModel.setGalleryColumns(it.toInt()) },
            valueRange = 2f..5f,
            steps = 3,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        HorizontalDivider()
        
        // Thumbnail Ratio Setting
        ListItem(
            headlineContent = { Text("Thumbnail Aspect Ratio") },
            supportingContent = { Text(uiState.thumbnailRatio) },
            leadingContent = {
                Icon(
                    Icons.Default.PhotoSizeSelectLarge,
                    contentDescription = null
                )
            },
            modifier = Modifier.clickable { showThumbnailRatioSheet = true }
        )
        
        HorizontalDivider()
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // App Info
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "LumaLoop",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Version 1.3.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Dynamic wallpaper slideshow app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    // Show interval bottom sheet
    if (showIntervalSheet) {
        IntervalBottomSheet(
            currentInterval = uiState.interval,
            onDismiss = { showIntervalSheet = false },
            onSave = { newInterval ->
                viewModel.setInterval(newInterval)
            }
        )
    }
    
    // Show playback order bottom sheet
    if (showPlaybackOrderSheet) {
        PlaybackOrderBottomSheet(
            currentOrder = uiState.playbackOrder,
            onDismiss = { showPlaybackOrderSheet = false },
            onSave = { newOrder ->
                viewModel.setPlaybackOrder(newOrder)
                showPlaybackOrderSheet = false
            }
        )
    }
    
    // Show display mode bottom sheet
    if (showDisplayModeSheet) {
        DisplayModeBottomSheet(
            currentMode = uiState.displayMode,
            onDismiss = { showDisplayModeSheet = false },
            onSave = { newMode ->
                viewModel.setDisplayMode(newMode)
                showDisplayModeSheet = false
            }
        )
    }

    if (showThumbnailRatioSheet) {
        ThumbnailRatioBottomSheet(
            selectedRatio = uiState.thumbnailRatio,
            onRatioSelected = { viewModel.setThumbnailRatio(it) },
            onDismiss = { showThumbnailRatioSheet = false }
        )
    }
}
