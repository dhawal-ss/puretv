package com.puretv.twitch.desktop.data

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Crash-safe local-file persistence shared by the desktop stores.
 *
 * Audit P0-2: every store previously persisted via `file.writeText(...)`, which
 * opens the LIVE file with `TRUNCATE_EXISTING` — the file is emptied, then the
 * bytes stream in. A crash, power-loss, OOM-kill, or forced-quit *during* that
 * window leaves a truncated, unparseable file; the lenient loaders
 * (`runCatching { decode }.getOrElse { empty }`) then silently reset the whole
 * store to empty, i.e. total loss of the user's follows / history / progress /
 * tokens with no visible error.
 *
 * [writeTextAtomically] / [writeBytesAtomically] write to a sibling temp file,
 * fsync it to stable storage, then atomically rename it over the target. A crash
 * leaves either the intact old file (rename not yet committed) or the complete
 * new file (rename committed) — never a torn file. On Windows the rename maps to
 * `MoveFileEx(MOVEFILE_REPLACE_EXISTING)`, which is atomic on NTFS.
 */
internal object AtomicFile {

    fun writeTextAtomically(file: File, text: String) =
        writeBytesAtomically(file, text.toByteArray(StandardCharsets.UTF_8))

    /**
     * Move an unparseable file aside before the lenient loaders fall back to empty.
     *
     * Audit F3: the atomic writer makes a *torn* file unlikely, but a file can still be
     * unreadable for other reasons (a half-written file from a pre-atomic app version
     * already on disk, a manual edit, bit-rot, or a future serialization-incompatible
     * change). When [decode] throws, the stores reset to empty AND the very next mutation
     * atomically overwrites the bad file — turning a *recoverable* corruption into
     * *permanent, silent* loss of the user's follows / history / progress.
     *
     * Renaming the bad bytes to a `<name>.corrupt-<epochMillis>` sidecar preserves them
     * for manual/automated recovery and stops the next write from destroying them. The
     * sidecar name is made unique so a same-millisecond second corruption can't clobber
     * an earlier quarantine. Best-effort: returns the sidecar path, or null if nothing
     * was moved (file absent, or the rename failed — in which case the old fallback of
     * resetting to empty still applies).
     */
    fun quarantineCorrupt(file: File): File? {
        if (!file.exists()) return null
        val base = "${file.name}.corrupt-${System.currentTimeMillis()}"
        var dest = File(file.parentFile, base)
        var n = 1
        while (dest.exists()) dest = File(file.parentFile, "$base-${n++}")
        return runCatching {
            try {
                Files.move(file.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(file.toPath(), dest.toPath())
            }
            dest
        }.getOrNull()
    }

    fun writeBytesAtomically(file: File, bytes: ByteArray) {
        val dir = file.parentFile
        dir?.mkdirs()
        // Temp file MUST be on the same filesystem/volume as the target for the
        // rename to be atomic, so create it in the same directory.
        val tmp = File.createTempFile(file.name + ".", ".tmp", dir)
        try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                // Force OS buffers to disk before the rename so a crash can't
                // expose a not-yet-durable file. Best-effort: some filesystems
                // no-op fsync, which is acceptable — the rename is still atomic.
                runCatching { out.fd.sync() }
            }
            try {
                Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                // Rare (e.g. some network filesystems). Fall back to a
                // non-atomic replace — still far safer than truncate-in-place.
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            // Clean up the temp file if the move never consumed it (write threw,
            // or a fallback path left it behind).
            runCatching { if (tmp.exists()) tmp.delete() }
        }
    }
}
