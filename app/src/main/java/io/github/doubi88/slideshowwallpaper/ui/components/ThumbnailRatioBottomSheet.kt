package io.github.doubi88.slideshowwallpaper.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThumbnailRatioBottomSheet(
    currentRatio: String,
    onRatioSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Thumbnail Ratio",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            
            val ratios = listOf("9:16", "3:4", "1:1", "16:9", "Natural")
            
            ratios.forEach { ratio ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onRatioSelected(ratio) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (ratio == currentRatio)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = ratio,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (ratio == currentRatio) {
                            RadioButton(
                                selected = true,
                                onClick = null
                            )
                        }
                    }
                }
            }
        }
    }
}
