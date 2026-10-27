package com.puretv.twitch.desktop.player

import com.puretv.twitch.desktop.platform.HostOs
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeLibsTest {
    private fun resources(vararg files: String): File {
        val root = Files.createTempDirectory("res").toFile()
        files.forEach { File(root, it).apply { parentFile.mkdirs(); writeText("x") } }
        return root
    }

    @Test
    fun `windows finds the bundled dll folder`() {
        val res = resources("vlc/libvlc.dll", "vlc/plugins/codec/a.dll")
        assertEquals(File(res, "vlc"), NativeLibs.bundledVlcLibDir(res, HostOs.WINDOWS))
    }

    @Test
    fun `macos finds the VLC app layout with plugins beside lib`() {
        val res = resources("vlc/lib/libvlc.dylib", "vlc/lib/libvlccore.dylib", "vlc/plugins/libavcodec_plugin.dylib")
        assertEquals(File(res, "vlc/lib"), NativeLibs.bundledVlcLibDir(res, HostOs.MACOS))
    }

    @Test
    fun `a bundle for another os is not mistaken for ours`() {
        val res = resources("vlc/libvlc.dll")
        assertNull(NativeLibs.bundledVlcLibDir(res, HostOs.MACOS))
    }

    @Test
    fun `linux always uses the distro libVLC`() {
        val res = resources("vlc/libvlc.dll", "vlc/lib/libvlc.dylib")
        assertNull(NativeLibs.bundledVlcLibDir(res, HostOs.LINUX))
    }

    @Test
    fun `a windows bundle without a plugin cache asks libVLC to write one`() {
        val res = resources("vlc/libvlc.dll", "vlc/plugins/codec/a.dll")
        assertEquals(listOf("--reset-plugins-cache"), NativeLibs.vlcPluginCacheArgs(File(res, "vlc"), HostOs.WINDOWS))
    }

    @Test
    fun `a windows bundle with its plugin cache uses it`() {
        val res = resources("vlc/libvlc.dll", "vlc/plugins/plugins.dat")
        assertEquals(emptyList(), NativeLibs.vlcPluginCacheArgs(File(res, "vlc"), HostOs.WINDOWS))
    }

    @Test
    fun `the plugin cache is never written on macOS or for a system VLC`() {
        val res = resources("vlc/lib/libvlc.dylib", "vlc/plugins/a.dylib")
        assertEquals(emptyList(), NativeLibs.vlcPluginCacheArgs(File(res, "vlc/lib"), HostOs.MACOS))
        assertEquals(emptyList(), NativeLibs.vlcPluginCacheArgs(null, HostOs.WINDOWS))
    }

    @Test
    fun `mpv is offered everywhere but macOS`() {
        assertTrue(NativeLibs.mpvSupported(HostOs.WINDOWS))
        assertTrue(NativeLibs.mpvSupported(HostOs.LINUX))
        assertFalse(NativeLibs.mpvSupported(HostOs.MACOS))
    }

    @Test
    fun `linux tries the runtime soname after the dev symlink`() {
        assertEquals(listOf("mpv", "libmpv.so.2", "libmpv.so.1"), NativeLibs.mpvLoadNames(HostOs.LINUX))
        assertEquals(listOf("libmpv-2"), NativeLibs.mpvLoadNames(HostOs.WINDOWS))
    }

    @Test
    fun `only linux falls back to software video in mpv`() {
        assertEquals("gpu-next,gpu,x11", NativeLibs.mpvVideoOutputs(HostOs.LINUX))
        assertEquals("gpu-next,gpu", NativeLibs.mpvVideoOutputs(HostOs.WINDOWS))
    }

    @Test
    fun `windows tiles keep DirectSound and linux tiles get their own pulse stream`() {
        assertEquals("directsound", NativeLibs.tileAudioOutput(HostOs.WINDOWS))
        assertEquals("pulse", NativeLibs.tileAudioOutput(HostOs.LINUX))
        assertNull(NativeLibs.tileAudioOutput(HostOs.MACOS))
    }

    @Test
    fun `linux is told to install VLC from the package manager`() {
        assertTrue("apt install vlc" in NativeLibs.vlcMissingMessage(packaged = true, os = HostOs.LINUX))
        assertTrue("reinstall" in NativeLibs.vlcMissingMessage(packaged = true, os = HostOs.WINDOWS))
    }
}
