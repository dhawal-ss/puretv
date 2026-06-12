package com.puretv.twitch.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart

/**
 * SECTION 06.4 / 05 — live chat list + composer. Renders [MessagePart.Text]
 * inline and [MessagePart.TwitchEmote]/[MessagePart.ThirdPartyEmote] as a
 * `[code]` placeholder chip (swap for `AsyncImage` via Coil once emote URLs
 * are resolved through [com.puretv.twitch.core.emotes.EmoteRepository]).
 */
@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(messages, key = { it.id }) { message -> ChatMessageRow(message) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Send a message", color = PureTvColors.TextMuted) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(onClick = {
                if (draft.isNotBlank()) {
                    onSend(draft)
                    draft = ""
                }
            }) { Text("Chat") }
        }
    }
}

@Composable
private fun ChatMessageRow(message: ChatMessage) {
    val nameColor = remember(message.color) { parseChatColor(message.color) }
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = nameColor)) {
            append(message.displayName)
        }
        append(": ")
        message.parsedParts.forEach { part ->
            when (part) {
                is MessagePart.Text -> append(part.content)
                is MessagePart.TwitchEmote -> append("[${part.name}]")
                is MessagePart.ThirdPartyEmote -> append("[${part.name}]")
            }
        }
    }
    Text(text = annotated, style = MaterialTheme.typography.bodyMedium, color = PureTvColors.TextPrimary)
}

/** Twitch sends chat colors as `#RRGGBB` hex strings (or empty for default). */
private fun parseChatColor(hex: String): androidx.compose.ui.graphics.Color =
    if (hex.isBlank()) {
        PureTvColors.TwitchPurpleLight
    } else {
        runCatching {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
        }.getOrDefault(PureTvColors.TwitchPurpleLight)
    }
