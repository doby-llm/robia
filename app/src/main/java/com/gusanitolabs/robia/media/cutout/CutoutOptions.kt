package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.core.model.BatchCutoutMode
import com.gusanitolabs.robia.core.model.CutoutEdges
import com.gusanitolabs.robia.core.model.CutoutEngineChoice
import com.gusanitolabs.robia.core.model.HangerSensitivity
import com.gusanitolabs.robia.core.model.RobiaSettings

/** S3 detector score that a hanger must reach; Normal is the value the precise pipeline was measured with. */
internal const val HANGER_THRESHOLD_NORMAL = 0.25f

/** Lower bar for High sensitivity (evidence: `artifacts/inv-r3-hanger-sensitivity.md`). */
internal const val HANGER_THRESHOLD_HIGH = 0.12f

/** How one cutout runs: which engine, whether hangers are removed, the detector threshold and the edge profile. */
data class CutoutOptions(
    val usePrecise: Boolean,
    val removeHanger: Boolean,
    val hangerThreshold: Float,
    val edges: CutoutEdges,
)

/** Options for a photo from the saved settings; the precise engine needs the pack to be ready and the Automatic method. */
fun cutoutOptionsOf(settings: RobiaSettings, packReady: Boolean): CutoutOptions = CutoutOptions(
    usePrecise = useEnginePrecise(packReady, settings.cutoutEngine == CutoutEngineChoice.Automatic),
    removeHanger = settings.cutoutRemoveHanger,
    hangerThreshold = when (settings.cutoutHangerSensitivity) {
        HangerSensitivity.Normal -> HANGER_THRESHOLD_NORMAL
        HangerSensitivity.High -> HANGER_THRESHOLD_HIGH
    },
    edges = settings.cutoutEdges,
)

/** Cutouts allowed in flight for a batch: one for Memory saver and for the precise engine, two otherwise (B01). */
fun batchCutouts(mode: BatchCutoutMode, preciseActive: Boolean): Int =
    if (mode == BatchCutoutMode.MemorySaver) 1 else if (preciseActive) 1 else 2
