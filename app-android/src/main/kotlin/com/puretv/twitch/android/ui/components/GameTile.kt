package com.puretv.twitch.android.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.core.model.GameInfo

/**
 * A category/game tile: a 3:4 box-art card at `shapes.card` that morphs out to
 * `shapes.cardMorph` under the finger, plus the game name below. The label sits
 * outside the morphing surface so the tile's shape feedback stays confined to
 * the art, and reserves exactly two lines so tiles in a grid row keep their
 * baselines aligned regardless of name length.
 */
@Composable
fun GameTile(game: GameInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }

    Column(modifier = modifier.width(120.dp)) {
        AsyncImage(
            model = game.boxArtUrl.replace("{width}", "285").replace("{height}", "380"),
            contentDescription = game.name,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(3f / 4f)
                .expressiveClickable(
                    interaction = interaction,
                    onClick = onClick,
                    restRadius = shapes.card,
                    pressRadius = shapes.cardMorph,
                    color = c.surfaceHigh,
                ),
            contentScale = ContentScale.Crop,
        )
        Text(
            game.name,
            style = MaterialTheme.typography.titleMedium,
            color = c.onSurface,
            // Two lines, fixed, so rows of tiles align even when names wrap.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}
