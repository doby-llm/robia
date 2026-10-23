package com.gusanitolabs.robia.suggestions

import org.junit.Assert.assertEquals
import org.junit.Test

class GpuBenchmarkReportTest {
    private val device = "device=test device (test) API 36"

    @Test
    fun successReportListsBackendLoadImageTimingsPssAndParity() {
        val report = gpuBenchmarkReport(
            deviceLine = device,
            loadMs = 1200L,
            imageMs = listOf(300L, 100L, 200L, 400L, 500L),
            peakPssMb = 512L,
            parity = 0.99123f,
        )

        assertEquals(
            listOf(
                device,
                "backend=gpu vision_backend=gpu vision_tokens=${EmbeddingRecipe.VISION_TOKENS}",
                "load_ms=1200",
                "image_ms median=300 p90=500 (n=5)",
                "peak_pss_mb=512",
                "parity_cosine=0.9912",
            ).joinToString("\n"),
            report,
        )
    }

    @Test
    fun unavailableReportNamesExceptionClassAndMessage() {
        val report = gpuUnavailableReport(device, IllegalStateException("No OpenCL on device"))

        assertEquals("$device\nGPU unavailable: IllegalStateException: No OpenCL on device", report)
    }

    @Test
    fun unavailableReportWithoutMessageSaysNoMessage() {
        val report = gpuUnavailableReport(device, RuntimeException())

        assertEquals("$device\nGPU unavailable: RuntimeException: no message", report)
    }

    @Test
    fun unavailableReportKeepsOneLineAndNoAbsolutePath() {
        val report = gpuUnavailableReport(
            device,
            IllegalStateException("bad\nmodel at /data/user/0/app/files/model.litertlm"),
        )

        assertEquals("$device\nGPU unavailable: IllegalStateException: bad model at <path>", report)
    }
}
