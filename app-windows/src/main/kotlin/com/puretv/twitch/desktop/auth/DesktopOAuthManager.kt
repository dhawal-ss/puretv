package com.puretv.twitch.desktop.auth

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop
import java.net.BindException
import java.net.URI

/**
 * SECTION 03.2 / 10 — desktop OAuth Authorization Code + PKCE flow.
 *
 * Mobile apps capture the `puretv-twitch://auth` redirect via an Android
 * intent filter; a desktop JVM app has no equivalent custom-URI-scheme
 * registration story that doesn't involve registry edits / installer hooks
 * (Section 14 Gotcha #5). Instead we do what most desktop OAuth clients do:
 *
 *   1. Spin up a one-shot embedded HTTP server on `http://localhost:3000`
 *      (== [com.puretv.twitch.core.api.TwitchConfig.REDIRECT_URI_DESKTOP]).
 *   2. Open the Twitch authorize URL in the user's system browser via
 *      `Desktop.getDesktop().browse(uri)`.
 *   3. Twitch redirects back to `localhost:3000/?code=...&state=...`; our
 *      server captures the query params, shows a friendly "you can close
 *      this tab" page, resolves the [CompletableDeferred], and shuts down.
 *
 * [awaitRedirect] is what [com.puretv.twitch.desktop.ui.LoginViewModel] calls
 * after opening the browser — it suspends until the redirect lands or a
 * 2-minute timeout elapses (covering slow logins / 2FA prompts).
 */
class DesktopOAuthManager {

    companion object {
        private const val PORT = 3000
        private const val TIMEOUT_MS = 120_000L
    }

    data class RedirectResult(val code: String, val state: String)

    /** Thrown when the OAuth redirect port (3000) is already bound by another process. */
    class PortInUseException(port: Int, cause: Throwable) :
        IllegalStateException(
            "Port $port is required by the Twitch sign-in redirect but is already in use. " +
                "Stop the process holding it (often a local web/dev server) and try again.",
            cause,
        )

    /**
     * Opens [authorizeUrl] in the system browser and starts a temporary
     * localhost server to capture the redirect. Returns the captured
     * `code`/`state`, or `null` on timeout / cancellation / browse failure.
     *
     * Throws [PortInUseException] if port 3000 is taken — the redirect URI is
     * fixed by the Twitch app registration, so the user must free the port
     * rather than us picking a different one.
     */
    suspend fun launchAndAwaitRedirect(authorizeUrl: String): RedirectResult? {
        val deferred = CompletableDeferred<RedirectResult?>()
        // Ktor 3 changed `embeddedServer { }.start()` to return EmbeddedServer
        // (parameterized over the engine type) instead of the older
        // ApplicationEngine — they're different superhierarchies.
        var engine: EmbeddedServer<*, *>? = null

        engine = try {
            embeddedServer(Netty, port = PORT, host = "127.0.0.1") {
                routing {
                    get("/") {
                    val code = call.request.queryParameters["code"]
                    val state = call.request.queryParameters["state"]
                    val error = call.request.queryParameters["error"]

                    val (title, body) = when {
                        code != null && state != null -> "Signed in" to
                            "You're signed in to PureTV for Twitch. You can close this tab and return to the app."
                        error != null -> "Sign-in cancelled" to
                            "Twitch reported: $error. You can close this tab and try again in the app."
                        else -> "Something went wrong" to
                            "No authorization code was returned. You can close this tab and try again in the app."
                    }
                    call.respondText(
                        contentType = ContentType.Text.Html,
                        text = """
                            <!DOCTYPE html>
                            <html><head><meta charset="utf-8"><title>$title — PureTV for Twitch</title>
                            <style>
                              body { font-family: -apple-system, Segoe UI, sans-serif; background:#0A0A0F; color:#E8E8F0;
                                     display:flex; align-items:center; justify-content:center; height:100vh; margin:0; }
                              .card { max-width: 420px; text-align:center; padding: 32px; }
                              h1 { color:#9B5DE5; }
                            </style></head>
                            <body><div class="card"><h1>$title</h1><p>$body</p></div></body></html>
                        """.trimIndent(),
                    )

                    if (code != null && state != null) {
                        deferred.complete(RedirectResult(code, state))
                    } else if (!deferred.isCompleted) {
                        deferred.complete(null)
                    }
                }
            }
            }.start(wait = false)
        } catch (e: BindException) {
            throw PortInUseException(PORT, e)
        } catch (e: java.net.SocketException) {
            // Netty wraps the underlying BindException on some platforms.
            if (e.message?.contains("Address already in use", ignoreCase = true) == true) {
                throw PortInUseException(PORT, e)
            }
            throw e
        }

        return runCatching {
            // Open AFTER the server is listening so a fast click can't race a 404.
            openInSystemBrowser(authorizeUrl)
            withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
        }.getOrNull().also {
            runCatching { engine?.stop(gracePeriodMillis = 100, timeoutMillis = 500) }
        }
    }

    private fun openInSystemBrowser(url: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            // Headless / unsupported environment fallback (Section 14 Gotcha #5):
            // surface the URL so the user can copy-paste it manually.
            println("Open this URL to sign in to Twitch: $url")
        }
    }
}
