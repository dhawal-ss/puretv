package com.puretv.twitch.desktop.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.Window

/**
 * SECTION 08.5 — native Win32 window-chrome integration.
 *
 * Our top-level window is `undecorated = true` so we can paint a custom title
 * bar (see `App.CustomTitleBar`). The cost of dropping the native non-client
 * area is that Windows' window-management gestures — Aero Snap (drag-to-top to
 * maximize, drag-to-edge to half-tile), Win11 Snap Layouts, and Aero Shake —
 * are wired to the *native* title-bar drag, which no longer exists.
 *
 * Repositioning the frame manually with `Window.setLocation` never tells the OS
 * a move is in progress, so the DWM snap engine never engages. The canonical
 * Win32 fix is to hand the drag back to the OS: release our mouse capture and
 * post a non-client left-button-down on the caption. `DefWindowProc` then runs
 * the standard modal move loop — with all snap behavior included — exactly as
 * if the user had grabbed a real title bar.
 *
 *   ReleaseCapture();
 *   SendMessage(hwnd, WM_NCLBUTTONDOWN, HTCAPTION, 0);
 *
 * JNA (already on the classpath via VLCJ) gives us `user32.dll` and the HWND of
 * the AWT window. The SendMessage call is *blocking* — it runs the OS move loop
 * inline on the EDT and returns only when the user releases the mouse, by which
 * point the window has been moved/snapped natively.
 */
object WindowsNative {

    private const val WM_NCLBUTTONDOWN = 0x00A1
    private const val HTCAPTION = 0x2

    private val isWindows: Boolean =
        System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)

    /**
     * `ReleaseCapture` isn't declared on this JNA version's `User32` interface,
     * so we map the single export we need directly from user32.dll. Lazily loaded
     * so non-Windows hosts (or a missing native lib) never trigger the bind.
     */
    private interface User32Ext : Library {
        fun ReleaseCapture(): Boolean
    }

    private val user32Ext: User32Ext? by lazy {
        runCatching { Native.load("user32", User32Ext::class.java) }.getOrNull()
    }

    /**
     * Initiates an OS-driven window move for [window]. Call this on mouse-press
     * inside the custom title bar's drag region; the OS takes over until release,
     * providing full Aero Snap / Snap Layouts support.
     *
     * @return true if the native move loop was started; false on non-Windows or
     *   if the native call failed (caller should fall back to manual dragging).
     */
    fun startWindowDrag(window: Window): Boolean {
        if (!isWindows) return false
        return runCatching {
            val pointer = Native.getWindowPointer(window) ?: return false
            val hwnd = WinDef.HWND(pointer)
            user32Ext?.ReleaseCapture()
            User32.INSTANCE.SendMessage(
                hwnd,
                WM_NCLBUTTONDOWN,
                WinDef.WPARAM(HTCAPTION.toLong()),
                WinDef.LPARAM(0),
            )
            true
        }.getOrDefault(false)
    }
}
