package com.gusanitolabs.robia.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DriveHttpErrorsTest {
    @Test
    fun httpStatusesClassifyByTheSpecTable() {
        assertEquals(DriveErrorClass.AuthRequired, classify(401, ""))
        assertEquals(DriveErrorClass.NotFound, classify(404, ""))
        assertEquals(DriveErrorClass.Transient(), classify(408, ""))
        assertEquals(DriveErrorClass.Transient(), classify(429, ""))
        assertEquals(DriveErrorClass.Transient(), classify(500, ""))
        assertEquals(DriveErrorClass.Transient(), classify(503, ""))
        assertEquals(DriveErrorClass.Permanent(400), classify(400, ""))
    }

    @Test
    fun retryAfterTravelsWithTheTransientClass() {
        assertEquals(DriveErrorClass.Transient(retryAfterMillis = 5_000L), classify(429, "", retryAfterMillis = 5_000L))
        assertEquals(DriveErrorClass.Transient(retryAfterMillis = 2_000L), classify(403, forbidden("rateLimitExceeded"), retryAfterMillis = 2_000L))
    }

    @Test
    fun forbiddenIsSplitByGoogleErrorReason() {
        assertEquals(DriveErrorClass.Transient(), classify(403, forbidden("rateLimitExceeded")))
        assertEquals(DriveErrorClass.Transient(), classify(403, forbidden("userRateLimitExceeded")))
        assertEquals(DriveErrorClass.Transient(), classify(403, forbidden("backendError")))
        assertEquals(DriveErrorClass.Transient(), classify(403, forbidden("dailyLimitExceeded")))
        assertEquals(DriveErrorClass.Transient(), classify(403, forbidden("sharingRateLimitExceeded")))
        assertEquals(DriveErrorClass.Transient(), classify(403, forbidden("quotaExceeded")))
        assertEquals(DriveErrorClass.StorageFull, classify(403, forbidden("storageQuotaExceeded")))
        assertEquals(DriveErrorClass.AuthRequired, classify(403, forbidden("forbidden")))
        assertEquals(DriveErrorClass.AuthRequired, classify(403, forbidden("insufficientPermissions")))
    }

    @Test
    fun forbiddenWithAnUnlistedReasonIsAPermanentFailureNotASignInError() {
        assertEquals(DriveErrorClass.Permanent(403), classify(403, forbidden("accessNotConfigured")))
        assertEquals(DriveErrorClass.Permanent(403), classify(403, ""))
        assertEquals(DriveErrorClass.Permanent(403), classify(403, "<html>Forbidden</html>"))
        assertEquals(DriveErrorClass.Permanent(403), classify(403, """{"error":{"code":403,"status":"PERMISSION_DENIED"}}"""))
    }

    @Test
    fun forbiddenFallsBackToErrorStatusWhenNoReasonIsGiven() {
        assertEquals(DriveErrorClass.Transient(), classify(403, """{"error":{"code":403,"status":"RESOURCE_EXHAUSTED"}}"""))
        assertEquals(DriveErrorClass.Transient(), classify(403, """{"error":{"status":"rateLimitExceeded"}}"""))
    }

    @Test
    fun resourceExhaustedStatusIsTransientWhateverTheReason() {
        val body = """{"error":{"code":403,"errors":[{"reason":"accessNotConfigured"}],"status":"RESOURCE_EXHAUSTED"}}"""
        assertEquals(DriveErrorClass.Transient(), classify(403, body))
    }

    @Test
    fun networkExceptionsAreTransientAndOtherThrowablesAreNot() {
        assertEquals(DriveErrorClass.Transient(), classify(SocketTimeoutException("read timed out")))
        assertEquals(DriveErrorClass.Transient(), classify(UnknownHostException("www.googleapis.com")))
        assertEquals(DriveErrorClass.Permanent(code = 0), classify(IllegalStateException("bad state")))
    }

    @Test
    fun httpDetailNamesTheCodeAndAPlainReasonOnly() {
        assertEquals("http=403 reason=dailyLimitExceeded", driveHttpDetail(403, forbidden("dailyLimitExceeded")))
        assertEquals("http=401 reason=none", driveHttpDetail(401, "<html>token abc</html>"))
        assertEquals("http=403 reason=none", driveHttpDetail(403, """{"error":{"errors":[{"reason":"a b:Bearer x"}]}}"""))
    }

    private fun forbidden(reason: String): String =
        """{"error":{"code":403,"errors":[{"domain":"usageLimits","reason":"$reason"}],"status":"PERMISSION_DENIED"}}"""
}
