package com.puretv.twitch.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.model.GameInfo

/**
 * A category/game tile: portrait box-art (3:4) plus the game name. The box-art
 * sits on a Surface2 placeholder so it never flashes black, is clipped to the
 * medium shape to match StreamCard, and the label reserves exactly two lines so
 * tiles in a grid row keep their baselines aligned regardless of name length.
 */
@Composable
fun GameTile(game: GameInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = game.boxArtUrl.replace("{width}", "285").replace("{height}", "380"),
            contentDescription = game.name,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium)
                .background(PureTvColors.Surface2),
            contentScale = ContentScale.Crop,
        )
        Text(
            game.name,
            style = MaterialTheme.typography.titleMedium,
            color = PureTvColors.TextPrimary,
            // Two lines, fixed, so rows of tiles align even when names wrap.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}
