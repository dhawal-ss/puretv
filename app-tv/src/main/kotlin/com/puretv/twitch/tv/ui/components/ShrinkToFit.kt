package com.puretv.twitch.tv.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Shrinks this element uniformly when it would otherwise be taller than the
 * space it is given, and leaves it completely alone when it fits.
 *
 * A television is the one platform where the app cannot scroll its way out of
 * an overflow. Scroll position on a 10-foot UI only moves when focus moves, so
 * anything that is not focusable (a QR code, an activation code, a paragraph of
 * instructions) is simply unreachable once it falls past the bottom edge: it is
 * not "below the fold", it does not exist. Issue #20 was exactly that: the
 * sign-in code was rendered, off-screen, on a set whose usable height is smaller
 * than the layout assumed.
 *
 * The set of heights a TV can report is wider than it looks. Most Android TVs
 * hand out the canonical 960x540dp surface, but a display-size setting shrinks
 * that, a larger text setting inflates every `sp` inside it, and the two
 * compound. Rather than guess at the range, this measures what the content
 * actually came out to and scales the whole thing down by whatever it takes.
 * Scaling down, never up: on a set with room to spare the layout is left at its
 * designed size instead of being blown up to fill the panel.
 *
 * The knock-on effect is that a larger system font no longer risks hiding the
 * code. It makes the content taller, which pulls the scale factor down, which
 * lands the layout back inside the screen. Text ends up close to the size it
 * would have been, and remains visible, which is the trade this screen needs.
 */
fun Modifier.shrinkToFitHeight(): Modifier = layout { measurable, constraints ->
    // Measure with the height ceiling lifted so the content reports the height
    // it actually wants rather than the clipped one the parent would impose.
    val placeable = measurable.measure(
        Constraints(
            minWidth = constraints.minWidth,
            maxWidth = constraints.maxWidth,
            minHeight = 0,
            maxHeight = Constraints.Infinity,
        ),
    )
    val scale = if (constraints.hasBoundedHeight && placeable.height > constraints.maxHeight) {
        constraints.maxHeight.toFloat() / placeable.height
    } else {
        1f
    }
    // Anchored top-centre: the content keeps its horizontal centring as it
    // shrinks, and the caller decides where the resulting block sits.
    layout(placeable.width, (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    }
}
