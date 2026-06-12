package com.puretv.twitch.android.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.puretv.twitch.android.MainActivity
import org.koin.android.ext.android.inject

/**
 * SECTION 06.5 — foreground "media playback" service declared in
 * AndroidManifest (`android:foregroundServiceType="mediaPlayback"`).
 *
 * Wrapping the shared [TwitchPlayer]'s `ExoPlayer` in a [MediaSession] keeps
 * audio alive (and shows lockscreen/notification transport controls) when the
 * user backgrounds the app — whether that's plain background audio or PiP
 * (Section 6.5). The `ExoPlayer` instance itself is a Koin singleton shared
 * with [PlayerSurface], so starting/stopping this service never tears down or
 * recreates playback — it only attaches a session to the existing player.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private val twitchPlayer: TwitchPlayer by inject()
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, twitchPlayer.exoPlayer)
            .setSessionActivity(openAppIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Section 6.5 — once the player is paused/idle and the activity isn't in
     * front (e.g. user dismissed PiP), let the system tear the service down
     * rather than holding a phantom foreground notification open.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            // Release only the session wrapper — the underlying ExoPlayer is a
            // Koin singleton owned/released elsewhere (survives across PiP and
            // service restarts).
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
