package com.gusanitolabs.robia.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveFileIndexTest {
    @Test
    fun indexesFilesByNameAndListsPrefixMatches() {
        val index = DriveFileIndex(
            listOf(
                DriveFileMetadata(id = "id-1", name = "photos/garment-1/aaaaaaaaaaaaaaaa.webp"),
                DriveFileMetadata(id = "id-2", name = "photos/garment-1/bbbbbbbbbbbbbbbb.webp"),
                DriveFileMetadata(id = "id-3", name = "photos/garment-2/cccccccccccccccc.webp"),
                DriveFileMetadata(id = "id-4", name = "wardrobe_snapshot.json"),
            ),
        )

        assertEquals("id-1", index.idOf("photos/garment-1/aaaaaaaaaaaaaaaa.webp"))
        assertNull(index.idOf("photos/garment-9/missing.webp"))
        assertEquals(
            listOf("photos/garment-1/aaaaaaaaaaaaaaaa.webp", "photos/garment-1/bbbbbbbbbbbbbbbb.webp"),
            index.namesWithPrefix("photos/garment-1/"),
        )
    }

    @Test
    fun putAndRemoveUpdateTheIndex() {
        val index = DriveFileIndex(emptyList())

        index.put(DriveFileMetadata(id = "id-1", name = "photos/garment-1/aaaaaaaaaaaaaaaa.webp"))
        assertEquals("id-1", index.idOf("photos/garment-1/aaaaaaaaaaaaaaaa.webp"))

        index.put(DriveFileMetadata(id = "id-2", name = "photos/garment-1/aaaaaaaaaaaaaaaa.webp"))
        assertEquals("id-2", index.idOf("photos/garment-1/aaaaaaaaaaaaaaaa.webp"))

        index.remove("photos/garment-1/aaaaaaaaaaaaaaaa.webp")
        assertNull(index.idOf("photos/garment-1/aaaaaaaaaaaaaaaa.webp"))
        assertEquals(emptyList<String>(), index.namesWithPrefix("photos/"))
    }

    @Test
    fun duplicateNamesResolveToTheMostRecentlyModifiedFile() {
        val index = DriveFileIndex(
            listOf(
                DriveFileMetadata(id = "newer", name = "wardrobe_snapshot.json", modifiedTime = "2026-10-02T10:00:00Z"),
                DriveFileMetadata(id = "older", name = "wardrobe_snapshot.json", modifiedTime = "2026-10-01T10:00:00Z"),
            ),
        )

        assertEquals("newer", index.idOf("wardrobe_snapshot.json"))
    }

    @Test
    fun filesWithoutANameAreNotIndexed() {
        val index = DriveFileIndex(listOf(DriveFileMetadata(id = "unnamed")))

        assertNull(index.idOf(""))
        assertEquals(emptyList<String>(), index.namesWithPrefix(""))
    }
}
