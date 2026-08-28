package com.puretv.twitch.android.ui.components

/** Twitch first-party emote CDN URL (static, dark, 2x), built from the emote id. */
fun twitchEmoteUrl(id: String): String =
    "https://static-cdn.jtvnw.net/emoticons/v2/$id/static/dark/2.0"
