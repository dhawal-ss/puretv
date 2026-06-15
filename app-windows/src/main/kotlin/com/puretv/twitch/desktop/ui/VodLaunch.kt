package com.puretv.twitch.desktop.ui

/** Everything the VOD player needs at launch: the id to play, plus display
 *  metadata so a resume entry can be stored without an extra API call. */
data class VodLaunch(
    val vodId: String,
    val channelLogin: String,
    val title: String,
    val thumbnailUrl: String,
)
