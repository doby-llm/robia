package com.gusanitolabs.robia.sync

import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.IOException
import kotlin.math.roundToLong

/** How a failed Drive call should be handled. 403 is split by Google's error reason. */
internal sealed interface DriveErrorClass {
    data object Transient : DriveErrorClass
    data object AuthRequired : DriveErrorClass
    data object StorageFull : DriveErrorClass
    data object NotFound : DriveErrorClass
    data class Permanent(val code: Int) : DriveErrorClass
}

internal fun classify(code: Int, body: String): DriveErrorClass = when (code) {
    401 -> DriveErrorClass.AuthRequired
    403 -> when (driveErrorReason(body)) {
        "rateLimitExceeded", "userRateLimitExceeded", "backendError" -> DriveErrorClass.Transient
        "storageQuotaExceeded" -> DriveErrorClass.StorageFull
        else -> DriveErrorClass.AuthRequired
    }
    404 -> DriveErrorClass.NotFound
    408, 429 -> DriveErrorClass.Transient
    in 500..599 -> DriveErrorClass.Transient
    else -> DriveErrorClass.Permanent(code)
}

/** Network-level IOExceptions (timeout, DNS, connect, reset) are transient; other throwables are not. */
internal fun classify(t: Throwable): DriveErrorClass = when (t) {
    is IOException -> DriveErrorClass.Transient
    else -> DriveErrorClass.Permanent(code = 0)
}

private fun driveErrorReason(body: String): String? {
    val error = try {
        JSONObject(body).optJSONObject("error")
    } catch (_: Exception) {
        null
    } ?: return null
    return error.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")?.takeIf(String::isNotBlank)
        ?: error.optString("status").takeIf(String::isNotBlank)
}

/**
 * Retries only [DriveApiResult.Transient] results: up to [maxAttempts] attempts, waiting
 * 1 s, 2 s, 4 s, 8 s (each ±20 % jitter) between them.
 */
internal suspend fun <T> withRetry(
    maxAttempts: Int = 5,
    sleeper: suspend (Long) -> Unit = { delay(it) },
    random: () -> Double = Math::random,
    block: suspend () -> DriveApiResult<T>,
): DriveApiResult<T> {
    var attempt = 1
    while (true) {
        val result = block()
        if (result !is DriveApiResult.Transient || attempt >= maxAttempts) return result
        val baseMillis = 1_000L shl (attempt - 1)
        sleeper((baseMillis * (0.8 + 0.4 * random())).roundToLong())
        attempt++
    }
}
