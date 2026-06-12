package com.puretv.twitch.core.api

import java.security.MessageDigest

actual object Sha256 {
    actual fun digest(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)
}
