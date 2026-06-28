package com.gusanitolabs.robia.additionalinfo.cli

import com.gusanitolabs.robia.core.model.AdditionalInfoLabelScore
import com.gusanitolabs.robia.media.additionalinfo.AdditionalInfoModelConfig
import com.gusanitolabs.robia.media.additionalinfo.AdditionalInfoModelManifest
import com.gusanitolabs.robia.media.additionalinfo.AdditionalInfoPreprocessingPolicy
import com.gusanitolabs.robia.media.additionalinfo.AdditionalInfoTagMapper
import com.gusanitolabs.robia.media.additionalinfo.AdditionalInfoTensorBuilder
import org.tensorflow.lite.Interpreter
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.math.max

fun main(rawArgs: Array<String>) {
    val args = CliArgs.parse(rawArgs)
    val config = AdditionalInfoModelManifest.parse(args.manifest.readText())
    require(AdditionalInfoModelManifest.validate(config)) { "Invalid additional-info manifest: ${args.manifest}" }

    val preprocessed = preprocessImage(args.image)
    val result = linkedMapOf<String, Any?>(
        "image" to args.image.absolutePath,
        "manifest" to args.manifest.absolutePath,
        "modelVersion" to config.modelVersion,
        "preprocessing" to preprocessed.preprocessing,
        "sourceWidth" to preprocessed.sourceWidth,
        "sourceHeight" to preprocessed.sourceHeight,
        "inputShape" to config.input.shape,
        "normalizationType" to config.input.normalizationType,
        "tensorStats" to statsMap(preprocessed.tensor.stats),
    )

    if (args.noModel) {
        result["modelSkipped"] = true
    } else {
        val model = args.model ?: File(args.manifest.parentFile, config.modelFile)
        val outputs = runTflite(model, config, preprocessed.tensor.tensor)
        val prediction = AdditionalInfoTagMapper.map(outputs, config.modelOutputTagIds(), config)
            ?: error("Could not map TFLite outputs to manifest heads")
        result["model"] = model.absolutePath
        result["outputs"] = outputs.mapValues { (headName, scores) -> summarizeScores(config.requireHead(headName), scores, args.topK) }
        result["selectedTagIds"] = prediction.selectedTagIds.sorted()
    }

    println(toJson(result))
}

private data class PreprocessedImage(
    val tensor: com.gusanitolabs.robia.media.additionalinfo.AdditionalInfoTensorWithStats,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val preprocessing: String,
)

private fun preprocessImage(file: File): PreprocessedImage {
    val decoded = ImageIO.read(file) ?: error("Unsupported image file: $file")
    val background = chooseCompositeBackground(decoded)
    val squareSize = max(decoded.width, decoded.height)
    val square = BufferedImage(squareSize, squareSize, BufferedImage.TYPE_INT_RGB)
    val compositeGraphics = square.createGraphics()
    try {
        compositeGraphics.color = background.color
        compositeGraphics.fillRect(0, 0, square.width, square.height)
        compositeGraphics.drawImage(decoded, (squareSize - decoded.width) / 2, (squareSize - decoded.height) / 2, null)
    } finally {
        compositeGraphics.dispose()
    }

    val size = AdditionalInfoPreprocessingPolicy.inputSize
    val resized = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    val graphics = resized.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(square, 0, 0, size, size, null)
    } finally {
        graphics.dispose()
    }

    val pixels = IntArray(size * size)
    resized.getRGB(0, 0, size, size, pixels, 0, size)
    return PreprocessedImage(
        tensor = AdditionalInfoTensorBuilder.fromRgbPixels(pixels),
        sourceWidth = decoded.width,
        sourceHeight = decoded.height,
        preprocessing = "${AdditionalInfoPreprocessingPolicy.description}__background_${background.descriptionName}",
    )
}

private data class CompositeBackground(val color: Color, val descriptionName: String)

