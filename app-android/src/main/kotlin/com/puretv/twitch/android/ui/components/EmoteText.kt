package com.puretv.twitch.android.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart

/** Twitch first-party emote CDN URL (static, dark, 2x), built from the emote id. */
fun twitchEmoteUrl(id: String): String =
    "https://static-cdn.jtvnw.net/emoticons/v2/$id/static/dark/2.0"

/**
 * Renders one chat message with emotes drawn inline as images. Twitch emotes
 * come from the parsed [MessagePart.TwitchEmote] parts; third-party (7TV/BTTV/
 * FFZ) emotes are matched by word against [emotes] (name to url), since the live
 * IRC parser only tags first-party emotes in [ChatMessage.parsedParts].
 */
@Composable
fun EmoteText(message: ChatMessage, emotes: Map<String, String>, modifier: Modifier = Modifier) {
    val inline = HashMap<String, InlineTextContent>()
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = parseChatColor(message.color), fontWeight = FontWeight.SemiBold)) {
            append(message.displayName)
        }
        append(": ")
        message.parsedParts.forEach { part ->
            when (part) {
                is MessagePart.Text -> appendWords(part.content, emotes, inline)
                is MessagePart.TwitchEmote -> appendEmote("tw_${part.id}", part.name, twitchEmoteUrl(part.id), inline)
                is MessagePart.ThirdPartyEmote -> appendEmote("tp_${part.name}", part.name, part.url, inline)
            }
        }
    }
    Text(
        text = text,
        inlineContent = inline,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = PureTvColors.TextPrimary,
    )
}

private fun AnnotatedString.Builder.appendWords(
    content: String,
    emotes: Map<String, String>,
    inline: HashMap<String, InlineTextContent>,
) {
    val tokens = content.split(" ")
    tokens.forEachIndexed { i, token ->
        val url = if (token.isNotBlank()) emotes[token] else null
        if (url != null) appendEmote("tp_$token", token, url, inline) else append(token)
        if (i < tokens.lastIndex) append(" ")
    }
}

private fun AnnotatedString.Builder.appendEmote(
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
private fun parseChatColor(hex: String): Color =
    if (hex.isBlank()) {
        PureTvColors.TwitchPurpleLight
    } else {
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(PureTvColors.TwitchPurpleLight)
    }
