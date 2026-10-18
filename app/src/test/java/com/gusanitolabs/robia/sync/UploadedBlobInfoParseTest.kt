package com.gusanitolabs.robia.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The upload response: Drive reports the size as a string or a number; checksums compare case-insensitively. */
class UploadedBlobInfoParseTest {
    @Test
    fun sizeAsAJsonStringIsRead() {
        assertEquals(1234L, parseUploadedBlobInfo("""{"id":"x","size":"1234"}""").size)
    }

    @Test
    fun sizeAsAJsonNumberIsRead() {
        assertEquals(1234L, parseUploadedBlobInfo("""{"id":"x","size":1234}""").size)
    }

    @Test
    fun missingOrNullSizeIsUnknown() {
        assertNull(parseUploadedBlobInfo("""{"id":"x"}""").size)
        assertNull(parseUploadedBlobInfo("""{"id":"x","size":null}""").size)
        assertNull(parseUploadedBlobInfo("""{"id":"x","size":"not a number"}""").size)
    }

    @Test
    fun uppercaseChecksumIsLowercased() {
        assertEquals("abcdef0123", parseUploadedBlobInfo("""{"sha256Checksum":"ABCDEF0123"}""").sha256)
    }

    @Test
    fun missingNullOrBlankChecksumIsUnknown() {
        assertNull(parseUploadedBlobInfo("""{"id":"x"}""").sha256)
        assertNull(parseUploadedBlobInfo("""{"sha256Checksum":null}""").sha256)
        assertNull(parseUploadedBlobInfo("""{"sha256Checksum":"  "}""").sha256)
    }
}
