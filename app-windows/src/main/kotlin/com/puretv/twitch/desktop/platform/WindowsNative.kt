package com.puretv.twitch.desktop.platform

import com.sun.jna.CallbackReference
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Window
import java.util.concurrent.ConcurrentHashMap

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

    // ── Win32 constants for borderless Aero-Snap (see enableBorderlessSnap) ──────
    private const val GWLP_WNDPROC = -4
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084

    // Window styles the DWM snap engine requires; undecorated AWT frames omit them.
    private const val WS_MAXIMIZE = 0x01000000
    private const val WS_THICKFRAME = 0x00040000
    private const val WS_MINIMIZEBOX = 0x00020000
    private const val WS_MAXIMIZEBOX = 0x00010000

    // SetWindowPos flags.
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020

    // GetSystemMetrics indices for the maximized-frame inset.
    private const val SM_CXFRAME = 32
    private const val SM_CYFRAME = 33
    private const val SM_CXPADDEDBORDER = 92

    // WM_NCHITTEST return codes.
    private const val HTLEFT = 10
    private const val HTRIGHT = 11
    private const val HTTOP = 12
    private const val HTTOPLEFT = 13
    private const val HTTOPRIGHT = 14
    private const val HTBOTTOM = 15
    private const val HTBOTTOMLEFT = 16
    private const val HTBOTTOMRIGHT = 17

    /** Width, in px, of the invisible resize grab band along each window edge. */
    private const val RESIZE_BORDER = 6

    /**
     * Installed window procedures, keyed by HWND value. Two jobs:
     *  - prevents double-subclassing if [enableBorderlessSnap] is called twice;
     *  - keeps a strong reference to each [WinUser.WindowProc] so the JVM can't
     *    GC the callback out from under the live native subclass (which would
     *    crash the EDT the next time Windows dispatched a message to it).
     */
    private val installedProcs = ConcurrentHashMap<Long, WinUser.WindowProc>()

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

    /**
     * Makes the undecorated top-level [window] participate in Windows' native
     * window management — Aero Snap (drag-to-edge tiling, drag-to-top maximize),
     * Win11 Snap Layouts, Win+Arrow keyboard snapping, and edge-drag resize —
     * while keeping the borderless, custom-title-bar look.
     *
     * Two native operations:
     *
     *  1. **Add the styles snap requires.** The DWM snap engine refuses to tile a
     *     window that lacks `WS_THICKFRAME` (resizable) and `WS_MAXIMIZEBOX`.
     *     `undecorated = true` strips both, which is why [startWindowDrag]'s move
     *     loop never actually snapped before — the gesture ran but the OS had
     *     nothing to snap.
     *
     *  2. **Subclass the window procedure** so adding `WS_THICKFRAME` doesn't ruin
     *     the look:
     *       - `WM_NCCALCSIZE` → 0 keeps the *client* area covering the entire
     *         frame, so the window stays visually borderless (no OS-drawn border)
     *         and AWT's zero-inset assumption stays correct. When maximized we
     *         inset by the frame metrics so the window doesn't bleed over the
     *         monitor edge and hide the taskbar.
     *       - `WM_NCHITTEST` re-adds the edge/corner resize regions that
     *         `WM_NCCALCSIZE → 0` would otherwise remove.
     *     Everything else is forwarded to the original proc.
     *
     * Idempotent and self-contained: any failure (non-Windows, missing HWND, JNA
     * fault) leaves the window exactly as it was. Call once, on the EDT, after the
     * window is realized.
     *
     * @return true if the native styling was applied (or already had been).
     */
    fun enableBorderlessSnap(window: Window): Boolean {
        if (!isWindows) return false
        return runCatching {
            val ptr = Native.getWindowPointer(window) ?: return false
            val key = Pointer.nativeValue(ptr)
            if (installedProcs.containsKey(key)) return true

            val hwnd = WinDef.HWND(ptr)
            val u = User32.INSTANCE

            // Capture the stock proc so our subclass can delegate to it. This JNA
            // build types CallWindowProc's prev-proc arg as Pointer, so wrap the
            // LONG_PTR address once.
            val originalPtr = Pointer(u.GetWindowLongPtr(hwnd, GWLP_WNDPROC).toLong())
            val proc = object : WinUser.WindowProc {
                override fun callback(
                    h: WinDef.HWND,
                    msg: Int,
                    wParam: WinDef.WPARAM,
                    lParam: WinDef.LPARAM,
                ): WinDef.LRESULT = runCatching {
                    when (msg) {
                        WM_NCCALCSIZE -> if (wParam.toInt() != 0) {
                            trimMaximizedClientRect(h, lParam)
                            WinDef.LRESULT(0L) // client area == window area → borderless
                        } else {
                            u.CallWindowProc(originalPtr, h, msg, wParam, lParam)
                        }
                        WM_NCHITTEST -> resizeHitTest(h, lParam)
                            ?: u.CallWindowProc(originalPtr, h, msg, wParam, lParam)
                        else -> u.CallWindowProc(originalPtr, h, msg, wParam, lParam)
                    }
                }.getOrElse {
                    // Never let a bug in our handling crash the EDT — fall back to
                    // the OS default behavior for this message.
                    u.CallWindowProc(originalPtr, h, msg, wParam, lParam)
                }
            }
            // Reference BEFORE install so it can't be GC'd between the two calls.
            installedProcs[key] = proc
            u.SetWindowLongPtr(hwnd, GWLP_WNDPROC, CallbackReference.getFunctionPointer(proc))

            // Add the snap-enabling styles, then force a frame recalculation so the
            // new WM_NCCALCSIZE handling takes effect immediately.
            val style = u.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            u.SetWindowLong(hwnd, WinUser.GWL_STYLE, style or WS_THICKFRAME or WS_MAXIMIZEBOX or WS_MINIMIZEBOX)
            u.SetWindowPos(
                hwnd, null, 0, 0, 0, 0,
                SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
            )
            true
        }.getOrDefault(false)
    }

    /**
     * When the window is maximized, shrinks the proposed client RECT (rgrc[0] of
     * the NCCALCSIZE_PARAMS that [lParam] points at) by the frame metrics. Without
     * this a borderless maximized window spills [SM_CXFRAME] px past every monitor
     * edge and covers the taskbar. No-op while not maximized.
     */
    private fun trimMaximizedClientRect(hwnd: WinDef.HWND, lParam: WinDef.LPARAM) {
        val u = User32.INSTANCE
        if (u.GetWindowLong(hwnd, WinUser.GWL_STYLE) and WS_MAXIMIZE == 0) return
        val x = u.GetSystemMetrics(SM_CXFRAME) + u.GetSystemMetrics(SM_CXPADDEDBORDER)
        val y = u.GetSystemMetrics(SM_CYFRAME) + u.GetSystemMetrics(SM_CXPADDEDBORDER)
        // RECT = { LONG left, top, right, bottom } → four 4-byte ints at 0,4,8,12.
        val p = Pointer(lParam.toLong())
        p.setInt(0L, p.getInt(0L) + x)
        p.setInt(4L, p.getInt(4L) + y)
        p.setInt(8L, p.getInt(8L) - x)
        p.setInt(12L, p.getInt(12L) - y)
    }

    /**
     * Maps a screen point (packed in [lParam]) to an edge/corner resize hit-test
     * code, or null when the cursor isn't over a [RESIZE_BORDER] band (so the
     * caller forwards to the default proc → HTCLIENT and Compose handles it).
     */
    private fun resizeHitTest(hwnd: WinDef.HWND, lParam: WinDef.LPARAM): WinDef.LRESULT? {
        val packed = lParam.toLong().toInt()
        val sx = (packed and 0xFFFF).toShort().toInt()
        val sy = (packed ushr 16 and 0xFFFF).toShort().toInt()
        val r = WinDef.RECT()
        if (!User32.INSTANCE.GetWindowRect(hwnd, r)) return null
        val onLeft = sx < r.left + RESIZE_BORDER
        val onRight = sx >= r.right - RESIZE_BORDER
        val onTop = sy < r.top + RESIZE_BORDER
        val onBottom = sy >= r.bottom - RESIZE_BORDER
        val code = when {
            onTop && onLeft -> HTTOPLEFT
            onTop && onRight -> HTTOPRIGHT
            onBottom && onLeft -> HTBOTTOMLEFT
            onBottom && onRight -> HTBOTTOMRIGHT
            onLeft -> HTLEFT
            onRight -> HTRIGHT
            onTop -> HTTOP
            onBottom -> HTBOTTOM
            else -> return null
        }
        return WinDef.LRESULT(code.toLong())
    }
}
