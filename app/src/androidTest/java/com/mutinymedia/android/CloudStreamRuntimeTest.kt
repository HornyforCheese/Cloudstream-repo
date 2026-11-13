package com.mutinymedia.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mutinymedia.android.data.*
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.utils.extractorApis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual DEX, Android Plugin.load(Context), MainAPI and extractor invocation, no fake host. */
@RunWith(AndroidJUnit4::class)
class CloudStreamRuntimeTest {
    @Test fun separateDexSearchToStreamAndReload() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = CloudStreamHost(context)
        val id = "f".repeat(64)
        val file = File(context.filesDir, "cloudstream_extensions/$id.cs3")
        file.parentFile!!.mkdirs()
        file.delete()
        instrumentation.context.assets.open("legal-fixture.cs3").use { input -> file.outputStream().use { input.copyTo(it) } }
        try {
            // Proves no parent-classloader shortcut can satisfy this test.
            assertTrue(runCatching { context.classLoader.loadClass("org.mutinymedia.fixture.FixturePlugin") }.isFailure)
            val status = host.inspect(id)
            assertEquals(status.detail, CloudStreamHost.State.ACTIVE, status.state)
            assertEquals(1, host.providers().count { it.packageId == id })
            assertTrue(extractorApis.any { it.sourcePlugin == file.absolutePath })
            val client = CloudStreamClient(host)
            val results = client.search("bunny")
            val title = results.single { it.id.startsWith("cloudstream:$id/") }
            assertEquals("Big Buck Bunny", title.title)
            assertTrue(client.catalogs().any { it.id == title.id })
            val details = client.details(title)
            assertEquals(2008, details.item.year)
            val streams = client.streams(details.item)
            val stream = streams.single()
            assertTrue(stream.playable)
            assertEquals("verified", stream.headers["X-Mutiny-Fixture"])
            assertEquals("https://peach.blender.org/", stream.headers["Referer"])
            val series = client.search("series").single { it.id.startsWith("cloudstream:$id/") }
            val seriesDetails = client.details(series)
            val episode = seriesDetails.episodes.single()
            assertEquals(2, episode.season)
            assertEquals(3, episode.number)
            assertTrue(client.streams(seriesDetails.item.copy(id = episode.id)).single().playable)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val viewModels = androidx.lifecycle.ViewModelStore()
                try {
                    val vm = androidx.lifecycle.ViewModelProvider(viewModels,
                        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application))[MutinyViewModel::class.java]
                    vm.play(stream)
                    assertEquals(stream, vm.activeStream.value)
                } finally { viewModels.clear() }
            }
            val provider = host.providers().single { it.packageId == id }
            host.enable(provider.id, false)
            assertFalse(client.search("bunny").any { it.id == title.id })
            host.enable(provider.id, true)
            host.unload(id)
            assertFalse(APIHolder.allProviders.any { it.sourcePlugin == file.absolutePath })
            assertFalse(extractorApis.any { it.sourcePlugin == file.absolutePath })
            assertNull(CloudStreamPlayback.client(stream.id))
            assertEquals(CloudStreamHost.State.ACTIVE, host.inspect(id).state)
            assertEquals(1, host.providers().count { it.packageId == id })
        } finally { host.unload(id); file.delete() }
    }
    @Test fun portableLifecycleAndPartialRegistrationRollback() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = CloudStreamHost(context)
        val ids = listOf("d".repeat(64), "e".repeat(64))
        val directory = File(context.filesDir, "cloudstream_extensions").apply { mkdirs() }
        val bytes = instrumentation.context.assets.open("legal-fixture.cs3").use { it.readBytes() }
        fun write(id: String, className: String) {
            val target = File(directory, "$id.cs3"); target.delete()
            java.util.zip.ZipInputStream(bytes.inputStream()).use { source ->
                java.util.zip.ZipOutputStream(target.outputStream()).use { output ->
                    while (true) {
                        val entry = source.nextEntry ?: break
                        output.putNextEntry(java.util.zip.ZipEntry(entry.name))
                        if (entry.name == "manifest.json") output.write("""{"pluginClassName":"org.mutinymedia.fixture.$className"}""".toByteArray())
                        else source.copyTo(output)
                        output.closeEntry()
                    }
                }
            }
        }
        try {
            write(ids[0], "PortablePlugin")
            assertEquals(CloudStreamHost.State.ACTIVE, host.inspect(ids[0]).state)
            write(ids[1], "FailingPlugin")
            assertEquals(CloudStreamHost.State.FAILED, host.inspect(ids[1]).state)
            assertEquals(1, host.providers().count { it.packageId == ids[0] })
            assertFalse(APIHolder.allProviders.any { it.sourcePlugin == File(directory, "${ids[1]}.cs3").absolutePath })
            assertFalse(extractorApis.any { it.sourcePlugin == File(directory, "${ids[1]}.cs3").absolutePath })
        } finally { ids.forEach { host.unload(it); File(directory, "$it.cs3").delete() } }
    }

}
