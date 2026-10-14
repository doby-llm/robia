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
        assertEquals(DriveErrorClass.Transient, classify(408, ""))
        assertEquals(DriveErrorClass.Transient, classify(429, ""))
        assertEquals(DriveErrorClass.Transient, classify(500, ""))
        assertEquals(DriveErrorClass.Transient, classify(503, ""))
        assertEquals(DriveErrorClass.Permanent(400), classify(400, ""))
    }

    @Test
    fun forbiddenIsSplitByGoogleErrorReason() {
        assertEquals(DriveErrorClass.Transient, classify(403, forbidden("rateLimitExceeded")))
        assertEquals(DriveErrorClass.Transient, classify(403, forbidden("userRateLimitExceeded")))
        assertEquals(DriveErrorClass.Transient, classify(403, forbidden("backendError")))
        assertEquals(DriveErrorClass.StorageFull, classify(403, forbidden("storageQuotaExceeded")))
        assertEquals(DriveErrorClass.AuthRequired, classify(403, forbidden("forbidden")))
    }

    @Test
    fun forbiddenWithoutReadableReasonRequiresSignIn() {
        assertEquals(DriveErrorClass.AuthRequired, classify(403, ""))
        assertEquals(DriveErrorClass.AuthRequired, classify(403, "<html>Forbidden</html>"))
        assertEquals(DriveErrorClass.AuthRequired, classify(403, """{"error":{"code":403,"status":"PERMISSION_DENIED"}}"""))
    }

    @Test
    fun forbiddenFallsBackToErrorStatusWhenNoReasonIsGiven() {
        val body = """{"error":{"code":403,"status":"RESOURCE_EXHAUSTED"}}"""
        assertEquals(DriveErrorClass.AuthRequired, classify(403, body))
        assertEquals(DriveErrorClass.Transient, classify(403, """{"error":{"status":"rateLimitExceeded"}}"""))
    }

    @Test
    fun networkExceptionsAreTransientAndOtherThrowablesAreNot() {
        assertEquals(DriveErrorClass.Transient, classify(SocketTimeoutException("read timed out")))
        assertEquals(DriveErrorClass.Transient, classify(UnknownHostException("www.googleapis.com")))
        assertEquals(DriveErrorClass.Permanent(code = 0), classify(IllegalStateException("bad state")))
    }

    private fun forbidden(reason: String): String =
        """{"error":{"code":403,"errors":[{"domain":"usageLimits","reason":"$reason"}],"status":"PERMISSION_DENIED"}}"""
}
