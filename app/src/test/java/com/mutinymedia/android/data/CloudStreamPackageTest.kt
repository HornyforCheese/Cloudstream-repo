package com.mutinymedia.android.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CloudStreamPackageTest {
    private fun archive(dex: ByteArray?, manifest: String = """{"pluginClassName":"org.example.Plugin"}""", test: (File) -> Unit) {
        val file = File.createTempFile("cloudstream", ".cs3")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
                if (dex != null) { zip.putNextEntry(ZipEntry("classes.dex")); zip.write(dex); zip.closeEntry() }
            }
            test(file)
        } finally { file.delete() }
    }
    @Test fun missingDexIsRejected() = archive(null) { file ->
        assertTrue(runCatching { CloudStreamPackage.validate(file) }.exceptionOrNull()?.message.orEmpty().contains("Missing classes.dex"))
    }
    @Test fun malformedDexIsRejected() = archive(ByteArray(112)) { file ->
        assertTrue(runCatching { CloudStreamPackage.validate(file) }.isFailure)
    }
    @Test fun invalidClassNameIsRejected() = archive(ByteArray(112), """{"pluginClassName":"invalid name"}""") { file ->
        assertTrue(runCatching { CloudStreamPackage.validate(file) }.exceptionOrNull()?.message.orEmpty().contains("invalid plugin class"))
    }
    @Test fun supportedHeaderPassesStructuralValidationOnly() {
        val header = ByteArray(112); "dex\n035\u0000".toByteArray().copyInto(header)
        archive(header) { assertEquals("org.example.Plugin", CloudStreamPackage.validate(it).className) }
    }
}
