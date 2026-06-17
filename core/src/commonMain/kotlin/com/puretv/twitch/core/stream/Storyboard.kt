package com.puretv.twitch.core.stream

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One quality variant in a Twitch VOD storyboard `-info.json`. */
@Serializable
data class StoryboardSpec(
    val quality: String = "",
    val count: Int = 0,
    val cols: Int = 1,
    val rows: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    val interval: Int = 1,            // seconds per tile
    val images: List<String> = emptyList(),
)

/** Parsed storyboard: sprite specs plus the base URL to resolve image names. */
data class Storyboard(val baseUrl: String, val specs: List<StoryboardSpec>) {
    /** Variant by quality name ("low"/"high"), falling back to the first. */
    fun spec(quality: String): StoryboardSpec? =
        specs.firstOrNull { it.quality == quality } ?: specs.firstOrNull()
}

/** A cropped sub-rectangle of a sprite sheet for a playback position. */
data class StoryboardTile(
    val imageUrl: String,
    val srcXPx: Int,
    val srcYPx: Int,
    val tileWidthPx: Int,
    val tileHeightPx: Int,
)

object StoryboardParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** [seekPreviewsUrl] is the `.../storyboards/<id>-info.json` URL; [jsonText] its body. */
    fun parse(seekPreviewsUrl: String, jsonText: String): Storyboard =
        Storyboard(
            baseUrl = seekPreviewsUrl.substringBeforeLast('/'),
            specs = json.decodeFromString<List<StoryboardSpec>>(jsonText),
        )

    /** The tile to show for [positionSeconds] from [spec], images resolved against [baseUrl]. */
    fun tileAt(spec: StoryboardSpec, baseUrl: String, positionSeconds: Long): StoryboardTile {
        // cols/rows/interval come from untrusted remote JSON; a 0 (or negative)
        // value must not divide-by-zero when the user hovers the scrubber
        // (audit P0-4). Coerce every divisor to a sane minimum.
        val cols = spec.cols.coerceAtLeast(1)
        val rows = spec.rows.coerceAtLeast(1)
        val perImage = cols * rows
        val idx = (positionSeconds / spec.interval.coerceAtLeast(1)).toInt()
            .coerceIn(0, (spec.count - 1).coerceAtLeast(0))
        val imageIndex = (idx / perImage).coerceIn(0, (spec.images.size - 1).coerceAtLeast(0))
        val within = idx % perImage
        val col = within % cols
        val row = within / cols
        return StoryboardTile(
            imageUrl = "$baseUrl/${spec.images.getOrElse(imageIndex) { spec.images.firstOrNull().orEmpty() }}",
            srcXPx = col * spec.width,
            srcYPx = row * spec.height,
            tileWidthPx = spec.width,
            tileHeightPx = spec.height,
        )
    }
}
