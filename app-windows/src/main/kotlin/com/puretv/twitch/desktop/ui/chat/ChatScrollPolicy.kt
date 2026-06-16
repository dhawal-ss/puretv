package com.puretv.twitch.desktop.ui.chat

import com.puretv.twitch.core.model.ChatMessage

/**
 * Key the auto-scroll effect on this — the newest message's id.
 *
 * GOTCHA: the chat buffer is capped via `takeLast(200)`, so once it fills, every
 * new message keeps `messages.size` at 200. A `LaunchedEffect(messages.size)`
 * therefore stops re-firing and auto-scroll silently dies. The newest message's
 * id changes on every new message regardless of the cap, so it keeps firing.
 */
fun scrollAnchor(messages: List<ChatMessage>): String? = messages.lastOrNull()?.id

/**
 * Next "follow the live feed" intent. Auto-scroll is gated on THIS, not on
 * instantaneous geometry — so a batch of newly-appended (briefly unmeasured) rows,
 * which makes `atBottom` read false without any user scroll, does NOT pause following
 * (the VOD bug). A user scroll away from the bottom pauses; reaching the bottom resumes.
 * Our own scrolls are instant snaps to the bottom, so they only ever resume.
 */
fun nextFollowing(following: Boolean, atBottom: Boolean, userScrolling: Boolean): Boolean = when {
    userScrolling && !atBottom -> false
    atBottom -> true
    else -> following
}
