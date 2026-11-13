package com.mutinymedia.android.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class NetworkTest {
    @Test fun acceptsHttpsAndTrimsWhitespace() {
        assertEquals("https://example.org/path", Network.validateHttps(" https://example.org/path "))
    }
    @Test fun rejectsInvalidAndInsecureUrls() {
        listOf("http://example.org", "https:///missing-host", "https://user:pass@example.org", "file:///tmp/test").forEach {
            assertTrue(it, runCatching { Network.validateHttps(it) }.isFailure)
        }
    }
    @Test fun responseLimitUsesBytesAndClosesStream() {
        var closed = false
        val input = object : ByteArrayInputStream(ByteArray(20)) {
            override fun close() { closed = true; super.close() }
        }
        assertTrue(runCatching { Network.readLimited(input, 10) }.isFailure)
        assertTrue(closed)
        assertEquals("hello", Network.readLimited(ByteArrayInputStream("hello".toByteArray()), 5))
    }
    @Test fun retriesOnlyTimeoutsAndOnlyOnce() {
        val timeout = java.net.SocketTimeoutException("read timed out")
        assertTrue(Network.shouldRetryTimeout(timeout, 0))
        assertFalse(Network.shouldRetryTimeout(timeout, 1))
        assertFalse(Network.shouldRetryTimeout(IllegalStateException("HTTP 404"), 0))
        assertEquals(10_000, Network.CONNECT_TIMEOUT_MS)
        assertEquals(16_000, Network.READ_TIMEOUT_MS)
    }
    @Test fun normalizesAddonManifestAndStremioLinks() {
        val client = StremioClient()
        assertEquals("https://example.org/config", client.normalizeBase(" stremio://example.org/config/manifest.json/ "))
        listOf("https://example.org/#fragment", "https://example.org?query=1", "https://user@example.org", "https:///bad").forEach {
            assertTrue(it, runCatching { client.normalizeBase(it) }.isFailure)
        }
    }
}
