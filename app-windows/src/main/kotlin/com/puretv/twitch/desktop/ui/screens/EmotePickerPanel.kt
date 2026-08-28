package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
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
import com.puretv.twitch.core.emotes.EmoteSource
import com.puretv.twitch.core.emotes.PickableEmote
import com.puretv.twitch.desktop.ui.components.EmoteImage
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveDivider
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.components.expressiveSurface
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType

/** The section label shown above each provider's tiles, in encounter order. */
private fun EmoteSource.categoryLabel(): String = when (this) {
    EmoteSource.TWITCH_CHANNEL -> "Channel"
    EmoteSource.TWITCH_GLOBAL -> "Global"
    EmoteSource.BTTV -> "BetterTTV"
    EmoteSource.FFZ -> "FrankerFaceZ"
    EmoteSource.SEVENTV -> "7TV"
}

/**
 * A Cinémathèque-styled emote picker shown directly above the chat composer.
 * Filters [emotes] by code substring (case-insensitive) via the search field,
 * groups the result by provider and renders it as an adaptive grid of clickable
 * tiles under kicker headers. Each tap fires [onPick]; [onDismiss] closes the panel.
 */
@Composable
fun EmotePickerPanel(
    emotes: List<PickableEmote>,
    onPick: (PickableEmote) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, emotes) {
        if (query.isBlank()) emotes
        else emotes.filter { it.code.contains(query.trim(), ignoreCase = true) }
    }
    // groupBy preserves both key-encounter order and per-key member order, so the
    // headers fall out in the same channel/global/third-party order buildPickableEmotes
    // already established — no re-sort needed.
    val grouped = remember(filtered) { filtered.groupBy { it.source } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .background(c.surfaceContainer, shapes.cardShape),
    ) {
        // Header: search field + close affordance.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val searchInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .hoverable(searchInteraction)
                    .expressiveSurface(
                        interaction = searchInteraction,
                        restRadius = 22.dp,
                        hoverRadius = shapes.md,
                        color = c.surfaceHigh,
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text("Search emotes", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ExpressiveIconButton(
                icon = ExpressiveIcons.Close,
                contentDescription = "Close emote picker",
                onClick = onDismiss,
                style = ExpressiveButtonStyle.Text,
                boxSize = 32.dp,
                iconSize = 16.dp,
            )
        }
        ExpressiveDivider()

        if (filtered.isEmpty()) {
            Text(
                "No emotes",
                color = c.onSurfaceVariant,
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                grouped.forEach { (source, group) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            source.categoryLabel().uppercase(),
                            style = PureTvType.kicker,
                            color = c.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp, start = 2.dp),
                        )
                    }
                    items(group, key = { it.source.name + ":" + it.code }) { emote ->
                        EmoteTile(emote, onPick)
                    }
                }
            }
        }
    }
}

/** One tile: a squircle at rest that rounds further out and lights up on hover. */
@Composable
private fun EmoteTile(emote: PickableEmote, onPick: (PickableEmote) -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = { onPick(emote) },
                restRadius = 10.dp,
                hoverRadius = 17.dp,
                hoverColor = c.surfaceHighest,
            ),
        contentAlignment = Alignment.Center,
    ) {
        EmoteImage(emote.imageUrl, emote.code, modifier = Modifier.size(28.dp))
    }
}
