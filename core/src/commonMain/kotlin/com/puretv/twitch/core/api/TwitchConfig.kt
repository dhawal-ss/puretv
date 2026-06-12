package com.puretv.twitch.core.api

/**
 * PureTV for Twitch — central Twitch endpoint & credential configuration.
 *
 * To run your own build:
 *   1. Register an application at https://dev.twitch.tv/console
 *   2. Set the OAuth Redirect URI to http://localhost:3000 (desktop) and
 *      puretv-twitch://auth (Android/TV)
 *   3. Generate a Client Secret (Twitch's authorization_code flow requires
 *      one even with PKCE — unlike most OAuth providers). Put it in a
 *      gitignored `secrets.properties` at the repo root as
 *      `twitch.client.secret=...` (or set the `TWITCH_CLIENT_SECRET` env var
 *      for CI). It is injected at build time via [TwitchSecrets] and is never
 *      committed — see `core/build.gradle.kts`.
 */
object TwitchConfig {
    const val CLIENT_ID = "0d3t2yyq8nxwbspxvxms81jcgq46l0"
    const val PLACEHOLDER_CLIENT_ID = "YOUR_CLIENT_ID_HERE"
    /**
     * Required by Twitch's /oauth2/token endpoint. Injected at build time from
     * `secrets.properties` / `TWITCH_CLIENT_SECRET` (never hardcoded here).
     */
    val CLIENT_SECRET = TwitchSecrets.CLIENT_SECRET
    const val PLACEHOLDER_CLIENT_SECRET = "PASTE_YOUR_CLIENT_SECRET_HERE"
    const val REDIRECT_URI_DESKTOP = "http://localhost:3000"
    const val REDIRECT_URI_MOBILE = "puretv-twitch://auth"
    const val SCOPES = "user:read:follows chat:read chat:edit"

    const val API_BASE = "https://api.twitch.tv/helix"
    const val AUTH_BASE = "https://id.twitch.tv/oauth2"
    const val IRC_ENDPOINT = "wss://irc-ws.chat.twitch.tv:443"
    const val GQL_ENDPOINT = "https://gql.twitch.tv/gql"

    /**
     * GQL client ID used by Twitch's own web client. It is not a secret —
     * it is sent by every browser session that loads twitch.tv — but it IS
     * the identifier Twitch uses to fingerprint non-browser clients, so it
     * is the first thing that may need rotating if requests start failing.
     */
    const val GQL_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"

    /**
     * Persisted-query hash for the PlaybackAccessToken GQL operation.
     * Twitch rotates this occasionally — see [com.puretv.twitch.core.stream.GqlHashProvider]
     * for the dynamic-refresh strategy that should back this constant up.
     */
    const val PLAYBACK_ACCESS_TOKEN_HASH =
        "0828119ded1c13477966434e15800ff57ddacf13ba1911c129dc2200705b0712"

    const val USHER_BASE = "https://usher.ttvnw.net/api/channel/hls"

    fun authorizeUrl(redirectUri: String, codeChallenge: String, state: String): String {
        // Fail at OAuth-start time rather than letting Twitch reject the request
        // with a generic "invalid client" the user can't act on. Both placeholders
        // are shipped intentionally so the project builds out of the box — but
        // neither must ever reach a real authorize URL.
        check(CLIENT_ID != PLACEHOLDER_CLIENT_ID) {
            "TwitchConfig.CLIENT_ID is still the placeholder \"$PLACEHOLDER_CLIENT_ID\". " +
                "Register an app at https://dev.twitch.tv/console and replace the constant in " +
                "core/src/commonMain/kotlin/com/puretv/twitch/core/api/TwitchConfig.kt before signing in."
        }
        check(CLIENT_SECRET != PLACEHOLDER_CLIENT_SECRET) {
            "TwitchConfig.CLIENT_SECRET is still the placeholder. Generate a secret at " +
                "https://dev.twitch.tv/console/apps → Manage → New Secret, then paste it into " +
                "core/src/commonMain/kotlin/com/puretv/twitch/core/api/TwitchConfig.kt. " +
                "Twitch requires client_secret for the authorization_code flow even with PKCE."
        }
        return "$AUTH_BASE/authorize" +
            "?client_id=$CLIENT_ID" +
            "&redirect_uri=$redirectUri" +
            "&response_type=code" +
            "&scope=${SCOPES.replace(" ", "+")}" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=S256" +
            "&state=$state"
    }
}
