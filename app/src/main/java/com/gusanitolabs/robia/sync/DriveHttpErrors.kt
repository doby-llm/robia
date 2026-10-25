package com.gusanitolabs.robia.sync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToLong

/** How a failed Drive call should be handled. 403 is split by Google's error reason. */
internal sealed interface DriveErrorClass {
    /** [retryAfterMillis] is the server's Retry-After, already capped; the retry waits at least that long. */
    data class Transient(val retryAfterMillis: Long? = null) : DriveErrorClass
    data object AuthRequired : DriveErrorClass
    data object StorageFull : DriveErrorClass
    data object NotFound : DriveErrorClass
    data class Permanent(val code: Int) : DriveErrorClass
}

/** Upper bound for a server Retry-After: a longer wait than this is retried sooner, with the backoff. */
internal const val RETRY_AFTER_CAP_MILLIS = 60_000L

private val TRANSIENT_FORBIDDEN_REASONS = setOf(
    "rateLimitExceeded",
    "userRateLimitExceeded",
    "backendError",
    "dailyLimitExceeded",
    "sharingRateLimitExceeded",
    "quotaExceeded",
)
private val AUTH_FORBIDDEN_REASONS = setOf("forbidden", "insufficientPermissions")
private const val RESOURCE_EXHAUSTED = "RESOURCE_EXHAUSTED"
private val RATE_LIMIT_REASONS = setOf("rateLimitExceeded", "userRateLimitExceeded")

internal fun classify(code: Int, body: String, retryAfterMillis: Long? = null): DriveErrorClass = when (code) {
    401 -> DriveErrorClass.AuthRequired
    403 -> classifyForbidden(body, retryAfterMillis)
    404 -> DriveErrorClass.NotFound
    408, 429 -> DriveErrorClass.Transient(retryAfterMillis)
    in 500..599 -> DriveErrorClass.Transient(retryAfterMillis)
    else -> DriveErrorClass.Permanent(code)
}

/**
 * Only the reasons that name a sign-in problem require sign-in. An unlisted reason, or a body without a readable
 * reason, is a permanent failure of the cycle: it is retried later and never shows the sign-in error.
 */
private fun classifyForbidden(body: String, retryAfterMillis: Long?): DriveErrorClass {
    val reason = driveErrorReason(body)
    return when {
        reason == "storageQuotaExceeded" -> DriveErrorClass.StorageFull
        reason in TRANSIENT_FORBIDDEN_REASONS || reason == RESOURCE_EXHAUSTED || driveErrorStatus(body) == RESOURCE_EXHAUSTED ->
            DriveErrorClass.Transient(retryAfterMillis)
        reason in AUTH_FORBIDDEN_REASONS -> DriveErrorClass.AuthRequired
        else -> DriveErrorClass.Permanent(403)
    }
}

/** A 429, a Retry-After or a Google rate-limit reason: the upload sends fewer calls at once for the rest of the cycle. */
internal fun isRateLimited(code: Int, body: String, retryAfterMillis: Long?): Boolean =
    code == 429 || retryAfterMillis != null || driveErrorReason(body) in RATE_LIMIT_REASONS

/** Network-level IOExceptions (timeout, DNS, connect, reset) are transient; other throwables are not. */
internal fun classify(t: Throwable): DriveErrorClass = when (t) {
    is IOException -> DriveErrorClass.Transient()
    else -> DriveErrorClass.Permanent(code = 0)
}

/** The seconds form of Retry-After, capped at [RETRY_AFTER_CAP_MILLIS]. An HTTP-date, a negative or any other value gives null. */
internal fun parseRetryAfterMillis(headerValue: String?): Long? {
    val trimmed = headerValue?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (!trimmed.all { char -> char in '0'..'9' }) return null
    val seconds = trimmed.toLongOrNull() ?: return RETRY_AFTER_CAP_MILLIS
    return if (seconds >= RETRY_AFTER_CAP_MILLIS / 1_000L) RETRY_AFTER_CAP_MILLIS else seconds * 1_000L
}

/** The Google error reason of a failed call, or its status when no reason is given. Null when the body has neither. */
internal fun driveErrorReason(body: String): String? {
    val error = errorObject(body) ?: return null
    return error.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")?.takeIf(String::isNotBlank)
        ?: error.optString("status").takeIf(String::isNotBlank)
}

private fun driveErrorStatus(body: String): String? =
    errorObject(body)?.optString("status")?.takeIf(String::isNotBlank)

private fun errorObject(body: String): JSONObject? = try {
    JSONObject(body).optJSONObject("error")
} catch (_: Exception) {
    null
}

/** Attempts of one Drive call, the first included. */
internal const val DRIVE_MAX_ATTEMPTS = 5

