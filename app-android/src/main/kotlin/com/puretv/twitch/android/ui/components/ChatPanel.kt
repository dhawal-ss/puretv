package com.puretv.twitch.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.puretv.twitch.android.ui.theme.PureTvMotion
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SECTION 06.4 / 05: live chat list + composer, Material 3 Expressive. Each row
 * is its own rounded container (mirrors the desktop `ChatMessageRow`): sender in
 * their IRC colour, a mono clock, then the body with emotes drawn inline as
 * images. Third-party (7TV/BTTV/FFZ) emotes are matched by word against
 * [emotes] since the live IRC parser only tags first-party emotes in
 * [ChatMessage.parsedParts]; Twitch emotes come from those parsed parts
 * directly. This duplicates the small inline-content builder in [EmoteText]
 * rather than reusing it, because that composable welds the sender name into
 * the same run as the body, and the mock calls for the name and body to carry
 * two different type styles.
 */
@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    emotes: Map<String, String> = emptyMap(),
    canSend: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                ChatMessageRow(message = message, emotes = emotes, modifier = Modifier.fillMaxWidth())
            }
        }

        if (canSend) {
            if (pickerOpen && emotes.isNotEmpty()) {
                EmoteQuickPicker(
                    emotes = emotes,
                    onPick = { name -> draft = if (draft.isEmpty()) name else "$draft $name" },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            // The composer itself: a surfaceHigh pill that squares off to 16dp the
            // moment the field takes focus, echoing the press-morph language with a
            // focus-driven trigger since a text field is held, not tapped-and-released.
            val composerRadius by animateDpAsState(
                targetValue = if (fieldFocused) PureTvTheme.shapes.md else PureTvTheme.shapes.pill,
                animationSpec = PureTvMotion.MorphSpring,
                label = "composerRadius",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(composerRadius.coerceAtLeast(0.dp)))
                    .background(c.surfaceHigh)
                    .padding(start = 18.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text("Send a message", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = c.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { fieldFocused = it.isFocused },
                    )
                }
                ExpressiveIconButton(
                    icon = ExpressiveIcons.Emote,
                    contentDescription = "Emotes",
                    onClick = { pickerOpen = !pickerOpen },
                    boxSize = 44.dp,
                    iconSize = 22.dp,
                    tint = if (pickerOpen) c.primary else c.onSurfaceVariant,
                )
                ExpressiveIconButton(
                    icon = ExpressiveIcons.Send,
                    contentDescription = "Send message",
                    onClick = send,
                    style = ExpressiveButtonStyle.Filled,
                    boxSize = 44.dp,
                    iconSize = 22.dp,
                    enabled = draft.isNotBlank(),
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Log in to chat",
                    color = c.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/**
 * One chat message: its own rounded [PureTvTheme.shapes.md] container that fills
 * [PureTvAndroidColors.surfaceHigh] under the finger, or [PureTvAndroidColors.primaryContainer]
 * standing (not just on press) when it @-mentions the viewer, so a ping is
 * legible while scrolling past rather than only while touched.
 */
@Composable
private fun ChatMessageRow(message: ChatMessage, emotes: Map<String, String>, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = when {
            message.mentionsSelf -> c.primaryContainer
            pressed -> c.surfaceHigh
            else -> Color.Transparent
        },
        animationSpec = tween(PureTvMotion.Fast),
        label = "chatRowFill",
    )
    val nameColor = remember(message.color) { parseChatColor(message.color, c.primary) }
    val timestamp = remember(message.timestamp) {
        runCatching { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)) }.getOrDefault("")
    }
    val (bodyText, bodyInline) = remember(message.id, emotes) { buildChatBody(message, emotes) }

    Column(
        modifier = modifier
            .clip(shapes.mdShape)
            .background(fill)
            // No destination exists for a tap yet (no reply feature on Android), so
            // onClick is a no-op: the interaction source only drives the press fill.
            .clickable(interactionSource = interaction, indication = null, onClick = {})
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (message.isBroadcaster) {
                ChatBadgeChip("HOST", c.primary, c.onPrimary)
            } else if (message.isModerator) {
                ChatBadgeChip("MOD", c.tertiaryContainer, c.onTertiaryContainer)
            }
            if (message.isSubscriber) {
                ChatBadgeChip("SUB", c.secondaryContainer, c.onSecondaryContainer)
            }
            Text(
                message.displayName,
                color = nameColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(timestamp, style = PureTvType.dataSmall, color = c.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Text(
            text = bodyText,
            inlineContent = bodyInline,
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurface,
        )
    }
}

@Composable
private fun ChatBadgeChip(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = PureTvType.dataSmall,
        color = fg,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** A row of tappable emote thumbnails above the composer; tapping inserts the
 *  emote's name into the draft. No search/grouping: the phone only carries a
 *  flat name-to-url map, unlike desktop's richer [com.puretv.twitch.core.emotes.PickableEmote] list. */
@Composable
private fun EmoteQuickPicker(emotes: Map<String, String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val entries = remember(emotes) { emotes.entries.toList() }
    LazyRow(
        modifier = modifier
            .clip(PureTvTheme.shapes.mdShape)
            .background(c.surfaceContainer)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(entries, key = { it.key }) { (name, url) ->
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(36.dp)
                    .clip(PureTvTheme.shapes.smShape)
                    .clickable { onPick(name) },
            )
        }
    }
}

// twitchEmoteUrl(id) comes from EmoteText.kt (same package): the CDN URL builder
// for first-party Twitch emotes.

private fun buildChatBody(message: ChatMessage, emotes: Map<String, String>): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inline = HashMap<String, InlineTextContent>()
    val text = buildAnnotatedString {
        message.parsedParts.forEach { part ->
            when (part) {
                is MessagePart.Text -> appendChatWords(part.content, emotes, inline)
                is MessagePart.TwitchEmote -> appendChatEmote("tw_${part.id}", part.name, twitchEmoteUrl(part.id), inline)
                is MessagePart.ThirdPartyEmote -> appendChatEmote("tp_${part.name}", part.name, part.url, inline)
            }
        }
    }
    return text to inline
}

private fun AnnotatedString.Builder.appendChatWords(
    content: String,
    emotes: Map<String, String>,
    inline: HashMap<String, InlineTextContent>,
) {
    val tokens = content.split(" ")
    tokens.forEachIndexed { i, token ->
        val url = if (token.isNotBlank()) emotes[token] else null
        if (url != null) appendChatEmote("tp_$token", token, url, inline) else append(token)
        if (i < tokens.lastIndex) append(" ")
    }
}

private fun AnnotatedString.Builder.appendChatEmote(
    id: String,
    name: String,
    url: String,
    inline: HashMap<String, InlineTextContent>,
) {
    appendInlineContent(id, name)
    if (!inline.containsKey(id)) {
        inline[id] = InlineTextContent(
            Placeholder(width = 1.8.em, height = 1.8.em, placeholderVerticalAlign = PlaceholderVerticalAlign.Center),
        ) {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** Twitch sends chat colors as `#RRGGBB` hex strings (or empty for the default). */
private fun parseChatColor(hex: String, fallback: Color): Color =
    if (hex.isBlank()) {
        fallback
    } else {
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
    }
