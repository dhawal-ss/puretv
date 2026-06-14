package com.puretv.twitch.desktop.ui.theme

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the bundled font resources. [Fonts.kt] loads each weight via
 * `Font(resource = "fonts/…")`, which resolves on the classpath at RENDER time, not
 * at compile time — so a renamed/missing TTF compiles fine and only crashes when the
 * first frame draws. This test fails loudly in CI instead.
 *
 * The list must mirror exactly the paths referenced in Fonts.kt.
 */
class FontResourcesTest {

    private val bundledFonts = listOf(
        "fonts/BricolageGrotesque-SemiBold.ttf",
        "fonts/BricolageGrotesque-Bold.ttf",
        "fonts/BricolageGrotesque-ExtraBold.ttf",
        "fonts/Archivo-Light.ttf",
        "fonts/Archivo-Regular.ttf",
        "fonts/Archivo-Medium.ttf",
        "fonts/Archivo-SemiBold.ttf",
        "fonts/Archivo-Bold.ttf",
        "fonts/IBMPlexMono-Regular.ttf",
        "fonts/IBMPlexMono-Medium.ttf",
        "fonts/IBMPlexMono-SemiBold.ttf",
    )

    @Test
    fun allBundledFontsResolveOnClasspathAsValidTrueType() {
        for (path in bundledFonts) {
            val bytes = javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
            assertNotNull(bytes, "Missing bundled font resource: $path — Fonts.kt loads it via Font(resource = …)")
            assertTrue(bytes.size > 1_000, "Font $path is suspiciously small (${bytes.size} bytes)")
            // TrueType outline fonts start with the version tag 0x00010000.
            val magicOk = bytes.size >= 4 &&
                bytes[0].toInt() == 0x00 && bytes[1].toInt() == 0x01 &&
                bytes[2].toInt() == 0x00 && bytes[3].toInt() == 0x00
            assertTrue(magicOk, "Font $path is not a valid TrueType file (bad magic bytes)")
        }
    }
}
