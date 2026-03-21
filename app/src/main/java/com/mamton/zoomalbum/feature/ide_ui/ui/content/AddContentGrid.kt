package com.mamton.zoomalbum.feature.ide_ui.ui.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ContentType(val icon: String, val label: String)

private val contentTypes = listOf(
    ContentType("\uD83D\uDDBC", "Photo"),   // 🖼
    ContentType("\uD83C\uDFA5", "Video"),   // 🎥
    ContentType("\uD83D\uDCC4", "Text"),    // 📄
    ContentType("\u2B50", "Sticker"),        // ⭐
    ContentType("\u25A1", "Frame"),          // □
)

/**
 * Reusable content type picker grid — rendered inside
 * [com.mamton.zoomalbum.feature.ide_ui.ui.sheets.AddContentBottomSheet].
 */
@Composable
fun AddContentGrid(
    onContentTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(contentTypes) { type ->
            ContentTypeCard(
                icon = type.icon,
                label = type.label,
                onClick = { onContentTypeSelected(type.label) },
            )
        }
    }
}

@Composable
private fun ContentTypeCard(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .size(96.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = icon, fontSize = 28.sp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
