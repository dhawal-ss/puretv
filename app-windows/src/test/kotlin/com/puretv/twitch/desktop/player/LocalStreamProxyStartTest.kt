package com.puretv.twitch.desktop.player

import java.io.IOException
import java.net.BindException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A port-7979 bind failure (a second PureTV window, or a leftover Netty from a
 * hard-killed process) must be recognized so [LocalStreamProxy.start] can turn it
 * into a clear, user-facing message instead of an opaque uncaught exception that
 * silently kills the stream ViewModel's init coroutine. Netty may wrap the
 * underlying [BindException] in its own exception, so the classifier walks the
 * cause chain.
 */
class LocalStreamProxyStartTest {

    @Test fun recognizesDirectBindException() {
        assertTrue(LocalStreamProxy.isAddressInUse(BindException("Address already in use: bind")))
    }

    @Test fun recognizesWrappedBindException() {
        val wrapped = RuntimeException("Failed to bind", IOException("boom", BindException("Address already in use")))
        assertTrue(LocalStreamProxy.isAddressInUse(wrapped))
    }

    @Test fun ignoresUnrelatedFailures() {
        assertFalse(LocalStreamProxy.isAddressInUse(IOException("connection reset")))
        assertFalse(LocalStreamProxy.isAddressInUse(RuntimeException("something else")))
    }
}
