package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.suggestions.model.HttpModelSource
import com.gusanitolabs.robia.suggestions.model.ModelState
import com.gusanitolabs.robia.suggestions.model.ModelStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File

/** One pinned file of the precise pack. [sha256] and [sizeBytes] were checked with curl before pinning (SG2a V-SG2-1). */
internal data class CutoutPackFile(
    val fileName: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
)

/** The precise pack manifest (TS§SG2a). ONNX models are downloaded; `magic_touch.tflite` is bundled in the APK. */
internal object CutoutPackManifest {
    const val DIRECTORY = "cutout_pack"

    val FILES: List<CutoutPackFile> = listOf(
        CutoutPackFile(
            fileName = "u2netp.onnx",
            url = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx",
            sha256 = "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8",
            sizeBytes = 4_574_861L,
        ),
        CutoutPackFile(
            fileName = "dfine_s_obj365.onnx",
            url = "https://huggingface.co/onnx-community/dfine_s_obj365-ONNX/resolve/a61e4cdfe4f9d3188a305d91e37dbf38688ffbb8/onnx/model.onnx",
            sha256 = "372feaa33ac6ba67d7df8589628f6abc395b3c6981c4edc70dfcfe2949751120",
            sizeBytes = 42_123_225L,
        ),
        CutoutPackFile(
            fileName = "mobile_sam_image_encoder.onnx",
            url = "https://huggingface.co/Acly/MobileSAM/resolve/0d3b403339b4674a82493d5e97964dd78089ddc8/mobile_sam_image_encoder.onnx",
            sha256 = "580f5fb648ea1062c0aabc26217aed56921985f03f0cbbd852bba81d760cc749",
            sizeBytes = 28_157_093L,
        ),
        CutoutPackFile(
            fileName = "sam_mask_decoder_multi.onnx",
            url = "https://huggingface.co/Acly/MobileSAM/resolve/0d3b403339b4674a82493d5e97964dd78089ddc8/sam_mask_decoder_multi.onnx",
            sha256 = "8976b90a87ba50a6a72217a5ff994f7d25ce16f2229fcc1ed259e1294c622ffe",
            sizeBytes = 16_496_559L,
        ),
    )
}

/**
 * The downloadable precise pack in `filesDir/cutout_pack/`: one [ModelStore] per file, each sha256-pinned. The pack is
 * ready only when every file is ready. Downloads run one file after another under the same unmetered rule as the
 * suggestions model.
 */
internal class CutoutPack(
    private val rootDir: File,
    isUnmetered: () -> Boolean,
) {
    private val stores: List<ModelStore> = CutoutPackManifest.FILES.map { file ->
        ModelStore(
            rootDir = rootDir,
            source = HttpModelSource(file.url),
            isUnmetered = isUnmetered,
            expectedSha256 = file.sha256,
            fileName = file.fileName,
        )
    }

    /** True once every file is verified on disk. */
    val ready: Flow<Boolean> = combine(stores.map { it.state }) { states -> states.all { it is ModelState.Ready } }

    /** The first file that is not ready yet (for progress text), or Ready when the whole pack is. */
    val state: Flow<ModelState> = combine(stores.map { it.state }) { states -> states.firstOrNull { it !is ModelState.Ready } ?: states.first() }

    fun isReady(): Boolean = stores.all { it.state.value is ModelState.Ready }

    /** Downloads the missing files in order; stops at the first one that is not Ready and returns its state. */
    suspend fun download(allowMetered: Boolean = false): ModelState {
        for (store in stores) {
            val outcome = store.download(allowMetered)
            if (outcome !is ModelState.Ready) return outcome
        }
        return ModelState.Ready(rootDir.path)
    }

    /** Removes all pack files. Callers switch the precise engine off first. */
    suspend fun delete() {
        stores.forEach { it.delete() }
    }
}
