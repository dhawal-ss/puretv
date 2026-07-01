package com.puretv.twitch.desktop.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color as ComposeColor
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * SECTION 08.4 [CRITICAL] — bridges VLCJ's AWT-based video surface into
 * Compose Desktop via `SwingPanel`.
 *
 * WHY A BRIDGE IS NEEDED: Compose Desktop renders through Skia, not AWT/Swing
 * — but `libvlc`'s embedded video output needs a native window handle, which
 * only AWT/Swing components expose on the JVM. `SwingPanel` punches a "hole"
 * in the Compose layer for an arbitrary `java.awt.Component`.
 *
 * WHY THE JPANEL WRAPPER: AWT Canvas is a heavyweight component; mixing it
 * directly with Compose's `SwingPanel` (which lives under a `JLayeredPane`)
 * causes z-order and sizing weirdness on Windows. Wrapping the Canvas in a
 * JPanel(BorderLayout) gives it a stable Swing parent and ensures predictable
 * resize behavior.
 *
 * WHY THE HIERARCHY LISTENER: libvlc binds to a Canvas's native window handle
 * (HWND on Windows) via `Component.addNotify()` — which only runs AFTER the
 * Canvas is added to a displayable container. Calling `attachToPanel` before
 * that produces a 0-handle binding and a silently-white video surface (the
 * exact symptom we shipped before this fix). HierarchyListener fires when
 * `DISPLAYABILITY_CHANGED`, which is the canonical signal that addNotify ran.
 *
 * THREADING: Compose runs `SwingPanel.factory` on the EDT, so component
 * construction is safe. HierarchyListener also fires on the EDT.
 *
 * INPUT (SECTION 08.4) — a heavyweight Canvas is a native window that paints
 * above Skia and swallows mouse + keyboard input before Compose sees it. In
 * fullscreen the Canvas fills the whole window, so without intervention the user
 * is trapped: mouse-moves never reach Compose (controls never reveal) and key
 * focus sits on the Canvas (F/Esc dead). Two fixes:
 *   1. `isFocusable = false` — the Canvas can never hold keyboard focus, so it
 *      stays with Compose and the F/Esc shortcuts keep working.
 *   2. a [MouseMotionAdapter] on the Canvas forwards genuine cursor movement
 *      back via [onUserActivity], so the controls can be revealed.
 *
 * @param onUserActivity invoked on real cursor movement over the video surface
 *   (used to un-hide the auto-hiding player controls).
 */
@Composable
fun VlcPlayerView(
    vlcPlayer: DesktopPlayer,
    modifier: Modifier = Modifier,
    onUserActivity: () -> Unit = {},
) {
    // Override removeNotify so the backend releases this surface BEFORE the native
    // peer (HWND) is destroyed. mpv binds its `wid` once and keeps rendering into
    // that HWND on a native VO thread; when Compose unmounts the stream screen
    // (e.g. tab change) the HWND is torn down — and rendering into a dead window
    // wedges the GPU/render path, freezing the whole window. removeNotify runs on
    // the EDT immediately before peer disposal, so detaching here stops mpv in
    // time. For VLC this is a no-op (it tolerates re-attach).
    val canvas = remember {
        object : Canvas() {
            override fun removeNotify() {
                vlcPlayer.detachFromPanel()
                super.removeNotify()
            }
        }.apply { background = Color.BLACK; isFocusable = false }
    }
    val panel = remember {
        JPanel(BorderLayout()).apply {
            background = Color.BLACK
            // Neither the Canvas (above) nor its Swing wrapper may accept keyboard
            // focus. If AWT ever routes focus into this interop hierarchy, Compose's
            // Skia layer stops receiving keys — the mechanism behind the old
            // "stuck in fullscreen, F/Esc dead" bug. The window-level
            // KeyEventDispatcher in StreamContent is the primary guard; this is
            // belt-and-suspenders so focus never lands here in the first place.
            isFocusable = false
            add(canvas, BorderLayout.CENTER)
        }
    }

    val currentActivity by rememberUpdatedState(onUserActivity)
    DisposableEffect(canvas) {
        // Compare against SCREEN coordinates: when revealing controls resizes the
        // Canvas under a stationary cursor, component-relative coords shift and
        // would fire a phantom "move", re-triggering the controls in a flicker
        // loop. Screen coords stay put for a physically still cursor.
        var lastX = Int.MIN_VALUE
        var lastY = Int.MIN_VALUE
        val motionListener = object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) = report(e)
            override fun mouseDragged(e: MouseEvent) = report(e)
            private fun report(e: MouseEvent) {
                if (e.xOnScreen != lastX || e.yOnScreen != lastY) {
                    lastX = e.xOnScreen
                    lastY = e.yOnScreen
                    currentActivity()
                }
            }
        }
        canvas.addMouseMotionListener(motionListener)
        onDispose { canvas.removeMouseMotionListener(motionListener) }
    }

    DisposableEffect(vlcPlayer, canvas) {
        val listener = HierarchyListener { e ->
            val flags = e.changeFlags
            if (flags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() != 0L && canvas.isDisplayable) {
                vlcPlayer.attachToPanel(canvas)
            }
        }
        canvas.addHierarchyListener(listener)
        // If the canvas is already displayable when this DisposableEffect runs
        // (can happen on recomposition), attach immediately — the listener
        // won't fire because nothing has changed.
        if (canvas.isDisplayable) {
            SwingUtilities.invokeLater { vlcPlayer.attachToPanel(canvas) }
        }
        onDispose {
            canvas.removeHierarchyListener(listener)
            // The player backend is a Koin singleton — released at app shutdown, not
            // per-screen. But its SURFACE must be released now: belt-and-suspenders
            // alongside the Canvas's removeNotify, in case this composable leaves
            // without removeNotify firing. Idempotent — detach no-ops if already done.
            vlcPlayer.detachFromPanel()
        }
    }

    SwingPanel(
        // CMP 1.7's SwingPanel defaults `background` to Color.White, which Compose
        // paints into the interop "hole" whenever the heavyweight Canvas doesn't
        // fully/promptly cover it — before VLC's first frame, during resize, and
        // during z-order handoff. That is the literal white blank square. The
        // Canvas + JPanel are already BLACK; this is the one background Compose
        // itself paints, so it must be black too.
        background = ComposeColor.Black,
        modifier = modifier,
        factory = { panel },
    )
}