/**
 * Retries only [DriveApiResult.Transient] results: up to [maxAttempts] attempts, waiting
 * 1 s, 2 s, 4 s, 8 s (each ±20 % jitter) between them. A server Retry-After is a floor for that wait.
 * [block] is told its 1-based attempt number.
 */
internal suspend fun <T> withRetry(
    maxAttempts: Int = DRIVE_MAX_ATTEMPTS,
    sleeper: suspend (Long) -> Unit = { delay(it) },
    random: () -> Double = Math::random,
    block: suspend (attempt: Int) -> DriveApiResult<T>,
): DriveApiResult<T> {
    var attempt = 1
    while (true) {
        val result = block(attempt)
        result.reportRateLimit()
        if (result !is DriveApiResult.Transient || attempt >= maxAttempts) return result
        val baseMillis = 1_000L shl (attempt - 1)
        val backoffMillis = (baseMillis * (0.8 + 0.4 * random())).roundToLong()
        sleeper(maxOf(backoffMillis, result.retryAfterMillis ?: 0L))
        attempt++
    }
}

/**
 * Whether an attempt of a blob upsert reads the name back before it writes. Only the first attempt of a caller that knows
 * the name is absent skips the read. Every later attempt reads it, so a create whose response was lost is updated, not
 * created a second time. A known [fileId] is updated without a read.
 */
internal fun blobAttemptNeedsLookup(attempt: Int, fileId: String?, knownAbsent: Boolean): Boolean =
    fileId == null && (attempt > 1 || !knownAbsent)

/**
 * One blob upsert with its retries. Each attempt resolves the id ([lookup] when [blobAttemptNeedsLookup] asks for it), then
 * makes one [write]: a create when no id is found, an update of the found id otherwise. [write] is a single request, so
 * every retry goes through the lookup. A failed lookup is returned as it is, never read as "absent".
 */
internal suspend fun upsertBlobWithRetry(
    fileId: String?,
    knownAbsent: Boolean,
    lookup: suspend () -> DriveApiResult<String?>,
    write: suspend (existingFileId: String?) -> DriveApiResult<UploadedBlobInfo>,
    sleeper: suspend (Long) -> Unit = { delay(it) },
    random: () -> Double = Math::random,
): DriveApiResult<UploadedBlobInfo> = withRetry<UploadedBlobInfo>(sleeper = sleeper, random = random) { attempt ->
    val existingFileId = when {
        fileId != null -> fileId
        !blobAttemptNeedsLookup(attempt, fileId, knownAbsent) -> null
        else -> when (val found = lookup()) {
            is DriveApiResult.Success -> found.value
            else -> return@withRetry found.failedAs<UploadedBlobInfo>()
        }
    }
    write(existingFileId)
}

/** Passes a failed result on unchanged, typed for the caller. A successful result is not a failure. */
internal fun <R> DriveApiResult<*>.failedAs(): DriveApiResult<R> = when (this) {
    is DriveApiResult.Failure -> this
    is DriveApiResult.Transient -> this
    DriveApiResult.StorageFull -> DriveApiResult.StorageFull
    DriveApiResult.NotFound -> DriveApiResult.NotFound
    DriveApiResult.Unauthorized -> DriveApiResult.Unauthorized
    is DriveApiResult.Success -> error("A successful Drive result is not a failure.")
}

/**
 * The upload concurrency of one cycle: [maxConcurrency] workers until a Drive call is rate-limited, then one. It travels in
 * the coroutine context so the retry loop, which retries 429s before returning them, can report each one.
 */
internal class DriveRateLimitGate(maxConcurrency: Int) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<DriveRateLimitGate>

    @Volatile
    var concurrency: Int = maxConcurrency
        private set

    fun onRateLimited() {
        concurrency = 1
    }
}

/** Tells the cycle's [DriveRateLimitGate], when there is one, that this result was rate-limited. */
internal suspend fun DriveApiResult<*>.reportRateLimit() {
    if (this is DriveApiResult.Transient && rateLimited) currentCoroutineContext()[DriveRateLimitGate]?.onRateLimited()
}

/**
 * The access token of one run of Drive calls. A call answered 401 gets one fresh token from [refresh] and one retry of
 * that same call; the fresh token then serves the calls after it. A second 401, or no fresh token, stays Unauthorized.
 */
internal class DriveCallAuth(
    accessToken: String,
    val refresh: suspend () -> String?,
) {
    @Volatile
    var accessToken: String = accessToken
        private set

    suspend fun <T> call(block: suspend (accessToken: String) -> DriveApiResult<T>): DriveApiResult<T> {
        val first = block(accessToken)
        val result = if (first !is DriveApiResult.Unauthorized) {
            first
        } else {
            val fresh = refresh() ?: return first
            accessToken = fresh
            block(fresh)
        }
        result.reportRateLimit()
        return result
    }
}
