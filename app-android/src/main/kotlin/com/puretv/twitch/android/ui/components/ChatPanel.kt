package com.puretv.twitch.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.model.ChatMessage

/**
 * SECTION 06.4 / 05: live chat list + composer. Each message renders via
 * [EmoteText], which draws Twitch and third-party emotes inline as images.
 * When the viewer is not signed in the IRC connection is anonymous (read-only),
 * so the composer is replaced with a sign-in hint rather than silently dropping
 * messages.
 */
@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    emotes: Map<String, String> = emptyMap(),
    canSend: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    // Key on the newest message id, not messages.size: the list is capped at 200
    // (takeLast(200) in the VM), so size saturates and a size-keyed effect would
    // stop firing, freezing auto-scroll in any busy channel.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val send: () -> Unit = {
        if (draft.isNotBlank()) {
            onSend(draft)
            draft = ""
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                EmoteText(message = message, emotes = emotes, modifier = Modifier.fillMaxWidth())
            }
        }

        if (canSend) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Send a message", color = PureTvColors.TextMuted) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
                IconButton(onClick = send, enabled = draft.isNotBlank()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (draft.isNotBlank()) PureTvColors.TwitchPurple else PureTvColors.TextMuted,
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Log in to chat",
                    color = PureTvColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
