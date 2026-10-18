package com.gusanitolabs.robia.media.cutout

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer

/** A float tensor input: flat row-major [data] with its [shape]. */
internal class FloatTensor(val data: FloatArray, val shape: LongArray)

/**
 * The ONNX models of the precise pack, opened per photo. Sessions are created on first use (MobileSAM only when a
 * hanger is found) and all closed by [close], so no model stays resident between photos.
 */
internal class OnnxSessions(private val packDir: File) : AutoCloseable {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(INTRA_OP_THREADS) }
    private val opened = ArrayList<OrtSession>()

    val saliency: OrtSession by lazy { open(U2NETP_FILE) }
    val hangerDetector: OrtSession by lazy { open(DFINE_FILE) }
    val samEncoder: OrtSession by lazy { open(SAM_ENCODER_FILE) }
    val samDecoder: OrtSession by lazy { open(SAM_DECODER_FILE) }

    /** Runs [session] and returns every output flattened, in model output order. */
    fun run(session: OrtSession, inputs: Map<String, FloatTensor>): List<FloatArray> {
        val tensors = inputs.mapValues { (_, input) -> OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.data), input.shape) }
        try {
            session.run(tensors).use { result ->
                return (0 until result.size()).map { index ->
                    val buffer = (result.get(index) as OnnxTensor).floatBuffer.duplicate()
                    FloatArray(buffer.remaining()).also { buffer.get(it) }
                }
            }
        } finally {
            tensors.values.forEach { it.close() }
        }
    }

    private fun open(fileName: String): OrtSession =
        environment.createSession(File(packDir, fileName).absolutePath, options).also { opened += it }

    override fun close() {
        opened.forEach { it.close() }
        opened.clear()
        options.close()
    }

    internal companion object {
        const val U2NETP_FILE = "u2netp.onnx"
        const val DFINE_FILE = "dfine_s_obj365.onnx"
        const val SAM_ENCODER_FILE = "mobile_sam_image_encoder.onnx"
        const val SAM_DECODER_FILE = "sam_mask_decoder_multi.onnx"
        private const val INTRA_OP_THREADS = 4
    }
}
