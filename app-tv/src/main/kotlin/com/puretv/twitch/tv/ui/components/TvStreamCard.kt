package com.puretv.twitch.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType

/** Twitch thumbnail/box-art URLs are templates carrying `{width}`/`{height}`. */
private fun templated(url: String, width: Int, height: Int): String =
    url.replace("{width}", width.toString()).replace("{height}", height.toString())

/**
 * SECTION 7.3 [CRITICAL] — canonical TV card pattern: 16:9 art over a
 * [PureTvTvTheme.colors.cardScrim], a [TvLivePill] top-left, the viewer count
 * (mono, via [formatTvViewerCount]) bottom-right, then the channel name and
 * category below. The whole card is one [Modifier.tvFocusClickable] target,
 * so focus is the one thing that morphs, grows and rings it, there is no
 * hand-rolled border or scale here anymore.
 *
 * The outer 6dp padding is the neighbour's breathing room: [tvFocusClickable]
 * scales the card up to `PureTvTvMotion.FocusScale` on focus, and a shelf
 * spaced tightly enough to touch at rest would have focused cards overlap
 * their neighbours.
 */
@Composable
fun TvStreamCard(stream: StreamInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .padding(6.dp)
            .width(220.dp)
            .tvFocusClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = shapes.card,
                focusRadius = shapes.cardFocus,
                color = c.surfaceContainer,
                focusColor = c.surfaceHigh,
            )
            .padding(12.dp),
    ) {
        // Live preview thumbnail. Twitch serves a {width}x{height} template we
        // substitute; a coloured plate shows through until the image loads.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shapes.thumbShape),
        ) {
            val thumb = templated(stream.thumbnailUrl, 440, 248)
            if (thumb.isNotBlank()) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(c.surfaceHigh))
            }
            Box(Modifier.fillMaxSize().background(c.cardScrim))
            TvLivePill(modifier = Modifier.align(Alignment.TopStart).padding(10.dp), height = 28.dp)
            Text(
                text = formatTvViewerCount(stream.viewerCount.toLong()),
                style = PureTvTvType.dataSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            )
        }
        Column(Modifier.padding(top = 10.dp)) {
            Text(
                text = stream.userName,
                style = MaterialTheme.typography.titleMedium,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stream.gameName.ifBlank { "Live" },
                style = MaterialTheme.typography.bodyMedium,
                color = c.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Same card grammar as [TvStreamCard], applied to game/category tiles for [TvBrowseScreen]. */
@Composable
fun TvGameCard(game: GameInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .padding(6.dp)
            .width(180.dp)
            .tvFocusClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = shapes.card,
                focusRadius = shapes.cardFocus,
                color = c.surfaceContainer,
                focusColor = c.surfaceHigh,
            )
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(285f / 380f)
                .clip(shapes.thumbShape),
        ) {
            val art = templated(game.boxArtUrl, 285, 380)
            if (art.isNotBlank()) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(c.surfaceHigh))
            }
        }
        Text(
            text = game.name,
            style = MaterialTheme.typography.titleMedium,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
