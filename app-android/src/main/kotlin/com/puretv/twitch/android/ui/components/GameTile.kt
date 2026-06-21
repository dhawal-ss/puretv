package com.puretv.twitch.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** A category/game tile: portrait box-art (3:4) plus the game name. */
@Composable
fun GameTile(game: GameInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(120.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = game.boxArtUrl.replace("{width}", "285").replace("{height}", "380"),
            contentDescription = game.name,
            modifier = Modifier.width(120.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Text(
            game.name,
            style = MaterialTheme.typography.bodySmall,
            color = PureTvColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
