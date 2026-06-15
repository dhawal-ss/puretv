package com.puretv.twitch.desktop.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the self-update script generation. The previous launch left a visible
 * console window open; these tests pin the no-console behaviour: a ping-based
 * wait (no console-input dependency) and a hidden script launcher.
 */
class UpdateScriptTest {

    @Test fun batWaitsWithPingNotTimeout() {
        val s = buildUpdateScripts(
            installerPath = "C:\\tmp\\PureTV-Setup-1.4.0.exe",
            isMsi = false,
            appExePath = "C:\\app\\PureTV for Twitch.exe",
            batPath = "C:\\tmp\\PureTV-update\\apply-update.bat",
        )
        assertTrue(s.bat.contains("ping 127.0.0.1 -n 3 >nul"), s.bat)
        assertFalse(s.bat.contains("timeout"), s.bat)
        assertTrue(s.bat.contains("\"C:\\tmp\\PureTV-Setup-1.4.0.exe\" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART"), s.bat)
        assertTrue(s.bat.contains("\"C:\\app\\PureTV for Twitch.exe\""), s.bat)
        assertTrue(s.bat.trimEnd().endsWith("exit /b 0"), s.bat)
    }

    @Test fun vbsRunsBatHiddenAndDetached() {
        val s = buildUpdateScripts(
            installerPath = "C:\\tmp\\x.exe",
            isMsi = false,
            appExePath = null,
            batPath = "C:\\tmp\\apply-update.bat",
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
        )
        assertTrue(s.bat.contains("msiexec /i \"C:\\tmp\\x.msi\" /passive /norestart"), s.bat)
    }

    @Test fun noRelaunchLineWhenExeNull() {
        val s = buildUpdateScripts(
            installerPath = "C:\\tmp\\x.exe",
            isMsi = false,
            appExePath = null,
            batPath = "C:\\tmp\\b.bat",
        )
        assertFalse(s.bat.contains("start \"\""), s.bat)
    }
}
