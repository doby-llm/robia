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
    private const val MAX_SAFE_ID_LENGTH = 64

    fun hash16(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
        .take(16)

    fun driveKey(garmentId: String, hash16: String) = "photos/${safe(garmentId)}/$hash16.webp"

    fun restoredLocalFileName(garmentId: String, hash16: String) = "restored-${safe(garmentId)}-$hash16.webp"

    fun isLegacyKey(blobPath: String): Boolean = !DRIVE_KEY_PATTERN.matches(blobPath)

    private fun safe(id: String): String = id
        .replace(UNSAFE_KEY_CHARACTERS, "_")
        .take(MAX_SAFE_ID_LENGTH)
}
