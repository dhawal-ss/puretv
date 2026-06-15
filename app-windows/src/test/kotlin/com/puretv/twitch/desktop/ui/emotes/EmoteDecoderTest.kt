package com.puretv.twitch.desktop.ui.emotes

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertNull

class EmoteDecoderTest {
    // Standard 1x1 transparent PNG (single frame).
    private val onePxPng: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    )

    @Test fun singleFrameImageDecodesToNull() {
        assertNull(decodeAnimatedFrames(onePxPng))
    }

    @Test fun garbageBytesDecodeToNullNotThrow() {
        assertNull(decodeAnimatedFrames(byteArrayOf(1, 2, 3, 4)))
    }
}
