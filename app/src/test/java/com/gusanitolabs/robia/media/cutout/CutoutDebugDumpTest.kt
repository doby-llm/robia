package com.gusanitolabs.robia.media.cutout

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/** V-K01-2: the stage file names match `tools/cutout-diag/compare.py`, the report keys and the no-op sink. */
class CutoutDebugDumpTest {
    private val expectedFileNames = mapOf(
        "s1_saliency" to "s1_saliency.png",
        "s2_selection" to "s2_selection.png",
        "s3_hanger" to "s3_hanger.json",
        "s4_kill" to "s4_kill.png",
        "s4b_filled" to "s4b_filled.png",
        "s4b_reselect" to "s4b_reselect.png",
        "s5_alpha" to "s5_alpha.png",
        "final" to "final.png",
        "mlkit" to "mlkit.png",
    )

    @Test
    fun stageFileName_isTheTableOfCompareStagesPlusTheExtraDumpFiles() {
        expectedFileNames.forEach { (stage, fileName) -> assertEquals(fileName, stageFileName(stage)) }

        val compareStages = Regex("STAGES = \\[(.*?)]").find(compareScript())!!.groupValues[1]
            .split(",")
            .map { it.trim().trim('\'') }
        assertTrue(compareStages.isNotEmpty())
        compareStages.forEach { stage -> assertEquals(stage + ".png", stageFileName(stage)) }
    }

    @Test(expected = IllegalStateException::class)
    fun stageFileName_rejectsAStageOutsideTheTable() {
        stageFileName("s6_unknown")
    }

    @Test
    fun reportJson_hasTheReferenceKeysAndNullsWhenNoHangerWasRemoved() {
        val report = JSONObject(
            reportJson(
                width = 1024,
                height = 768,
                s2Points = listOf(S2Point(10, 20, accepted = true), S2Point(30, 40, accepted = false)),
                hangerBox = null,
                kill = false,
                bandBottom = null,
                seconds = 4.5,
                opaqueFractionOfS2 = 0.98,
                engine = "precise",
                fallback = "none",
            ),
        )

        assertEquals(
            setOf("work", "s2_points", "hanger_box", "kill", "band_bottom", "seconds", "opaque_fraction_of_s2", "engine", "fallback"),
            report.keySet(),
        )
        assertEquals(1024, report.getJSONArray("work").getInt(0))
        assertEquals(768, report.getJSONArray("work").getInt(1))
        assertEquals(2, report.getJSONArray("s2_points").length())
        assertTrue(report.getJSONArray("s2_points").getJSONObject(0).getBoolean("accepted"))
        assertTrue(report.isNull("hanger_box"))
        assertFalse(report.getBoolean("kill"))
        assertTrue(report.isNull("band_bottom"))
        assertEquals("precise", report.getString("engine"))
        assertEquals("none", report.getString("fallback"))
        assertFalse(report.has("error"))
    }

    @Test
    fun reportJson_carriesTheHangerBoxBandAndErrorWhenGiven() {
        val report = JSONObject(
            reportJson(
                width = 10,
                height = 10,
                s2Points = emptyList(),
                hangerBox = intArrayOf(1, 2, 3, 4),
                kill = true,
                bandBottom = 7,
                seconds = 0.0,
                opaqueFractionOfS2 = 0.0,
                engine = "mlkit",
                fallback = "error",
                error = "IllegalStateException: boom",
            ),
        )

        assertEquals(4, report.getJSONArray("hanger_box").length())
        assertTrue(report.getBoolean("kill"))
        assertEquals(7, report.getInt("band_bottom"))
        assertEquals("IllegalStateException: boom", report.getString("error"))
    }

    @Test
    fun hangerJson_isTheBoxOrNull() {
        assertEquals("""{"box":null}""", hangerJson(null))
        assertEquals("""{"box":[1,2,3,4]}""", hangerJson(intArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun noStageSink_isASingletonThatWritesNothingAndTheEngineSkipsIt() {
        assertSame(NoStageSink, NoStageSink)
        assertFalse(wantsStages(NoStageSink))

        NoStageSink.mask("s1_saliency", floatArrayOf(0.5f), 1, 1)
        NoStageSink.image("final", intArrayOf(0), 1, 1)
        NoStageSink.json("s3_hanger", "{}")

        val recording = object : StageSink {
            override fun mask(stage: String, values: FloatArray, w: Int, h: Int) = Unit
            override fun image(stage: String, argb: IntArray, w: Int, h: Int) = Unit
            override fun json(stage: String, json: String) = Unit
        }
        assertTrue(wantsStages(recording))
    }

    /** The repository copy of compare.py; the unit tests run from the app module, so the repo root is one level up. */
    private fun compareScript(): String {
        val candidates = listOf(Path.of("../tools/cutout-diag/compare.py"), Path.of("tools/cutout-diag/compare.py"))
        return candidates.first { it.toFile().exists() }.toFile().readText()
    }
}
