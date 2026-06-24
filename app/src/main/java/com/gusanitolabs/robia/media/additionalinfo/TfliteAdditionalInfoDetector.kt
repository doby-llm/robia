package com.gusanitolabs.robia.media.additionalinfo

import android.content.Context
import android.net.Uri
import com.gusanitolabs.robia.core.model.AdditionalInfoDetectionDebug
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
        val input = AdditionalInfoImagePreprocessor.preprocessWithDiagnostics(context, imageUri, config.input)
            ?: return AdditionalInfoDetectionResult(failureReason = AdditionalInfoDetectionResult.FailureReason.PreprocessingFailed)
        val baseDebug = input.toDebug(imageUri, config)
        val rawScores = runCatching { runModel(context, config, input.tensor) }.getOrElse {
            return AdditionalInfoDetectionResult(
                failureReason = AdditionalInfoDetectionResult.FailureReason.OutputShapeMismatch,
                debug = baseDebug,
            )
        }
        val debug = baseDebug.copy(outputShapes = rawScores.mapValues { (_, scores) -> listOf(1, scores.size) })
        val prediction = AdditionalInfoTagMapper.map(
            scoresByHead = rawScores,
            availableTagIds = availableTags.map(GarmentTag::id).toSet(),
            config = config,
        ) ?: return AdditionalInfoDetectionResult(
                failureReason = AdditionalInfoDetectionResult.FailureReason.MappingFailed,
                debug = debug,
            )
        return AdditionalInfoDetectionResult(prediction = prediction, debug = debug)
    }

    internal fun runModel(
        context: Context,
        config: AdditionalInfoModelConfig,
        input: ByteBuffer,
    ): Map<String, FloatArray> {
        val interpreter = Interpreter(loadModel(context, config))
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
            return outputNamesByIndex.entries.associate { (index, name) ->
                @Suppress("UNCHECKED_CAST")
                name to (outputMap.getValue(index) as Array<FloatArray>)[0]
            }
        } finally {
            interpreter.close()
        }
    }

    private fun AdditionalInfoImagePreprocessor.PreprocessedInput.toDebug(
        imageUri: Uri,
        config: AdditionalInfoModelConfig,
    ): AdditionalInfoDetectionDebug = AdditionalInfoDetectionDebug(
        sourceUri = imageUri.toString(),
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        modelVersion = config.modelVersion,
        modelFile = config.modelFile,
        inputShape = config.input.shape,
        normalizationType = config.input.normalizationType,
        preprocessing = preprocessing,
        tensorStats = stats,
    )

    private fun loadModel(context: Context, config: AdditionalInfoModelConfig): MappedByteBuffer = context.assets
        .openFd(AdditionalInfoModelAssets.modelAssetPath(config.modelFile))
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
