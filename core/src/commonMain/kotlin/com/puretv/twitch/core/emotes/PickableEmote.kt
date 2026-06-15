package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.EmoteProvider

enum class EmoteSource { TWITCH_CHANNEL, TWITCH_GLOBAL, BTTV, FFZ, SEVENTV }

data class PickableEmote(val code: String, val imageUrl: String, val animated: Boolean, val source: EmoteSource)

private fun sourceFor(e: ChannelEmote, twitchIsChannel: Boolean): EmoteSource = when (e.provider) {
    EmoteProvider.TWITCH -> if (twitchIsChannel) EmoteSource.TWITCH_CHANNEL else EmoteSource.TWITCH_GLOBAL
    EmoteProvider.BTTV -> EmoteSource.BTTV
    EmoteProvider.FFZ -> EmoteSource.FFZ
    EmoteProvider.SEVENTV -> EmoteSource.SEVENTV
}

/**
 * Stable order: channel Twitch, channel third-party, Twitch globals, global
 * third-party. De-dupe by code (first wins, case-insensitive). Blank codes skipped.
 */
fun buildPickableEmotes(
    twitchChannel: List<ChannelEmote>,
    thirdPartyChannel: List<ChannelEmote>,
    twitchGlobal: List<ChannelEmote>,
    thirdPartyGlobal: List<ChannelEmote>,
): List<PickableEmote> {
    val ordered =
        twitchChannel.map { it to true } +
        thirdPartyChannel.map { it to false } +
        twitchGlobal.map { it to false } +
        thirdPartyGlobal.map { it to false }
    val seen = HashSet<String>()
    val out = ArrayList<PickableEmote>()
    for ((e, isChan) in ordered) {
        if (e.name.isBlank() || !seen.add(e.name.lowercase())) continue
        out += PickableEmote(e.name, e.url, e.animated, sourceFor(e, isChan))
    }
    return out
}
