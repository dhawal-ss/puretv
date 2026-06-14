package com.puretv.twitch.desktop.auth

/**
 * Auth storage schema version. Bumped when a change makes existing stored
 * sessions unusable — e.g. the move to Device Code Grant, after which tokens
 * minted under the old authorization_code flow can't be refreshed (no secret).
 */
const val CURRENT_AUTH_SCHEMA = 1

/**
 * True when the stored session predates [currentSchema] and a session exists —
 * i.e. we should clear it and prompt a one-time re-login on upgrade.
 */
fun needsAuthReset(storedSchema: Int, currentSchema: Int, hasSession: Boolean): Boolean =
    hasSession && storedSchema < currentSchema
