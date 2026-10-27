package com.puretv.twitch.desktop.ui.emotes

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The emote frame is chosen while drawing, from the window's EmoteClock: a tick must redraw
 * the emote with the next frame, or every animated emote would freeze on its first one.
 */
class AnimatedEmoteRenderTest {
    private fun solid(color: Color): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32Premul(8, 8))
        bitmap.erase(color.toArgb())
        return bitmap.asComposeImageBitmap()
    }

    @Test
    fun theDrawnFrameFollowsTheSharedClock() {
        val cache = EmoteFrameCache(HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) }))
        cache.putForTest("e", AnimatedEmoteFrames(listOf(solid(Color.Red), solid(Color.Blue)), listOf(100, 100)))
        var clock: EmoteClock? = null
        val scene = ImageComposeScene(width = 8, height = 8, coroutineContext = Dispatchers.Unconfined) {
            // As App provides it: the window's clock, running while the content is composed.
            val windowClock = rememberEmoteClock().also { clock = it }
            CompositionLocalProvider(LocalEmoteFrameCache provides cache, LocalEmoteClock provides windowClock) {
                AnimatedEmote("e", "e", Modifier.size(8.dp)) { _, _, _ -> }
            }
        }
        try {
            val seen = HashSet<Int>()
            val deadline = System.nanoTime() + 3_000_000_000L
            var time = 0L
            while (System.nanoTime() < deadline && seen.size < 2) {
                val image = scene.render(time)
                val pixels = Bitmap.makeFromImage(image)
                seen += pixels.getColor(4, 4)
                time += 16_000_000L
                Thread.sleep(16)
            }
            assertTrue(Color.Red.toArgb() in seen && Color.Blue.toArgb() in seen, "frames drawn: $seen, clock at ${clock?.elapsedMs?.value} ms")
        } finally {
            scene.close()
        }
    }
}
