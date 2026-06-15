package com.puretv.twitch.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.stream.Storyboard
import com.puretv.twitch.core.stream.StoryboardParser
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.koin.core.Koin

/** Decodes a sprite-sheet image to an ImageBitmap (refetched only when [url] changes). */
@Composable
private fun rememberSpriteBitmap(koin: Koin, url: String?): ImageBitmap? {
    val client = remember { koin.get<HttpClient>() }
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bmp = if (url == null) null else runCatching {
            val bytes: ByteArray = client.get(url).body()
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }
    return bmp
}

/**
 * A scrub-preview thumbnail cropped from the VOD storyboard at [positionSeconds].
 * Fixed 160x90 display box (the "low" tile size). Renders nothing if there's no
 * storyboard or the sprite hasn't loaded yet.
 */
@Composable
fun SeekPreview(
    koin: Koin,
    storyboard: Storyboard?,
    positionSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val spec = remember(storyboard) { storyboard?.spec("low") } ?: return
    val tile = remember(spec, storyboard, positionSeconds) {
        StoryboardParser.tileAt(spec, storyboard!!.baseUrl, positionSeconds)
    }
    val bitmap = rememberSpriteBitmap(koin, tile.imageUrl) ?: return
    val c = PureTvTheme.colors
    Box(
        modifier
            .size(160.dp, 90.dp)
            .background(c.background, PureTvShape.sm)
            .border(1.dp, c.hairline, PureTvShape.sm),
    ) {
        Canvas(Modifier.size(160.dp, 90.dp)) {
            drawImage(
                image = bitmap,
                srcOffset = IntOffset(tile.srcXPx, tile.srcYPx),
                srcSize = IntSize(tile.tileWidthPx, tile.tileHeightPx),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
        }
    }
}
