package com.puretv.twitch.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvColors

/**
 * SECTION 10: loading skeletons. A traveling-highlight shimmer plus card-shaped
 * placeholders, so a list that is still fetching shows its eventual SHAPE rather
 * than a spinner over a void. Built on the same elevation tokens (Surface1 base,
 * Surface2/Surface3 highlight) and the same corner shapes as the real cards, so
 * the loading state dissolves cleanly into the loaded one.
 */

/**
 * Paints an animated diagonal highlight sweep across the element. Apply to any
 * placeholder box; pair with .clip(shape) before it so the sweep is contained.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-translate",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            PureTvColors.Surface1,
            PureTvColors.Surface3,
            PureTvColors.Surface1,
        ),
        start = Offset(translate - 300f, translate - 300f),
        end = Offset(translate, translate),
    )
    return this.background(brush)
}

/** A single shimmering block of the given shape. */
@Composable
private fun ShimmerBlock(modifier: Modifier, corner: Int = 6) {
    Spacer(modifier.clip(RoundedCornerShape(corner.dp)).shimmer())
}

/**
 * Placeholder matching [StreamCard]: a 16:9 shimmering thumbnail over three
 * stacked text lines of decreasing width.
 */
@Composable
fun StreamCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(PureTvColors.Surface1),
    ) {
        ShimmerBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f), corner = 0)
        Column(modifier = Modifier.padding(8.dp)) {
            ShimmerBlock(Modifier.fillMaxWidth(0.7f).height(13.dp))
            Spacer(Modifier.height(6.dp))
            ShimmerBlock(Modifier.fillMaxWidth(0.45f).height(11.dp))
            Spacer(Modifier.height(6.dp))
            ShimmerBlock(Modifier.fillMaxWidth(0.9f).height(11.dp))
        }
    }
}

/** Placeholder matching [GameTile]: a 3:4 box-art block plus a label line. */
@Composable
fun GameTileSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(120.dp)) {
        ShimmerBlock(
            Modifier.width(120.dp).aspectRatio(3f / 4f),
            corner = 8,
        )
        Spacer(Modifier.height(6.dp))
        ShimmerBlock(Modifier.fillMaxWidth(0.8f).height(13.dp))
    }
}
