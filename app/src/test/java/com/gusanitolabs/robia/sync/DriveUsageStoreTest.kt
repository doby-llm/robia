package com.gusanitolabs.robia.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DriveUsageStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun roundTripsTheLastWrittenUsage() {
        val file = File(temp.newFolder("sync"), "drive_usage.json")
        val usage = DriveUsage(
            totalBytes = 1_234L,
            photoCount = 3,
            photoBytes = 1_000L,
            backupCount = 1,
            measuredAtEpochMillis = 99L,
        )

        DriveUsageStore(file).write(usage)

        assertEquals(usage, DriveUsageStore(file).read())
    }

    @Test
    fun latestFollowsWritesAndStartsFromTheFile() {
        val file = File(temp.newFolder("sync"), "drive_usage.json")
        val store = DriveUsageStore(file)
        val usage = DriveUsage(totalBytes = 5L, photoCount = 1, photoBytes = 4L, backupCount = 1, measuredAtEpochMillis = 1L)

        assertNull(store.latest.value)
        store.write(usage)

        assertEquals(usage, store.latest.value)
        assertEquals(usage, DriveUsageStore(file).latest.value)
    }

    @Test
    fun corruptFileReadsAsNull() {
        val file = File(temp.newFolder("sync"), "drive_usage.json")
        file.writeText("{not json")

        assertNull(DriveUsageStore(file).read())
    }

    @Test
    fun missingFieldReadsAsNull() {
        val file = File(temp.newFolder("sync"), "drive_usage.json")
        file.writeText("""{"totalBytes":1}""")

        assertNull(DriveUsageStore(file).read())
    }

    @Test
    fun missingFileReadsAsNull() {
        assertNull(DriveUsageStore(File(temp.newFolder("sync"), "absent.json")).read())
    }
}
