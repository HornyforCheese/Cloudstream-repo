package com.mutinymedia.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mutinymedia.android.data.CloudStreamHost
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Verifies diagnostic gates. This is not a test of upstream CloudStream plugin execution. */
@RunWith(AndroidJUnit4::class)
class CloudStreamCompatibilityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun missingPackageIsReported() = runBlocking {
        val id = "0".repeat(64)
        assertEquals(CloudStreamHost.State.MISSING, CloudStreamHost(context).inspect(id).state)
    }

    @Test fun malformedPluginIsRejectedWithoutExecution() = runBlocking {
        val id = "1".repeat(64)
        val directory = File(context.filesDir, "cloudstream_extensions").apply { mkdirs() }
        val archive = File(directory, "$id.cs3")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write("""{"pluginClassName":"not valid class name"}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("classes.dex"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
            val status = CloudStreamHost(context).inspect(id)
            assertEquals(CloudStreamHost.State.FAILED, status.state)
            assertTrue(status.detail.contains("invalid plugin class"))
        } finally {
            archive.delete()
        }
    }
}
