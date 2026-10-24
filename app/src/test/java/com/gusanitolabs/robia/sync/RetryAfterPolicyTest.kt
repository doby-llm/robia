package com.gusanitolabs.robia.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class RetryAfterPolicyTest {
    @Test
    fun secondsFormIsParsedAndCappedAtOneMinute() {
        assertEquals(5_000L, parseRetryAfterMillis("5"))
        assertEquals(7_000L, parseRetryAfterMillis(" 7 "))
        assertEquals(0L, parseRetryAfterMillis("0"))
        assertEquals(RETRY_AFTER_CAP_MILLIS, parseRetryAfterMillis("3600"))
        assertEquals(RETRY_AFTER_CAP_MILLIS, parseRetryAfterMillis("99999999999999999999999"))
    }

    @Test
    fun httpDateGarbageAndMissingValuesGiveNull() {
        assertNull(parseRetryAfterMillis("Wed, 21 Oct 2015 07:28:00 GMT"))
        assertNull(parseRetryAfterMillis("-1"))
        assertNull(parseRetryAfterMillis("soon"))
        assertNull(parseRetryAfterMillis("1.5"))
        assertNull(parseRetryAfterMillis(""))
        assertNull(parseRetryAfterMillis(null))
    }

    @Test
    fun retryAfterIsAFloorOnTheBackoffThroughTheSleeper() = runBlocking {
        val delays = mutableListOf<Long>()
        val calls = AtomicInteger(0)
        val result = withRetry(sleeper = { delays += it }, random = { 0.5 }) {
            if (calls.incrementAndGet() == 1) {
                DriveApiResult.Transient(IOException("HTTP 429"), retryAfterMillis = 3_000L)
            } else {
                DriveApiResult.Success("ok")
            }
        }

        assertEquals(DriveApiResult.Success("ok"), result)
        assertEquals(listOf(3_000L), delays)
    }

    @Test
    fun backoffWaitsWhenItIsLongerThanRetryAfter() = runBlocking {
        val delays = mutableListOf<Long>()
        val calls = AtomicInteger(0)
        withRetry(sleeper = { delays += it }, random = { 0.5 }) {
            if (calls.incrementAndGet() == 1) {
                DriveApiResult.Transient(IOException("HTTP 503"), retryAfterMillis = 500L)
            } else {
                DriveApiResult.Success("ok")
            }
        }

        assertEquals(listOf(1_000L), delays)
    }

    @Test
    fun retryAfterAboveTheCapWaitsTheCap() = runBlocking {
        val delays = mutableListOf<Long>()
        val calls = AtomicInteger(0)
        withRetry(sleeper = { delays += it }, random = { 0.5 }) {
            if (calls.incrementAndGet() == 1) {
                DriveApiResult.Transient(IOException("HTTP 429"), retryAfterMillis = RETRY_AFTER_CAP_MILLIS)
            } else {
                DriveApiResult.Success("ok")
            }
        }

        assertEquals(listOf(RETRY_AFTER_CAP_MILLIS), delays)
    }
}
