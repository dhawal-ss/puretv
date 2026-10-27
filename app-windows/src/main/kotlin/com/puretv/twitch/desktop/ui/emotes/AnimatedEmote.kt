package com.puretv.twitch.desktop.ui.emotes

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** Whether animated emotes should play. Provided from AppSettings.animateEmotes at the app root. */
val LocalEmoteAnimation = staticCompositionLocalOf { false }

/** The shared decoded-frame cache. Null when not provided (then we render statically). */
val LocalEmoteFrameCache = staticCompositionLocalOf<EmoteFrameCache?> { null }

/** The window's one emote clock (see [EmoteClock]). Null when not provided: emotes then hold still. */
val LocalEmoteClock = staticCompositionLocalOf<EmoteClock?> { null }

/**
 * One clock for every animated emote in a window, ticking [EmoteClock.TICK_MS] apart and
 * only while an animated emote is on screen.
 *
 * Each emote used to wait for every display frame itself. On Compose Desktop anything
 * waiting for a frame makes the whole window draw again, so one animated emote in chat
 * redrew the window at the monitor's refresh rate (60 to 240 times a second) and
 * recomposed itself on every frame change. Now the window redraws at most 50 times a
 * second, emotes read the clock only while drawing (no recomposition), and every copy of
 * the same emote shows the same frame.
 */
class EmoteClock {
    private val elapsed = mutableLongStateOf(0L)
    private val users = mutableIntStateOf(0)

    /** Milliseconds since the clock first ran; read it in the draw phase. */
    val elapsedMs: State<Long> get() = elapsed

    fun attach() { users.intValue++ }

    fun detach() { users.intValue-- }

    /** Ticks while anything is attached; pauses (no redraws at all) when nothing is. */
    suspend fun run() {
        val origin = System.nanoTime()
        snapshotFlow { users.intValue > 0 }.collectLatest { active ->
            while (active) {
                elapsed.longValue = (System.nanoTime() - origin) / 1_000_000
                delay(TICK_MS)
            }
        }
    }

    companion object {
        /** 50 a second: emote frames hold 20 to 100 ms, so finer steps show nothing new. */
        const val TICK_MS = 20L
    }
}

/** The window's [EmoteClock], running for as long as the window's content is composed. */
@Composable
fun rememberEmoteClock(): EmoteClock {
    val clock = remember { EmoteClock() }
    LaunchedEffect(clock) { clock.run() }
    return clock
}

/**
 * Plays an animated emote from [url]. While frames load (or if decode fails / no cache) it
 * renders the static frame via [staticFallback] so the emote is never blank. The frame is
 * chosen from the window's [EmoteClock] while drawing.
 */
@Composable
fun AnimatedEmote(
    url: String,
    name: String,
    modifier: Modifier,
    staticFallback: @Composable (String, String, Modifier) -> Unit,
) {
    val cache = LocalEmoteFrameCache.current
    if (cache == null) { staticFallback(url, name, modifier); return }

    // Hold a reference for this url while composed so the cache never frees its native frames
    // mid-render (an LRU eviction can target an on-screen entry); release on dispose/url change.
    DisposableEffect(url) {
        cache.retain(url)
        onDispose { cache.release(url) }
    }

    var frames by remember(url) { mutableStateOf<AnimatedEmoteFrames?>(null) }
    LaunchedEffect(url) { frames = cache.frames(url) }

    val current = frames
    val clock = LocalEmoteClock.current
    if (current == null || current.frames.isEmpty()) { staticFallback(url, name, modifier); return }
    if (clock == null) {
        Image(bitmap = current.frames[0], contentDescription = name, modifier = modifier)
        return
    }
    DisposableEffect(clock) {
        clock.attach()
        onDispose { clock.detach() }
    }
    val painter = remember(current, clock) { EmoteFramePainter(current, clock.elapsedMs) }
    Image(painter = painter, contentDescription = name, modifier = modifier)
}

/**
 * Draws the frame [frames] shows at the clock's time. The clock is read in [onDraw], so a
 * tick only redraws the emote. Mipmapped sampling: the frames are decoded at the CDN's
 * largest size and drawn about a quarter of that, which plain bilinear made jagged.
 */
private class EmoteFramePainter(
    private val frames: AnimatedEmoteFrames,
    private val clockMs: State<Long>,
) : Painter() {
    private val timeline = FrameTimeline(frames.durationsMs)

    override val intrinsicSize: Size =
        frames.frames[0].let { Size(it.width.toFloat(), it.height.toFloat()) }

    override fun DrawScope.onDraw() {
        val index = timeline.indexAt(clockMs.value).coerceIn(0, frames.frames.lastIndex)
        drawImage(
            image = frames.frames[index],
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}
