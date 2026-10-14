package com.gusanitolabs.robia.suggestions

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.math.sqrt

/** Float32 little-endian bytes, the storage and Drive payload format for embeddings. */
fun FloatArray.toLeBytes(): ByteArray =
    ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).also { buffer ->
        buffer.asFloatBuffer().put(this)
    }.array()

fun ByteArray.toFloatArrayLe(): FloatArray {
    require(size % Float.SIZE_BYTES == 0) { "Embedding bytes must be a multiple of 4, was $size" }
    val floats = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    return FloatArray(floats.remaining()).also { target -> floats.get(target) }
}

fun FloatArray.toBase64(): String = Base64.getEncoder().encodeToString(toLeBytes())

fun floatArrayFromBase64(encoded: String): FloatArray = Base64.getDecoder().decode(encoded).toFloatArrayLe()

fun dot(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "Vector sizes differ: ${a.size} vs ${b.size}" }
    var sum = 0.0
    for (index in a.indices) sum += a[index].toDouble() * b[index].toDouble()
    return sum.toFloat()
}

/** Unit-length copy; a zero vector stays zero. */
fun FloatArray.normalized(): FloatArray {
    val norm = sqrt(dot(this, this).toDouble()).toFloat()
    if (norm == 0f) return copyOf()
    return FloatArray(size) { index -> this[index] / norm }
}

/** Population z-scores. A (near-)constant vector maps to all zeros so ties never look like a winner. */
fun zScores(values: FloatArray): FloatArray {
    if (values.isEmpty()) return FloatArray(0)
    val mean = values.map(Float::toDouble).average()
    val variance = values.sumOf { value -> (value - mean) * (value - mean) } / values.size
    val std = sqrt(variance)
    if (std < 1e-6) return FloatArray(values.size)
    return FloatArray(values.size) { index -> ((values[index] - mean) / std).toFloat() }
}
