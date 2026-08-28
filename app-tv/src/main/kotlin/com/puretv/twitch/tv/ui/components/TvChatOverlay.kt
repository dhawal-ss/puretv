package com.puretv.twitch.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ClockFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * SECTION 07.4 — semi-transparent right-side chat rail toggled by the
 * remote's MENU button (DPAD_RIGHT also reveals it per spec; DPAD_LEFT
 * returns focus to the player and dismisses it). Read-only on TV — typing
 * chat messages with a D-pad isn't a primary use case, so no compose box
 * is rendered (mirrors real Twitch-on-TV apps' chat treatment).
 */
@Composable
fun TvChatOverlay(visible: Boolean, messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier,
    ) {
        // AnimatedVisibility's content scope is not a BoxScope, so alignment can't
        // be applied to the rail directly. Fill the transition area and align the
        // fixed-width rail to the right edge from here, with a margin so all four
        // corners of the panel below actually show their rounding.
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(420.dp)
                    .clip(shapes.paneShape)
                    .background(c.surfaceContainer)
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.onSurface,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(messages, key = { it.id }) { message -> TvChatMessageRow(message) }
                    }
                }
            }
        }
    }
}

/**
 * One chat line, its own rounded card at [shapes.mdShape]. A fast-moving feed
 * reads as distinct rows from three metres away this way, rather than a
 * run-on wall of same-weight text with no boundary between messages.
 */
@Composable
private fun TvChatMessageRow(message: ChatMessage) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val nameColor = remember(message.color, c.primary) {
        runCatching { Color(android.graphics.Color.parseColor(message.color)) }.getOrDefault(c.primary)
    }
    val timestamp = remember(message.timestamp) {
        runCatching {
            Instant.ofEpochMilli(message.timestamp).atZone(ZoneId.systemDefault()).format(ClockFormatter)
        }.getOrDefault("")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.mdShape)
            .background(c.surfaceHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = nameColor,
            )
            Text(text = timestamp, style = PureTvTvType.dataSmall, color = c.onSurfaceVariant)
        }
        Text(
            text = message.message,
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurface,
        )
    }
}
