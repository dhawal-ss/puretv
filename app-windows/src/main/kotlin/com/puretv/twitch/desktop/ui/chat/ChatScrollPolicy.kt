package com.puretv.twitch.desktop.ui.chat

/** Whether to instantly re-pin to the newest message. */
fun shouldStick(atBottom: Boolean): Boolean = atBottom

/** Next "has unread below" flag. Clear at bottom; set when new messages arrive while scrolled up. */
fun nextUnread(atBottom: Boolean, messagesGrew: Boolean, current: Boolean): Boolean = when {
    atBottom -> false
    messagesGrew -> true
    else -> current
}
