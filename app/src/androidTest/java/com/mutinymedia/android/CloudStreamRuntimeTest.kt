package com.mutinymedia.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.utils.extractorApis
import com.mutinymedia.android.data.CloudStreamHost
import com.mutinymedia.android.data.CloudStreamInstaller
import com.mutinymedia.android.data.CloudStreamProviderClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CloudStreamRuntimeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun independentlyDexedExtensionRegistersSearchesExtractsAndUnloads() = runBlocking {
        val id = "2".repeat(64)
        val host = CloudStreamHost(context)
        val client = CloudStreamProviderClient(host)
        try {
            stageFixture(id, "mutiny-cloudstream-fixture.cs3")
            assertEquals(CloudStreamHost.State.DISABLED, host.inspect(id).state)
            val status = host.setEnabled(id, true)
            assertEquals(status.detail, CloudStreamHost.State.ACTIVE, status.state)
            assertEquals(listOf("Mutiny Fixture Provider"), status.providers)
            assertTrue(APIHolder.getApiFromNameNull("Mutiny Fixture Provider") != null)
            val title = client.search("CloudStream Fixture").items.single()
            assertEquals(title, client.browse().items.single())
            val details = client.details(title)
            assertEquals("Deterministic CloudStream fixture metadata", details.item.description)
            val episode = details.episodes.single()
            assertEquals("Fixture episode", episode.title)
            val data = details.episodeData.getValue(episode.id)
            val stream = client.streams(title, data.first, data.second).single()
            assertTrue(stream.playable)
            assertTrue(stream.cloudStreamSession)
            assertEquals("https://fixture.example", stream.headers["referer"])
            assertEquals("registered", stream.headers["X-Fixture"])
            assertEquals("English", stream.subtitles.single().language)
            assertEquals("text/vtt", stream.subtitles.single().mimeType)
            assertEquals(CloudStreamHost.State.DISABLED, host.setEnabled(id, false).state)
            assertTrue(client.search("CloudStream Fixture").items.isEmpty())
            assertFalse(extractorApis.any { it.sourcePlugin?.endsWith("$id.cs3") == true })
            assertEquals(CloudStreamHost.State.ACTIVE, host.reload(id).state)
            assertEquals(1, client.search("CloudStream Fixture").items.size)
            host.uninstall(id)
            assertEquals(CloudStreamHost.State.MISSING, host.inspect(id).state)
            assertTrue(client.search("CloudStream Fixture").items.isEmpty())
        } finally {
            host.uninstall(id)
        }
    }

    @Test fun failedInitializationRemovesPartialRegistrations() = runBlocking {
        val id = "3".repeat(64)
        val host = CloudStreamHost(context)
        try {
            stageFixture(id, "mutiny-cloudstream-failure.cs3")
            val path = CloudStreamInstaller(context).file(id).absolutePath
            assertEquals(CloudStreamHost.State.FAILED, host.setEnabled(id, true).state)
            assertFalse(APIHolder.allProviders.any { it.sourcePlugin == path })
            assertFalse(extractorApis.any { it.sourcePlugin == path })
            assertTrue(CloudStreamProviderClient(host).search("Fixture").items.isEmpty())
        } finally {
            host.uninstall(id)
        }
    }

    private fun stageFixture(id: String, asset: String) {
        val file: File = CloudStreamInstaller(context).file(id)
        file.parentFile?.mkdirs()
        check(!file.exists() || file.delete())
        instrumentation.context.assets.open(asset).use { input ->
            file.outputStream().use { output ->
                check(file.setReadOnly())
                input.copyTo(output)
            }
        }
    }
}
