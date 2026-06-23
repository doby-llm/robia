package com.gusanitolabs.robia.media.additionalinfo

import android.content.Context
import android.net.Uri
import com.gusanitolabs.robia.core.model.AdditionalInfoDetectionResult
import com.gusanitolabs.robia.core.model.GarmentTag
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TfliteAdditionalInfoDetector(
    private val configLoader: (Context) -> AdditionalInfoModelConfig = AdditionalInfoModelConfigLoader::load,
) : AdditionalInfoDetector {
    override suspend fun detect(
        context: Context,
        imageUri: Uri,
        availableTags: List<GarmentTag>,
    ): AdditionalInfoDetectionResult {
        val config = runCatching { configLoader(context) }.getOrElse {
            return AdditionalInfoDetectionResult(failureReason = AdditionalInfoDetectionResult.FailureReason.ManifestInvalid)
        }
        if (!AdditionalInfoModelConfigLoader.validate(config)) {
            return AdditionalInfoDetectionResult(failureReason = AdditionalInfoDetectionResult.FailureReason.ManifestInvalid)
        }
        val input = AdditionalInfoImagePreprocessor.preprocess(context, imageUri, config.input)
            ?: return AdditionalInfoDetectionResult(failureReason = AdditionalInfoDetectionResult.FailureReason.PreprocessingFailed)
        val rawScores = runCatching { runModel(context, config, input) }.getOrElse {
            return AdditionalInfoDetectionResult(failureReason = AdditionalInfoDetectionResult.FailureReason.OutputShapeMismatch)
        }
        val prediction = AdditionalInfoTagMapper.map(rawScores, availableTags, config)
            ?: return AdditionalInfoDetectionResult(failureReason = AdditionalInfoDetectionResult.FailureReason.MappingFailed)
        return AdditionalInfoDetectionResult(prediction = prediction)
    }

    internal fun runModel(
        context: Context,
        config: AdditionalInfoModelConfig,
        input: ByteBuffer,
    ): Map<String, FloatArray> {
        val interpreter = Interpreter(loadModel(context))
        try {
            val outputMap = mutableMapOf<Int, Any>()
            val outputNamesByIndex = mutableMapOf<Int, String>()
            for (index in 0 until interpreter.outputTensorCount) {
                val tensor = interpreter.getOutputTensor(index)
                val shape = tensor.shape().toList()
                val head = config.heads.singleOrNull { candidate -> candidate.shape == shape }
                    ?: error("Unexpected additional-info output tensor ${tensor.name()} with shape $shape")
                outputMap[index] = Array(shape.first()) { FloatArray(head.width) }
                outputNamesByIndex[index] = head.name
            }
            check(outputNamesByIndex.values.toSet() == config.heads.map(AdditionalInfoHeadSpec::name).toSet()) {
                "Missing additional-info output tensors"
            }
            input.rewind()
            interpreter.runForMultipleInputsOutputs(arrayOf(input), outputMap)
            return outputNamesByIndex.mapValues { (index, _) ->
                @Suppress("UNCHECKED_CAST")
                (outputMap.getValue(index) as Array<FloatArray>)[0]
            }
        } finally {
            interpreter.close()
        }
    }

    private fun loadModel(context: Context): MappedByteBuffer = context.assets
        .openFd(AdditionalInfoModelAssets.MODEL_FILE)
        .use { assetFileDescriptor ->
            FileInputStream(assetFileDescriptor.fileDescriptor).channel.use { channel ->
                channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.declaredLength,
                )
            }
        }
}
