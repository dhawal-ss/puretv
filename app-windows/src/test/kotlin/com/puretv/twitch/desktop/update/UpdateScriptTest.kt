package com.puretv.twitch.desktop.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the self-update script generation. Pins two behaviours:
 *  - no visible console (ping-based wait, no `timeout`; hidden .vbs launcher), and
 *  - the install runs ONLY after the app process has exited: the script polls the
 *    app PID and force-closes it if it lingers, so the silent in-place upgrade
 *    never runs against locked files (which corrupted the launcher .cfg).
 */
class UpdateScriptTest {

    @Test fun batWaitsWithPingNotTimeout() {
        val s = buildUpdateScripts(
            installerPath = "C:\\tmp\\PureTV-Setup-1.4.0.exe",
            isMsi = false,
            appExePath = "C:\\app\\PureTV for Twitch.exe",
            batPath = "C:\\tmp\\PureTV-update\\apply-update.bat",
            appPid = 4242L,
        )
        assertTrue(s.bat.contains("ping 127.0.0.1 -n 3 >nul"), s.bat)
        assertFalse(s.bat.contains("timeout"), s.bat)
        assertTrue(s.bat.contains("\"C:\\tmp\\PureTV-Setup-1.4.0.exe\" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART"), s.bat)
        assertTrue(s.bat.contains("\"C:\\app\\PureTV for Twitch.exe\""), s.bat)
        assertTrue(s.bat.trimEnd().endsWith("exit /b 0"), s.bat)
    }

    @Test fun batWaitsForAppPidThenForceClosesIt() {
        val s = buildUpdateScripts(
            installerPath = "C:\\tmp\\x.exe",
            isMsi = false,
            appExePath = "C:\\app\\PureTV for Twitch.exe",
            batPath = "C:\\tmp\\b.bat",
            appPid = 4242L,
        )
        // The PID is captured, polled, and force-closed as a fallback.
        assertTrue(s.bat.contains("set \"APPPID=4242\""), s.bat)
        assertTrue(s.bat.contains("tasklist /fi \"PID eq %APPPID%\""), s.bat)
        assertTrue(s.bat.contains("taskkill /f /pid %APPPID%"), s.bat)
        // No tree-kill: /T would kill this very script (a descendant of the app).
        assertFalse(s.bat.contains("/t "), s.bat.lowercase())
        // The wait/kill block precedes the installer invocation.
        assertTrue(s.bat.indexOf("APPPID") < s.bat.indexOf("VERYSILENT"), s.bat)
    }

    @Test fun vbsRunsBatHiddenAndDetached() {
        val s = buildUpdateScripts(
            installerPath = "C:\\tmp\\x.exe",
            isMsi = false,
            appExePath = null,
            batPath = "C:\\tmp\\apply-update.bat",
            appPid = 1L,
        )
        // Run(cmd, 0, False): window style 0 = hidden, False = don't wait.
        assertTrue(s.vbs.contains("WScript.Shell"), s.vbs)
        assertTrue(s.vbs.contains("apply-update.bat"), s.vbs)
        assertTrue(s.vbs.contains(", 0, False"), s.vbs)
    }

    @Test fun msiUsesMsiexec() {
        val s = buildUpdateScripts(
            installerPath = "C:\\tmp\\x.msi",
            isMsi = true,
            appExePath = null,
            batPath = "C:\\tmp\\b.bat",
            appPid = 1L,
        )
        assertTrue(s.bat.contains("msiexec /i \"C:\\tmp\\x.msi\" /passive /norestart"), s.bat)
    }

    @Test fun noRelaunchLineWhenExeNull() {
        val s = buildUpdateScripts(
            installerPath = "C:\\tmp\\x.exe",
            isMsi = false,
            appExePath = null,
            batPath = "C:\\tmp\\b.bat",
            appPid = 1L,
        )
        assertFalse(s.bat.contains("start \"\""), s.bat)
    }
}
