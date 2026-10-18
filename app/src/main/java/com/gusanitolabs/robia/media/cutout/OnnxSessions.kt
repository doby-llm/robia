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
        // Filled one tensor at a time so that a createTensor failure still closes the ones already made.
        val tensors = LinkedHashMap<String, OnnxTensor>()
        try {
            inputs.forEach { (name, input) ->
                tensors[name] = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.data), input.shape)
            }
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

    /** Closes every session even when one close fails; [options] is always closed. The first failure is rethrown. */
    override fun close() {
        var failure: Throwable? = null
        for (session in opened) {
            try {
                session.close()
            } catch (closeFailure: Throwable) {
                failure = failure?.apply { addSuppressed(closeFailure) } ?: closeFailure
            }
        }
        opened.clear()
        try {
            options.close()
        } catch (closeFailure: Throwable) {
            failure = failure?.apply { addSuppressed(closeFailure) } ?: closeFailure
        }
        failure?.let { throw it }
    }

    internal companion object {
        const val U2NETP_FILE = "u2netp.onnx"
        const val DFINE_FILE = "dfine_s_obj365.onnx"
        const val SAM_ENCODER_FILE = "mobile_sam_image_encoder.onnx"
        const val SAM_DECODER_FILE = "sam_mask_decoder_multi.onnx"
        private const val INTRA_OP_THREADS = 4
    }
}
