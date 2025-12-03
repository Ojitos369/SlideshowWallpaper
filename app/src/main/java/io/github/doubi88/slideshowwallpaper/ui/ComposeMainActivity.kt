package io.github.doubi88.slideshowwallpaper.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.doubi88.slideshowwallpaper.ui.theme.SlideshowWallpaperTheme

class ComposeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Handle shared media from gallery
        val sharedUris = when (intent?.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { listOf(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        
        setContent {
            SlideshowWallpaperTheme {
                MainScreen(sharedUris = sharedUris)
            }
        }
    }
}
