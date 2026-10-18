package com.gusanitolabs.robia.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutBenchmarkReportTest {
    private val device = "device=test device (test) API 36"

    @Test
    fun failedInputLineNamesKindAndNameWithReason() {
        val line = failedInputLine(
            InputFailure(kind = "photo", name = "content:1234", reason = "IllegalStateException: Unable to open source photo"),
        )

        assertEquals(
            "failed_input=photo:content:1234 reason=IllegalStateException: Unable to open source photo",
            line,
        )
    }

    @Test
    fun reportWithScenesOkAndPhotosFailedKeepsSceneMediansAndCountsSkippedPhotos() {
        val failures = listOf(
            InputFailure(kind = "photo", name = "content:1", reason = "IllegalStateException: Unable to open source photo"),
            InputFailure(kind = "photo", name = "content:2", reason = "FileNotFoundException: missing"),
        )

        val report = cutoutBenchmarkReport(
            deviceLine = device,
            runInputs = 3,
            okInputs = 3,
            rounds = 2,
            totalMs = listOf(10L, 30L, 20L, 40L, 50L, 60L),
            stageMs = mapOf("decode" to listOf(1L, 2L, 3L)),
            fallbacks = 0,
            peakPssMb = 120L,
            failures = failures,
        ).lines()

        assertEquals(device, report[0])
        assertEquals("inputs_ok=3 inputs_failed=2", report[1])
        assertEquals("photos_skipped=2", report[2])
        assertEquals("failed_input=photo:content:1 reason=IllegalStateException: Unable to open source photo", report[3])
        assertEquals("failed_input=photo:content:2 reason=FileNotFoundException: missing", report[4])
        assertEquals("samples=6 (3 inputs x 2 rounds)", report[5])
        assertEquals("total_ms median=40 p90=60", report[6])
        assertEquals("stage_decode median=2 p90=3", report[7])
        assertEquals("fallbacks=0", report[8])
        assertEquals("peak_pss_mb=120", report[9])
        assertFalse(report.any { it.startsWith("error=") })
    }

    @Test
    fun reportWithNoInputOkSaysSoAndOmitsTimings() {
        val failures = listOf(
            InputFailure(kind = "scene", name = "sample.png", reason = "IOException: no asset"),
        )

        val report = cutoutBenchmarkReport(
            deviceLine = device,
            runInputs = 0,
            okInputs = 0,
            rounds = 2,
            totalMs = emptyList(),
            stageMs = emptyMap(),
            fallbacks = 0,
            peakPssMb = 80L,
            failures = failures,
        ).lines()

        assertEquals(
            listOf(
                device,
                "inputs_ok=0 inputs_failed=1",
                "error=no inputs succeeded",
                "failed_input=scene:sample.png reason=IOException: no asset",
                "samples=0 (0 inputs x 2 rounds)",
                "fallbacks=0",
                "peak_pss_mb=80",
            ),
            report,
        )
        assertTrue(report.none { it.contains("median") })
        assertFalse(report.any { it.startsWith("photos_skipped=") })
    }
}
