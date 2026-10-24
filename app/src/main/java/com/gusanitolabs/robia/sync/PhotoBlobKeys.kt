package com.gusanitolabs.robia.sync

import java.security.MessageDigest

/**
 * Short, fixed-length photo names for Drive keys and restored local files.
 *
 * Keys derive from the garment id and the encoded bytes only, never from a previous local name,
 * so repeated restores cannot grow the name.
 */
object PhotoBlobKeys {
    private val UNSAFE_KEY_CHARACTERS = Regex("[^A-Za-z0-9._-]")
    private val DRIVE_KEY_PATTERN = Regex("^photos/[A-Za-z0-9._-]+/[0-9a-f]{16}\\.webp$")
    private val ORIGINAL_KEY_PATTERN = Regex("^originals/([A-Za-z0-9._-]+)/[0-9a-f]{16}\\.jpg$")
    const val ORIGINAL_PREFIX = "originals/"
    private const val MAX_SAFE_ID_LENGTH = 64

    fun hash16(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
        .take(16)

    fun driveKey(garmentId: String, hash16: String) = "photos/${safe(garmentId)}/$hash16.webp"

    fun restoredLocalFileName(garmentId: String, hash16: String) = "restored-${safe(garmentId)}-$hash16.webp"

    fun isLegacyKey(blobPath: String): Boolean = !DRIVE_KEY_PATTERN.matches(blobPath)

    /** Original photos live beside `photos/`, never under it: a JPEG there would count as a legacy cutout (REQ-OR-04). */
    fun originalDriveKey(garmentId: String, hash16: String) = "$ORIGINAL_PREFIX${safe(garmentId)}/$hash16.jpg"

    fun originalPrefix(garmentId: String) = "$ORIGINAL_PREFIX${safe(garmentId)}/"

    /** The garment id a `originals/` blob belongs to, or null when [blobPath] is not an original key. */
    fun originalGarmentId(blobPath: String): String? = ORIGINAL_KEY_PATTERN.find(blobPath)?.let { match -> match.groupValues[1] }

    fun restoredOriginalFileName(garmentId: String, hash16: String) = "restored-original-${safe(garmentId)}-$hash16.jpg"

    private fun safe(id: String): String = id
        .replace(UNSAFE_KEY_CHARACTERS, "_")
        .take(MAX_SAFE_ID_LENGTH)
}
