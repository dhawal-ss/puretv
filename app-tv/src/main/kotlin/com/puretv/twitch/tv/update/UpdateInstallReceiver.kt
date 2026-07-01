package com.puretv.twitch.tv.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.flow.MutableSharedFlow

/** Terminal outcome of a system-side install, surfaced back into [TvUpdateManager]. */
data class InstallResult(val success: Boolean, val message: String?)

/**
 * In-process bus so [UpdateInstallReceiver] (which fires outside any ViewModel /
 * Compose scope) can hand the install outcome to the [TvUpdateManager] singleton.
 */
object UpdateInstallBus {
    val events = MutableSharedFlow<InstallResult>(extraBufferCapacity = 4)
}

/**
 * Receives [PackageInstaller] session callbacks for our own update.
 *
 * The critical case is [PackageInstaller.STATUS_PENDING_USER_ACTION]: for a
 * sideloaded app the OS won't install silently — it returns a confirm Intent we
 * must launch (with NEW_TASK, since we're outside an Activity). Without this the
 * download would complete and then nothing would happen. Terminal statuses are
 * forwarded to [UpdateInstallBus] so the UI can show a failure.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { confirm?.let { context.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS ->
                UpdateInstallBus.events.tryEmit(InstallResult(success = true, message = null))
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Install failed (code $status)."
                UpdateInstallBus.events.tryEmit(InstallResult(success = false, message = message))
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.puretv.twitch.tv.UPDATE_INSTALL_STATUS"
    }
}
