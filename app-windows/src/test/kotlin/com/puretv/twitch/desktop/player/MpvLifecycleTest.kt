package com.puretv.twitch.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The mpv surface-lifecycle decision. mpv takes the native window handle as a
 * set-once `wid` before `mpv_initialize`, so a context is bound to exactly one
 * Canvas/HWND for its lifetime. When Compose tears down a stream screen the
 * Canvas (and its HWND) is destroyed; if a new screen brings a new Canvas, the
 * old context must be torn down and a fresh one bound — never left rendering
 * into a destroyed HWND (that wedges the GPU/render path and freezes the app).
 */
class MpvLifecycleTest {
    @Test fun attachesFreshWhenNotInitialized() {
        assertEquals(AttachAction.ATTACH, mpvAttachAction(initialized = false, sameSurface = false))
        // sameSurface is irrelevant before there is any context.
        assertEquals(AttachAction.ATTACH, mpvAttachAction(initialized = false, sameSurface = true))
    }

    @Test fun noopWhenAlreadyBoundToSameSurface() {
        assertEquals(AttachAction.NOOP, mpvAttachAction(initialized = true, sameSurface = true))
    }

    @Test fun reattachesWhenSurfaceChanged() {
        assertEquals(AttachAction.REATTACH, mpvAttachAction(initialized = true, sameSurface = false))
    }
}
