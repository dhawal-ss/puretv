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

/** Whether to instantly re-pin to the newest message. */
fun shouldStick(atBottom: Boolean): Boolean = atBottom
