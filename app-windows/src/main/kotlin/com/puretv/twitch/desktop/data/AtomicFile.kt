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
