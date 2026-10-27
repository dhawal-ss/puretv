package com.puretv.twitch.core.adblock

import com.puretv.twitch.core.CallCounter
import com.puretv.twitch.core.stream.StreamResolver
import com.puretv.twitch.core.stream.TwitchGqlClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaPlaylistCleanerTest {

    private val variantUrl = "https://video-weaver.example.hls.ttvnw.net/v1/playlist/primary.m3u8"

    /** A clock the hold loop can advance without real waiting. */
    private class FakeClock {
        var now = 1_000_000L
        val pause: suspend (Long) -> Unit = { now += it }
    }

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handler))

    private fun cleaner(
        http: HttpClient,
        engine: AdBlockEngine,
        clock: FakeClock = FakeClock(),
        hold: Boolean = true,
        lowLatency: Boolean = false,
    ) = MediaPlaylistCleaner(
        adBlockEngine = engine,
        backupStreamResolver = BackupStreamResolver(http, StreamResolver(http, TwitchGqlClient(http))),
        httpClient = http,
        nowMs = { clock.now },
        holdEmptyWindows = hold,
        pause = clock.pause,
        lowLatency = { lowLatency },
    )

    private fun engine() = AdBlockEngine(AdBlockConfig(), client { respond("", HttpStatusCode.OK) })

    @Test
    fun cleanPlaylistPassesThroughAndTurnsThePillGreen() = runTest {
        val engine = engine()
        val result = cleaner(client { respond("", HttpStatusCode.OK) }, engine)
            .clean(variantUrl, contentWindow(firstSequence = 100), channelLogin = null, quality = null)

        assertEquals(MediaPlaylistCleaner.Strategy.CLEAN, result.strategy)
        assertEquals(4, MediaPlaylistCleaner.segmentCount(result.content))
        assertEquals(AdBlockStatus.AD_BLOCKED, engine.status.value)
    }

    @Test
    fun allAdWindowIsHeldUntilContentAppearsInsteadOfReachingThePlayerEmpty() = runTest {
        val polls = CallCounter()
        val http = client {
            // First re-fetch: still all ad. Second: the stream is back.
            val n = polls.incrementAndGet()
            val body = if (n == 1) PrerollWindowTest.REAL_PREROLL_WINDOW else contentWindow(firstSequence = 2_963)
            respond(body, HttpStatusCode.OK)
        }
        val engine = engine()
        val clock = FakeClock()
        val result = cleaner(http, engine, clock)
            .clean(variantUrl, PrerollWindowTest.REAL_PREROLL_WINDOW, channelLogin = null, quality = null)

        assertTrue(MediaPlaylistCleaner.segmentCount(result.content) > 0, "the player must never get an empty window:\n${result.content}")
        assertFalse(AdMarkers.containsAds(result.content), "holding must never hand over an ad")
        assertEquals(2, polls.get())
        assertEquals(4_000, result.heldMs, "two 2s polls")
    }

    @Test
    fun withoutHoldingTheLegacyEmptyWindowIsReturned() = runTest {
        val result = cleaner(client { respond("", HttpStatusCode.OK) }, engine(), hold = false)
            .clean(variantUrl, PrerollWindowTest.REAL_PREROLL_WINDOW, channelLogin = null, quality = null)

        assertEquals(MediaPlaylistCleaner.Strategy.STRIP, result.strategy)
        assertEquals(0, MediaPlaylistCleaner.segmentCount(result.content))
        assertEquals(0, result.heldMs)
    }

    @Test
    fun holdGivesUpAfterItsLimitSoAStuckAdCannotHangTheLoaderForever() = runTest {
        val polls = CallCounter()
        val http = client {
            polls.incrementAndGet()
            respond(PrerollWindowTest.REAL_PREROLL_WINDOW, HttpStatusCode.OK)
        }
        val engine = engine()
        val result = cleaner(http, engine).clean(variantUrl, PrerollWindowTest.REAL_PREROLL_WINDOW, null, null)

        assertEquals(0, MediaPlaylistCleaner.segmentCount(result.content))
        assertEquals(20, polls.get(), "40s limit at one poll every 2s")
        assertEquals(AdBlockStatus.AD_FILTERED, engine.status.value, "the pill says an ad is being skipped, not that all is well")
    }

    @Test
    fun holdEndsAsSoonAsThePlayerCancelsTheRequest() = runTest {
        // Polls are strictly one after another, so a plain counter is safe here.
        var polls = 0
        val http = client {
            polls++
            respond(PrerollWindowTest.REAL_PREROLL_WINDOW, HttpStatusCode.OK)
        }
        val clock = FakeClock()
        val result = cleaner(http, engine(), clock).clean(
            variantUrl,
            PrerollWindowTest.REAL_PREROLL_WINDOW,
            channelLogin = null,
            quality = null,
            isCancelled = { polls >= 2 },
        )

        assertEquals(2, polls)
        assertEquals(0, MediaPlaylistCleaner.segmentCount(result.content))
    }

    @Test
    fun holdStopsWhenTheVariantIsGone() = runTest {
        val http = client { respond("", HttpStatusCode.NotFound) }
        val result = cleaner(http, engine()).clean(variantUrl, PrerollWindowTest.REAL_PREROLL_WINDOW, null, null)

        assertEquals(2_000, result.heldMs, "one poll, then give the player its error")
    }

    @Test
    fun lowLatencyPromotesPrefetchHintsOnlyForASingleRendition() = runTest {
        val raw = contentWindow(firstSequence = 100) + "#EXT-X-TWITCH-PREFETCH:https://cdn.invalid/v1/segment/content-104\n"
        val http = client { respond("", HttpStatusCode.OK) }

        val single = cleaner(http, engine(), lowLatency = true)
            .clean(variantUrl, raw, channelLogin = null, quality = null, singleRendition = true)
        assertEquals(5, MediaPlaylistCleaner.segmentCount(single.content))
        assertFalse("#EXT-X-TWITCH-PREFETCH" in single.content)
        assertTrue("#EXT-X-TARGETDURATION:2" in single.content, single.content)

        // A player choosing quality by download speed would read a segment still
        // being written as a slow link, so it only gets the faster refresh.
        val auto = cleaner(http, engine(), lowLatency = true)
            .clean(variantUrl, raw, channelLogin = null, quality = null, singleRendition = false)
        assertEquals(4, MediaPlaylistCleaner.segmentCount(auto.content))
        assertTrue("#EXT-X-TARGETDURATION:2" in auto.content, auto.content)

        val off = cleaner(http, engine(), lowLatency = false)
            .clean(variantUrl, raw, channelLogin = null, quality = null, singleRendition = true)
        assertEquals(4, MediaPlaylistCleaner.segmentCount(off.content))
        assertTrue("#EXT-X-TARGETDURATION:5" in off.content, off.content)
    }

    @Test
    fun aStrippedAdWindowNeverKeepsPrefetchHints() = runTest {
        // Ads, no backup to swap to: the pod is stripped, and the hints (which can
        // be ad segments) go too, even with low latency on. Twitch's own target
        // duration stays, so the player's stuck-playlist timeout is unchanged.
        val raw = PrerollWindowTest.REAL_PREROLL_WINDOW + "\n#EXT-X-TWITCH-PREFETCH:https://cdn.invalid/v1/segment/next\n"
        val result = cleaner(client { respond("", HttpStatusCode.OK) }, engine(), hold = false, lowLatency = true)
            .clean(variantUrl, raw, channelLogin = null, quality = null, singleRendition = true)
        assertEquals(MediaPlaylistCleaner.Strategy.STRIP, result.strategy)
        assertFalse("#EXT-X-TWITCH-PREFETCH" in result.content)
        assertFalse("cdn.invalid/v1/segment/next" in result.content)
        val declared = raw.lines().first { it.startsWith("#EXT-X-TARGETDURATION:") }
        assertTrue(declared in result.content, result.content)
    }

    @Test
    fun adWindowSwapsToACleanBackupAtTheSameQuality() = runTest {
        // Real time: the backup timeout must not fire on runTest's virtual clock
        // while the mock engine answers on its own thread.
        withContext(Dispatchers.Default) {
            val http = client { request ->
                val url = request.url.toString()
                when {
                    request.url.host.contains("gql") ->
                        respond(TOKEN_JSON, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    "usher" in url -> respond(BACKUP_MASTER, HttpStatusCode.OK)
                    "backup-720" in url -> respond(contentWindow(firstSequence = 7_000, liveSequence = 7_000), HttpStatusCode.OK)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val engine = engine()
            val result = cleaner(http, engine).clean(
                variantUrl,
                PrerollWindowTest.REAL_PREROLL_WINDOW,
                channelLogin = "somechannel",
                quality = MediaPlaylistCleaner.VariantQuality("1280x720", 60.0),
            )

            assertEquals(MediaPlaylistCleaner.Strategy.BACKUP, result.strategy)
            assertEquals("embed", result.backupPlayerType)
            assertFalse(AdMarkers.containsAds(result.content))
            assertEquals(AdBlockStatus.AD_BLOCKED, engine.status.value)
        }
    }

    @Test
    fun aBackupThatAnswersWithAnErrorIsNotServedAsAdFree() = runTest {
        // An expired backup session answers 403 with a short error body: no ad markers,
        // but not a playlist either. The ad window is stripped instead of swapped for it.
        withContext(Dispatchers.Default) {
            val http = client { request ->
                val url = request.url.toString()
                when {
                    request.url.host.contains("gql") ->
                        respond(TOKEN_JSON, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    "usher" in url -> respond(BACKUP_MASTER, HttpStatusCode.OK)
                    "backup-720" in url -> respond("""[{"error":"Forbidden"}]""", HttpStatusCode.Forbidden)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val result = cleaner(http, engine()).clean(
                variantUrl,
                PrerollWindowTest.REAL_PREROLL_WINDOW,
                channelLogin = "somechannel",
                quality = MediaPlaylistCleaner.VariantQuality("1280x720", 60.0),
            )

            assertEquals(MediaPlaylistCleaner.Strategy.STRIP, result.strategy)
            assertTrue(result.content.trimStart().startsWith("#EXTM3U"), result.content)
        }
    }

    @Test
    fun backupSessionNumberingIsMappedOntoTheLiveSequence() = runTest {
        // Real time: the backup timeout must not fire on runTest's virtual clock
        // while the mock engine answers on its own thread.
        withContext(Dispatchers.Default) {
            val http = client { request ->
                val url = request.url.toString()
                when {
                    request.url.host.contains("gql") ->
                        respond(TOKEN_JSON, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    "usher" in url -> respond(BACKUP_MASTER, HttpStatusCode.OK)
                    // A backup session counts from 3 while the broadcast is at 7,000.
                    "backup-720" in url -> respond(contentWindow(firstSequence = 3, liveSequence = 7_000), HttpStatusCode.OK)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val result = cleaner(http, engine()).clean(
                variantUrl,
                PrerollWindowTest.REAL_PREROLL_WINDOW,
                channelLogin = "somechannel",
                quality = MediaPlaylistCleaner.VariantQuality("1280x720", 60.0),
            )

            assertTrue(result.sequenceNormalized)
            assertTrue("#EXT-X-MEDIA-SEQUENCE:7000" in result.content, result.content)
        }
    }

    private companion object {
        const val TOKEN_JSON = """{"data":{"streamPlaybackAccessToken":{"value":"{}","signature":"sig"}}}"""

        val BACKUP_MASTER = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=1920x1080,VIDEO="chunked",FRAME-RATE=60.000
            https://video-weaver.example.hls.ttvnw.net/v1/playlist/backup-1080.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720,VIDEO="720p60",FRAME-RATE=60.000
            https://video-weaver.example.hls.ttvnw.net/v1/playlist/backup-720.m3u8
        """.trimIndent()

        fun contentWindow(firstSequence: Long, liveSequence: Long? = null): String = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:5")
            appendLine("#EXT-X-MEDIA-SEQUENCE:$firstSequence")
            if (liveSequence != null) appendLine("#EXT-X-TWITCH-LIVE-SEQUENCE:$liveSequence")
            for (i in 0 until 4) {
                appendLine("#EXTINF:2.000,live")
                appendLine("https://cdn.invalid/v1/segment/content-${firstSequence + i}")
            }
        }
    }
}
