package com.gusanitolabs.robia.media

import org.junit.Assert.assertEquals
import org.junit.Test

/** V-CLN-1: the pure orphan planner keeps referenced and young files and deletes only old raw, subject and cutout names. */
class OrphanPhotoCleanupTest {
    private val now = 100L * DAY_MILLIS
    private val providerPrefix = "content://com.gusanitolabs.robia.fileprovider/robia_clothing_images/"

    private fun file(name: String, ageMillis: Long) = FileInfo(name, now - ageMillis, sizeBytes = 1_000L)

    private fun planned(files: List<FileInfo>, referenced: Set<String> = emptySet()): List<String> =
        OrphanPhotoCleanup.planOrphanDeletion(files, referenced, now).map(FileInfo::name)

    @Test
    fun unreferencedOldRawSubjectAndCutoutFilesArePlanned() {
        val files = listOf(
            file("gallery-1a2b.jpg", 2 * DAY_MILLIS),
            file("camera-3c4d.jpg", 2 * DAY_MILLIS),
            file("subject-5e6f.png", 2 * DAY_MILLIS),
            file("cropped-subject-7a8b.png", 2 * DAY_MILLIS),
        )

        assertEquals(files.map(FileInfo::name), planned(files))
    }

    @Test
    fun referencedFilesAreKeptWhateverTheirUriLooksLike() {
        val files = listOf(
            file("gallery-1a2b.jpg", 2 * DAY_MILLIS),
            file("cropped-subject-7a8b.png", 2 * DAY_MILLIS),
            file("camera-3c4d.jpg", 2 * DAY_MILLIS),
        )
        val referenced = setOf(
            "${providerPrefix}gallery-1a2b.jpg",
            "content://other.authority/nested/cropped-subject-7a8b.png?x=1",
        )

        assertEquals(listOf("camera-3c4d.jpg"), planned(files, referenced))
    }

    @Test
    fun youngFilesAreKept() {
        val files = listOf(
            file("gallery-1a2b.jpg", DAY_MILLIS / 2),
            file("subject-5e6f.png", DAY_MILLIS),
        )

        assertEquals(emptyList<String>(), planned(files))
    }

    @Test
    fun onlyFilesOlderThan24HoursArePlanned() {
        val files = listOf(
            file("gallery-exact.jpg", DAY_MILLIS),
            file("gallery-just-over.jpg", DAY_MILLIS + 1),
        )

        assertEquals(listOf("gallery-just-over.jpg"), planned(files))
    }

    @Test
    fun otherNamesAreNeverPlannedEvenWhenOldAndUnreferenced() {
        val files = listOf(
            file("original-9f0a.jpg", 30 * DAY_MILLIS),
            file("restored-garment-0123abcd.webp", 30 * DAY_MILLIS),
            file("thumbnail-cache.png", 30 * DAY_MILLIS),
            file("notes.txt", 30 * DAY_MILLIS),
        )

        assertEquals(emptyList<String>(), planned(files))
    }

    @Test
    fun activePhotoRefsRegisteredDraftsAreTreatedAsReferenced() {
        val draftUri = "${providerPrefix}subject-draft-0001.png"
        val files = listOf(file("subject-draft-0001.png", 2 * DAY_MILLIS))

        ActivePhotoRefs.register(draftUri)
        assertEquals(emptyList<String>(), planned(files, ActivePhotoRefs.snapshot()))

        ActivePhotoRefs.unregister(draftUri)
        assertEquals(listOf("subject-draft-0001.png"), planned(files, ActivePhotoRefs.snapshot()))
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