private fun chooseCompositeBackground(source: BufferedImage): CompositeBackground {
    val sampleStep = max(1, max(source.width, source.height) / 128)
    var weightedLuminance = 0.0
    var weightSum = 0.0
    for (y in 0 until source.height step sampleStep) {
        for (x in 0 until source.width step sampleStep) {
            val pixel = source.getRGB(x, y)
            val alpha = (pixel ushr 24) and 0xff
            if (alpha < 64) continue
            val weight = alpha / 255.0
            val red = (pixel ushr 16) and 0xff
            val green = (pixel ushr 8) and 0xff
            val blue = pixel and 0xff
            weightedLuminance += ((0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255.0) * weight
            weightSum += weight
        }
    }
    if (weightSum < 8.0) return CompositeBackground(Color.WHITE, "white")

    val foregroundLuminance = (weightedLuminance / weightSum).coerceIn(0.0, 1.0)
    val contrastAgainstWhite = contrastRatio(foregroundLuminance, 1.0)
    val contrastAgainstBlack = contrastRatio(foregroundLuminance, 0.0)
    return if (foregroundLuminance >= 0.58 && contrastAgainstBlack - contrastAgainstWhite >= 1.0) {
        CompositeBackground(Color.BLACK, "black")
    } else {
        CompositeBackground(Color.WHITE, "white")
    }
}

private fun contrastRatio(first: Double, second: Double): Double {
    val lighter = max(first, second)
    val darker = minOf(first, second)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun runTflite(model: File, config: AdditionalInfoModelConfig, input: ByteBuffer): Map<String, FloatArray> {
    val interpreter = Interpreter(model)
    try {
        val outputMap = mutableMapOf<Int, Any>()
        val namesByIndex = mutableMapOf<Int, String>()
        for (index in 0 until interpreter.outputTensorCount) {
            val shape = interpreter.getOutputTensor(index).shape().toList()
            val head = config.heads.singleOrNull { it.shape == shape }
                ?: error("Unexpected output tensor at index $index with shape $shape")
            outputMap[index] = Array(shape.first()) { FloatArray(head.width) }
            namesByIndex[index] = head.name
        }
        check(namesByIndex.values.toSet() == config.heads.map { it.name }.toSet()) {
            "Missing additional-info output tensors"
        }
        input.rewind()
        interpreter.runForMultipleInputsOutputs(arrayOf(input), outputMap)
        return namesByIndex.entries.associate { (index, name) ->
            @Suppress("UNCHECKED_CAST")
            name to (outputMap.getValue(index) as Array<FloatArray>)[0]
        }
    } finally {
        interpreter.close()
    }
}

private fun summarizeScores(
    head: com.gusanitolabs.robia.media.additionalinfo.AdditionalInfoHeadSpec,
    scores: FloatArray,
    topK: Int,
): Map<String, Any?> {
    val top = scores.indices.sortedByDescending { scores[it] }.take(topK)
    return mapOf(
        "shape" to listOf(1, scores.size),
        "topK" to top.map { index ->
            mapOf(
                "index" to index,
                "label" to head.labels[index],
                "tagId" to head.tagIds[index],
                "score" to scores[index],
            )
        },
    )
}

private fun statsMap(stats: com.gusanitolabs.robia.core.model.AdditionalInfoTensorStats): Map<String, Any?> = mapOf(
    "min" to stats.min,
    "max" to stats.max,
    "mean" to stats.mean,
    "standardDeviation" to stats.standardDeviation,
    "channelMeans" to stats.channelMeans,
    "channelMins" to stats.channelMins,
    "channelMaxs" to stats.channelMaxs,
    "nonFiniteCount" to stats.nonFiniteCount,
    "checksum" to stats.checksum,
)

private data class CliArgs(
    val image: File,
    val manifest: File,
    val model: File?,
    val noModel: Boolean,
    val topK: Int,
) {
    companion object {
        fun parse(args: Array<String>): CliArgs {
            var image: File? = null
            var manifest = File("app/src/main/assets/additional_info/mobilenet_v3_large.json")
            var model: File? = null
            var noModel = false
            var topK = 5
            var index = 0
            while (index < args.size) {
                when (val arg = args[index]) {
                    "--manifest" -> manifest = File(args.getValue(++index))
                    "--model" -> model = File(args.getValue(++index))
                    "--no-model" -> noModel = true
                    "--top-k" -> topK = args.getValue(++index).toInt()
                    "--help", "-h" -> printUsageAndExit()
                    else -> if (image == null) image = File(arg) else error("Unexpected argument: $arg")
                }
                index++
            }
            return CliArgs(
                image = image ?: error("Missing image path. Pass --help for usage."),
                manifest = manifest,
                model = model,
                noModel = noModel,
                topK = topK,
            )
        }

        private fun Array<String>.getValue(index: Int): String = getOrNull(index) ?: error("Missing value for ${get(index - 1)}")

        private fun printUsageAndExit(): Nothing {
            println(
                """
                Usage: ./gradlew :additional-info-cli:run --args='<image> [--manifest path] [--model path] [--no-model] [--top-k n]'

                Reuses the Android app's shared Kotlin classifier core for manifest parsing,
                preprocessing tensor/stats policy, output-head mapping, and tag selection.
                The CLI-specific boundary is image decode/resize via JVM ImageIO/Java2D; Android
                still uses ContentResolver/Bitmap for that wrapper layer.
                """.trimIndent(),
            )
            kotlin.system.exitProcess(0)
        }
    }
}

private fun toJson(value: Any?): String = when (value) {
    null -> "null"
    is String -> value.replace("\\", "\\\\").replace("\"", "\\\"").let { "\"$it\"" }
    is Number, is Boolean -> value.toString()
    is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) -> "\n  ${toJson(key.toString())}: ${toJson(item).prependIndent("  ").trimStart()}" }
    is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { toJson(it) }
    is FloatArray -> value.asIterable().joinToString(prefix = "[", postfix = "]") { it.toString() }
    else -> toJson(value.toString())
}
