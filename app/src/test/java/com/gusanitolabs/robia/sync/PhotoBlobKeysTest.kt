package com.gusanitolabs.robia.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoBlobKeysTest {
    private val driveKeyPattern = Regex("^photos/[A-Za-z0-9._-]+/[0-9a-f]{16}\\.webp$")

    @Test
    fun driveKeysMatchTheFixedShape() {
        val key = PhotoBlobKeys.driveKey("garment-1", PhotoBlobKeys.hash16("encoded".toByteArray()))

        assertTrue(driveKeyPattern.matches(key))
        assertEquals("photos/garment-1/${PhotoBlobKeys.hash16("encoded".toByteArray())}.webp", key)
    }

    @Test
    fun unsafeCharactersAreReplacedAndIdsAreTruncated() {
        val key = PhotoBlobKeys.driveKey("a b/c:d", "0123456789abcdef")
        assertEquals("photos/a_b_c_d/0123456789abcdef.webp", key)

        val longId = "x".repeat(200)
        val longKey = PhotoBlobKeys.driveKey(longId, "0123456789abcdef")
        assertEquals("photos/${"x".repeat(64)}/0123456789abcdef.webp", longKey)
    }

    @Test
    fun hash16IsSixteenLowercaseHexCharacters() {
        val hash = PhotoBlobKeys.hash16(ByteArray(32) { it.toByte() })

        assertEquals(16, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{16}")))
    }

    @Test
    fun legacyDetectionFlagsOnlyKeysOutsideTheNewShape() {
        assertFalse(PhotoBlobKeys.isLegacyKey("photos/garment-1/0123456789abcdef.webp"))
        assertTrue(PhotoBlobKeys.isLegacyKey("photos/garment-1/drive-old-name-1f2e3d4c.jpg"))
        assertTrue(PhotoBlobKeys.isLegacyKey("photos/garment-1/0123456789ABCDEF.webp"))
        assertTrue(PhotoBlobKeys.isLegacyKey("wardrobe_snapshot.json"))
    }

    @Test
    fun tenRestoreCyclesKeepTheLocalNameConstantAndShort() {
        val garmentId = "7c9e6679-7425-40de-944b-e07fc1f90ae7-with-a-much-longer-suffix-to-stress-the-limit"
        val encoded = "same encoded webp bytes".toByteArray()
        val hash = PhotoBlobKeys.hash16(encoded)

        val names = (1..10).map {
            // Each cycle re-derives the name from the garment id and content hash, never from the previous name.
            PhotoBlobKeys.restoredLocalFileName(garmentId, hash)
        }

        assertEquals(1, names.distinct().size)
        assertTrue(names.first().toByteArray().size <= 120)
    }
}
