package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.DriveSnapshotJson
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.random.Random

/** REQ-ZIP-01 (V-ZIP-1, V-ZIP-2): archive contents, missing-file handling, progress, and cancellation. */
class WardrobeZipExporterTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun archiveHoldsDataPhotosOriginalsAndReadme() = runBlocking {
        val cutoutG1 = file("g1-cutout.png", bytes = 40_000, seed = 1)
        val originalG1 = file("g1-original.jpg", bytes = 90_000, seed = 2)
        val cutoutG2 = file("g2-cutout.png", bytes = 12_000, seed = 3)
        val missingG3 = File(folder.root, "g3-gone.jpg").path
        val snapshot = snapshotOf(
            photo("g1", cutoutG1, original = originalG1),
            photo("g2", cutoutG2),
            photo("g3", missingG3),
        )
        val progress = mutableListOf<Pair<Int, Int>>()
        val entries = export(snapshot) { done, total -> progress += done to total }

        assertEquals(setOf("robia-data.json", "photos/g1.png", "originals/g1.jpg", "photos/g2.png", "README.txt"), entries.keys)
        assertTrue(File(cutoutG1).readBytes().contentEquals(entries.getValue("photos/g1.png")))
        assertTrue(File(originalG1).readBytes().contentEquals(entries.getValue("originals/g1.jpg")))
        assertTrue(File(cutoutG2).readBytes().contentEquals(entries.getValue("photos/g2.png")))

        // Each photo's localUri becomes its archive path; the missing g3 photo is blank.
        val archivePathByGarment = mapOf("g1" to "photos/g1.png", "g2" to "photos/g2.png", "g3" to "")
        val expectedJson = DriveSnapshotJson.encode(
            snapshot.copy(
                photos = snapshot.photos.map { record ->
                    record.copy(localUri = archivePathByGarment.getValue(record.garmentId))
                },
            ),
        )
        assertEquals(expectedJson, String(entries.getValue("robia-data.json"), Charsets.UTF_8))

        val readme = String(entries.getValue("README.txt"), Charsets.UTF_8)
        assertTrue(readme.contains("App version: test-version"))
        assertTrue(readme.contains("Missing files (not on the phone at export time, so not in this archive):"))
        assertTrue(readme.contains("  photos/g3.jpg"))

        assertEquals(listOf(0 to 4, 1 to 4, 2 to 4, 3 to 4, 4 to 4), progress)
    }

    @Test
    fun missingOriginalIsListedInReadmeAndExportContinues() = runBlocking {
        val cutout = file("g1-cutout.jpg", bytes = 5_000, seed = 4)
        val goneOriginal = File(folder.root, "g1-original-gone.jpg").path
        val entries = export(snapshotOf(photo("g1", cutout, original = goneOriginal))) { _, _ -> }

        assertEquals(setOf("robia-data.json", "photos/g1.jpg", "README.txt"), entries.keys)
        val readme = String(entries.getValue("README.txt"), Charsets.UTF_8)
        assertTrue(readme.contains("  originals/g1.jpg"))
        val json = String(entries.getValue("robia-data.json"), Charsets.UTF_8)
        assertTrue(json.contains("\"localUri\":\"photos/g1.jpg\""))
    }

    @Test
    fun cancelMidStreamStopsReadingAndDeletesTheDocument() = runBlocking {
        val photo = file("g1-cutout.png", bytes = PHOTO_BYTES, seed = 5)
        var bytesRead = 0L
        val exporter = WardrobeZipExporter(
            loadSnapshot = { snapshotOf(photo("g1", photo)) },
            openLocalFile = { uri -> CountingInput(File(uri).inputStream()) { read -> bytesRead += read } },
            appVersion = "test-version",
        )
        lateinit var job: Job
        var discarded = false
        val output = object : ByteArrayOutputStream() {
            var closed = false
            override fun write(b: ByteArray, off: Int, len: Int) {
                super.write(b, off, len)
                if (size() >= CANCEL_AFTER_OUTPUT_BYTES) job.cancel()
            }
            override fun close() {
                closed = true
                super.close()
            }
        }
        job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            exporter.writeArchive(open = { output }, discard = { discarded = true })
        }
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertTrue("the partial document must be deleted", discarded)
        assertTrue("the chosen document's stream must be closed", output.closed)
        assertTrue("reading must stop early, read $bytesRead of $PHOTO_BYTES", bytesRead < PHOTO_BYTES)
        assertFalse("the archive must not be finished", endOfCentralDirectory(output.toByteArray()))
    }

    private suspend fun export(snapshot: WardrobeSyncSnapshot, onProgress: (Int, Int) -> Unit): Map<String, ByteArray> {
        val output = ByteArrayOutputStream()
        exporter(snapshot).export(output, onProgress)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> entries[entry.name] = zip.readBytes() }
        }
        return entries
    }

    private fun exporter(snapshot: WardrobeSyncSnapshot) = WardrobeZipExporter(
        loadSnapshot = { snapshot },
        openLocalFile = { uri -> File(uri).takeIf(File::isFile)?.inputStream() },
        appVersion = "test-version",
    )

    private fun file(name: String, bytes: Int, seed: Int): String =
        File(folder.root, name).apply { writeBytes(Random(seed).nextBytes(bytes)) }.path

    private fun photo(garmentId: String, localUri: String, original: String? = null) = GarmentPhotoRecord(
        garmentId = garmentId,
        localUri = localUri,
        localOriginalUri = original,
    )

    private fun snapshotOf(vararg photos: GarmentPhotoRecord) = WardrobeSyncSnapshot(photos = photos.toList())

    private fun endOfCentralDirectory(bytes: ByteArray): Boolean = (bytes.size - 3 downTo 0).any { index ->
        bytes[index] == 0x50.toByte() && bytes[index + 1] == 0x4b.toByte() &&
            bytes[index + 2] == 0x05.toByte() && bytes[index + 3] == 0x06.toByte()
    }

    /** Reports every byte read, so a test can see how far the export got. */
    private class CountingInput(source: InputStream, private val onRead: (Int) -> Unit) : FilterInputStream(source) {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = super.read(b, off, len)
            if (read > 0) onRead(read)
            return read
        }
    }

    private companion object {
        const val PHOTO_BYTES = 1_000_000
        const val CANCEL_AFTER_OUTPUT_BYTES = 10_000
    }
}
