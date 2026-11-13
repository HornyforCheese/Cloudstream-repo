package com.mutinymedia.android.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class RealDebridClientTest {
    @Test fun resolvesTorrentWhenSelectFilesReturnsNoContent() = runBlocking {
        val hash = "a".repeat(40)
        val steps = ArrayDeque(listOf(
            Reply("torrents/addMagnet", 201, """{"id":"fixture"}"""),
            Reply("torrents/info/fixture", 200, """{"files":[{"id":7,"bytes":20},{"id":9,"bytes":100}]}"""),
            Reply("torrents/selectFiles/fixture", 204, ""),
            Reply("torrents/info/fixture", 200, """{"status":"downloaded","links":["https://fixture.example/hoster"]}"""),
            Reply("unrestrict/link", 200, """{"download":"https://fixture.example/movie.mp4"}"""),
        ))
        val requests = mutableListOf<FixtureConnection>()
        val client = RealDebridClient { url ->
            val reply = steps.removeFirst()
            assertEquals("/rest/1.0/${reply.path}", url.path)
            FixtureConnection(url, reply).also(requests::add)
        }

        assertEquals("https://fixture.example/movie.mp4", client.resolveTorrent("fixture-token", hash, 0))
        assertEquals(0, steps.size)
        assertEquals("POST", requests[2].requestMethod)
        assertEquals("files=7", requests[2].form.toString("UTF-8"))
        assertEquals("Bearer fixture-token", requests[2].getRequestProperty("Authorization"))
    }

    private data class Reply(val path: String, val code: Int, val json: String)

    private class FixtureConnection(url: URL, private val reply: Reply) : HttpURLConnection(url) {
        val form = ByteArrayOutputStream()
        override fun getResponseCode() = reply.code
        override fun getInputStream() = reply.json.byteInputStream()
        override fun getOutputStream() = form
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }
}
