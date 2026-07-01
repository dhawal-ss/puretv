package com.puretv.twitch.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.core.model.ChatMessage

/**
 * SECTION 07.4 — semi-transparent right-side chat rail toggled by the
 * remote's MENU button (DPAD_RIGHT also reveals it per spec; DPAD_LEFT
 * returns focus to the player and dismisses it). Read-only on TV — typing
 * chat messages with a D-pad isn't a primary use case, so no compose box
 * is rendered (mirrors real Twitch-on-TV apps' chat treatment).
 */
@Composable
fun TvChatOverlay(visible: Boolean, messages: List<ChatMessage>, modifier: Modifier = Modifier) {
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
        // fixed-width rail to the right edge from here.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(messages, key = { it.id }) { message ->
                            Column {
                                Text(
                                    text = message.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = runCatching { Color(android.graphics.Color.parseColor(message.color)) }
                                        .getOrDefault(Color(0xFF9B5DE5)),
                                )
                                Text(
                                    text = message.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
