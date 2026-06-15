package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.emotes.PickableEmote
import com.puretv.twitch.desktop.ui.components.EmoteImage
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme

/**
 * A Cinémathèque-styled emote picker shown directly above the chat composer.
 * Filters [emotes] by code substring (case-insensitive) via the search field and
 * renders the result as an adaptive grid of clickable tiles. Each tap fires
 * [onPick]; [onDismiss] closes the panel.
 */
@Composable
fun EmotePickerPanel(
    emotes: List<PickableEmote>,
    onPick: (PickableEmote) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = PureTvTheme.colors
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, emotes) {
        if (query.isBlank()) emotes
        else emotes.filter { it.code.contains(query.trim(), ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .border(1.dp, c.hairline, PureTvShape.lg)
            .background(c.surfaceVariant, PureTvShape.lg),
    ) {
        // Header: search field + close affordance.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search emotes", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.textPrimary, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                    cursorBrush = SolidColor(c.twitchPurple),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, "Close emote picker", tint = c.textSecondary, modifier = Modifier.size(15.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))

        if (filtered.isEmpty()) {
            Text(
                "No emotes",
                color = c.textMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(34.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filtered, key = { it.source.name + ":" + it.code }) { emote ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { onPick(emote) },
                        contentAlignment = Alignment.Center,
                    ) {
                        EmoteImage(emote.imageUrl, emote.code, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}
