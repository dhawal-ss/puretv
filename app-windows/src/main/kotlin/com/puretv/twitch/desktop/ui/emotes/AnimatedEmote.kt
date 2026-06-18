package com.puretv.twitch.desktop.ui.emotes

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier

/** Whether animated emotes should play. Provided from AppSettings.animateEmotes at the app root. */
val LocalEmoteAnimation = staticCompositionLocalOf { false }

/** The shared decoded-frame cache. Null when not provided (then we render statically). */
val LocalEmoteFrameCache = staticCompositionLocalOf<EmoteFrameCache?> { null }

/**
 * Plays an animated emote from [url]. While frames load (or if decode fails / no cache) it
 * renders the static frame via [staticFallback] so the emote is never blank. Frame timing is
 * driven by a single withFrameNanos loop using [frameIndexAt].
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
    if (current == null) { staticFallback(url, name, modifier); return }

    var index by remember(url) { mutableStateOf(0) }
    LaunchedEffect(current) {
        var start = 0L
        var first = true
        while (true) {
            withFrameNanos { now ->
                if (first) { start = now; first = false }
                // Only write when the frame actually changes (the displayed frame holds
                // for ~6 vsyncs at 100ms), so we don't churn snapshot state every frame.
                val next = frameIndexAt((now - start) / 1_000_000, current.durationsMs)
                if (next != index) index = next
            }
        }
    }
    Image(bitmap = current.frames[index], contentDescription = name, modifier = modifier)
}
