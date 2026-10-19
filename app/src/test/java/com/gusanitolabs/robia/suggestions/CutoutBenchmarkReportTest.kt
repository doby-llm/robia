package com.gusanitolabs.robia.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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

    @Test
    fun failureReasonNamesTheRootCauseWithClassAndMessageOnly() {
        val failure = ExceptionInInitializerError(IllegalStateException("no caller found on the stack for: x7.c"))

        assertEquals(
            "ExceptionInInitializerError: no message caused by IllegalStateException: no caller found on the stack for: x7.c",
            failureReason(failure, secrets = emptyList()),
        )
    }

    @Test
    fun failureReasonFollowsAtMostFourCauses() {
        val deepest = RuntimeException("level 6")
        val chain = (5 downTo 0).fold(deepest) { cause, level -> RuntimeException("level $level", cause) }

        assertEquals(
            "RuntimeException: level 0 caused by RuntimeException: level 1 caused by RuntimeException: level 2 " +
                "caused by RuntimeException: level 3 caused by RuntimeException: level 4",
            failureReason(chain, secrets = emptyList()),
        )
    }

    @Test
    fun failureReasonReplacesInputUriAndAbsolutePathsAndDropsLineBreaks() {
        val uri = "content://media/external/images/media/1234"
        val linkage = UnsatisfiedLinkError(
            "dlopen failed: library \"/data/app/x/lib/arm64/libonnxruntime4j_jni.so\" not found\nsecond line",
        )
        val opened = IllegalArgumentException("cannot open $uri")

        assertEquals(
            "UnsatisfiedLinkError: dlopen failed: library \"<path>\" not found second line",
            failureReason(linkage, secrets = listOf(uri)),
        )
        assertEquals("IllegalArgumentException: cannot open <uri>", failureReason(opened, secrets = listOf(uri)))
    }

    @Test
    fun engineRuntimeExceptionEndsWithTheFirstThreeRootCauseFramesOfGoogleOnnxOrOurPackage() {
        val root = RuntimeException("Field typeUrl_ for com.google.protobuf.g not found.")
        root.stackTrace = arrayOf(
            StackTraceElement("java.util.ArrayList", "get", "ArrayList.java", 1),
            StackTraceElement("com.google.protobuf.MessageSchema", "<init>", "MessageSchema.java", 2),
            StackTraceElement("kotlin.collections.CollectionsKt", "first", "CollectionsKt.kt", 3),
            StackTraceElement("com.google.protobuf.GeneratedMessageLite", "newMessageInfo", "GeneratedMessageLite.java", 4),
            StackTraceElement("ai.onnxruntime.OrtSession", "run", "OrtSession.java", 5),
            StackTraceElement(
                "com.gusanitolabs.robia.media.cutout.PreciseCutoutEngine",
                "removeBackground",
                "PreciseCutoutEngine.kt",
                6,
            ),
        )

        assertEquals(
            "RuntimeException: Field typeUrl_ for com.google.protobuf.g not found. " +
                "frames=com.google.protobuf.MessageSchema.init<com.google.protobuf.GeneratedMessageLite.newMessageInfo" +
                "<ai.onnxruntime.OrtSession.run",
            failureReason(root, secrets = emptyList(), fromEngine = true),
        )
    }

    @Test
    fun engineFramesComeFromTheRootCauseNotTheWrapper() {
        val root = RuntimeException("deepest").apply {
            stackTrace = arrayOf(StackTraceElement("com.google.mediapipe.framework.Graph", "nativeCreateGraph", "Graph.java", 1))
        }
        val wrapper = IllegalStateException("wrapped", root).apply {
            stackTrace = arrayOf(StackTraceElement("com.gusanitolabs.robia.media.cutout.CutoutRun", "alpha", "CutoutRun.kt", 2))
        }

        assertEquals(
            "IllegalStateException: wrapped caused by RuntimeException: deepest " +
                "frames=com.google.mediapipe.framework.Graph.nativeCreateGraph",
            failureReason(wrapper, secrets = emptyList(), fromEngine = true),
        )
    }

    @Test
    fun engineFramesAreOmittedForNonRuntimeExceptionsAndWhenNoFrameMatches() {
        val linkage = UnsatisfiedLinkError("dlopen failed")
        val io = IOException("read failed")
        val unmatched = RuntimeException("no app frames").apply {
            stackTrace = arrayOf(StackTraceElement("java.util.HashMap", "get", "HashMap.java", 1))
        }

        assertEquals("UnsatisfiedLinkError: dlopen failed", failureReason(linkage, secrets = emptyList(), fromEngine = true))
        assertEquals("IOException: read failed", failureReason(io, secrets = emptyList(), fromEngine = true))
        assertEquals("RuntimeException: no app frames", failureReason(unmatched, secrets = emptyList(), fromEngine = true))
    }

    @Test
    fun runtimeExceptionOutsideTheEngineGetsNoFrames() {
        val failure = RuntimeException("Unable to open source photo")

        assertEquals(
            "RuntimeException: Unable to open source photo",
            failureReason(failure, secrets = emptyList()),
        )
    }

    @Test
    fun cyclicCauseChainStillFormatsWithoutLooping() {
        val first = RuntimeException("first")
        val second = RuntimeException("second")
        first.initCause(second)
        second.initCause(first)

        val reason = failureReason(first, secrets = emptyList(), fromEngine = true)

        assertTrue(reason.startsWith("RuntimeException: first caused by RuntimeException: second"))
        assertTrue(reason.contains(" frames=com.gusanitolabs.robia.suggestions.CutoutBenchmarkReportTest."))
    }
}
