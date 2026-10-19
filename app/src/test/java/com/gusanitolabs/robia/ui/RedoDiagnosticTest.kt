package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.CutoutEdges
import com.gusanitolabs.robia.media.cutout.CutoutOptions
import com.gusanitolabs.robia.media.cutout.HANGER_THRESHOLD_NORMAL
import com.gusanitolabs.robia.media.cutout.RedoCutoutChoice
import com.gusanitolabs.robia.media.cutout.redoCutoutOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The Developer diagnostics line after a Redo: it must say which engine and hanger setting really ran. */
class RedoDiagnosticTest {
    private val saved = CutoutOptions(
        usePrecise = false,
        removeHanger = true,
        hangerThreshold = HANGER_THRESHOLD_NORMAL,
        edges = CutoutEdges.Soft,
    )

    private fun lineFor(choice: RedoCutoutChoice, elapsedMs: Long = 1234L): String =
        redoDoneDiagnostic(choice, redoCutoutOptions(choice, saved), elapsedMs)

    @Test
    fun standardReportsNoPreciseEngineAndNoHangerRemovalWhateverTheSavedSetting() {
        assertEquals(
            "redo_cutout_done: choice=Standard precise=false removeHanger=false ms=1234",
            lineFor(RedoCutoutChoice.Standard),
        )
    }

    @Test
    fun preciseRemovesHangers() {
        assertEquals(
            "redo_cutout_done: choice=Precise precise=true removeHanger=true ms=1234",
            lineFor(RedoCutoutChoice.Precise),
        )
    }

    @Test
    fun preciseKeepHangerKeepsThePreciseEngineWithoutHangerRemoval() {
        assertEquals(
            "redo_cutout_done: choice=PreciseKeepHanger precise=true removeHanger=false ms=1234",
            lineFor(RedoCutoutChoice.PreciseKeepHanger),
        )
    }

    @Test
    fun theElapsedTimeIsTheMillisecondsGiven() {
        assertEquals(
            "redo_cutout_done: choice=Precise precise=true removeHanger=true ms=0",
            lineFor(RedoCutoutChoice.Precise, elapsedMs = 0L),
        )
    }

    @Test
    fun theLineHasNoPathsOrUris() {
        for (choice in RedoCutoutChoice.entries) {
            val line = lineFor(choice)
            assertFalse(line, line.contains('/'))
            assertFalse(line, line.contains("://"))
        }
    }
}
