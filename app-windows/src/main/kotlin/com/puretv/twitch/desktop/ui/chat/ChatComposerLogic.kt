package com.puretv.twitch.desktop.ui.chat

// The text helpers (wordAtCursor, completeWord, insertAtCursor, suggestions)
// live in core's com.puretv.twitch.core.chat.ChatComposer so Android shares
// them. Only the keyboard mapping, which is desktop-specific, stays here.

/** What a key press in the chat composer should do. */
enum class ComposerKeyAction { SEND, COMPLETE, NONE }

/**
 * Maps a composer key press to an action. Enter sends (matching Twitch's web
 * client, and what users expect instead of clicking the arrow); Tab completes when a
 * suggestion (emote or @username) is offered or a Tab cycle is under way, and the
 * composer moves to the next suggestion on each further press; anything else
 * falls through to normal text editing. Enter wins over Tab even while the
 * autocomplete strip is open: Tab is the completion key, Enter is always "send".
 */
fun composerKeyAction(isEnter: Boolean, isTab: Boolean, hasSuggestions: Boolean): ComposerKeyAction = when {
    isEnter -> ComposerKeyAction.SEND
    isTab && hasSuggestions -> ComposerKeyAction.COMPLETE
    else -> ComposerKeyAction.NONE
}
