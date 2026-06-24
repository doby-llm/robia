package com.gusanitolabs.robia.media.additionalinfo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AdditionalInfoModelAssets {
    const val DIRECTORY = "additional_info"
    const val MODEL_FILE = "$DIRECTORY/mobilenet_v3_large.tflite"
    const val MANIFEST_FILE = "$DIRECTORY/mobilenet_v3_large.json"

    private val SAFE_MODEL_FILE = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.tflite")

    fun modelAssetPath(modelFile: String): String {
        require(isSafeModelFile(modelFile)) { "Unsafe additional-info model file: $modelFile" }
        return "$DIRECTORY/$modelFile"
    }

    fun isSafeModelFile(modelFile: String): Boolean =
        SAFE_MODEL_FILE.matches(modelFile) &&
            '/' !in modelFile &&
            '\\' !in modelFile &&
            ".." !in modelFile
}

data class AdditionalInfoModelConfig(
    val modelVersion: String,
    val modelFile: String,
    val input: AdditionalInfoInputSpec,
    val heads: List<AdditionalInfoHeadSpec>,
    val noiseBaseline: Map<String, AdditionalInfoBaseline>,
) {
    val headsByName: Map<String, AdditionalInfoHeadSpec> = heads.associateBy { head -> head.name }

    fun requireHead(name: String): AdditionalInfoHeadSpec = headsByName[name]
        ?: error("Missing additional-info output head: $name")

    fun modelOutputTagIds(): Set<String> = heads
        .flatMap { head -> head.tagIds.filterNotNull() + head.multiSeasonTagIds }
        .toSet()
}

object AdditionalInfoModelOutputTags {
    val ids: Set<String> = setOf(
        "category-shorts",
        "category-jackets",
        "category-jumpsuits",
        "category-blouses",
        "category-dresses",
        "category-skirts",
        "category-blazers",
        "category-cardigans",
        "category-bags",
        "category-tops",
        "category-knitwear",
        "category-trousers",
        "category-sweaters",
        "category-shoes",
        "category-shirts",
        "category-vests",
        "category-jewelry",
        "category-accessories",
        "category-coats",
        "occasion-active",
        "occasion-statement",
        "occasion-dressed-up",
        "occasion-formal",
        "occasion-everyday",
        "occasion-business",
        "season-spring",
        "season-summer",
        "season-fall",
        "season-winter",
    )
}

data class AdditionalInfoInputSpec(
    val name: String,
    val shape: List<Int>,
    val normalizationType: String,
)

data class AdditionalInfoHeadSpec(
    val name: String,
    val shape: List<Int>,
    val labels: List<String>,
    val tagIds: List<String?>,
    val threshold: Float,
    val margin: Float?,
    val multiSelectThreshold: Float?,
    val nearTieMargin: Float?,
    val multiSeasonLabel: String?,
    val multiSeasonTagIds: Set<String>,
) {
    val width: Int = shape.lastOrNull() ?: 0
}

data class AdditionalInfoBaseline(
    val argmax: Int,
    val maxScore: Float,
    val shape: List<Int>,
)

object AdditionalInfoModelConfigLoader {
    fun load(context: Context): AdditionalInfoModelConfig = context.assets
        .open(AdditionalInfoModelAssets.MANIFEST_FILE)
        .bufferedReader()
        .use { reader -> parse(reader.readText()) }

    fun parse(json: String): AdditionalInfoModelConfig {
        val root = JSONObject(json)
        val input = root.getJSONObject("input")
        val heads = root.getJSONArray("outputs").mapObjects { output ->
            AdditionalInfoHeadSpec(
                name = output.getString("name"),
                shape = output.getJSONArray("shape").toIntList(),
                labels = output.getJSONArray("labels").toStringList(),
                tagIds = output.getJSONArray("tagIds").toNullableStringList(),
                threshold = output.getDouble("threshold").toFloat(),
                margin = output.optNullableFloat("margin"),
                multiSelectThreshold = output.optNullableFloat("multiSelectThreshold"),
                nearTieMargin = output.optNullableFloat("nearTieMargin"),
                multiSeasonLabel = output.optString("multiSeasonLabel").takeIf(String::isNotBlank),
                multiSeasonTagIds = output.optJSONArray("multiSeasonTagIds")?.toStringList().orEmpty().toSet(),
            )
        }
        return AdditionalInfoModelConfig(
            modelVersion = root.getString("modelVersion"),
            modelFile = root.getString("modelFile"),
            input = AdditionalInfoInputSpec(
                name = input.getString("name"),
                shape = input.getJSONArray("shape").toIntList(),
                normalizationType = input.getJSONObject("normalization").getString("type"),
            ),
            heads = heads,
            noiseBaseline = root.getJSONObject("deterministicNoiseBaseline").let { baseline ->
                listOf("category", "occasion", "season").associateWith { headName ->
                    val head = baseline.getJSONObject(headName)
                    AdditionalInfoBaseline(
                        argmax = head.getInt("argmax"),
                        maxScore = head.getDouble("maxScore").toFloat(),
                        shape = head.getJSONArray("shape").toIntList(),
                    )
                }
            },
        )
    }

    fun validate(config: AdditionalInfoModelConfig): Boolean {
        if (!AdditionalInfoModelAssets.isSafeModelFile(config.modelFile)) return false
        if (config.input.shape != listOf(1, 224, 224, 3)) return false
        return config.heads.all { head ->
            head.shape.size == 2 &&
                head.shape.first() == 1 &&
                head.width == head.labels.size &&
                head.width == head.tagIds.size
        }
    }
}

private fun JSONObject.optNullableFloat(name: String): Float? = if (has(name)) getDouble(name).toFloat() else null

private fun JSONArray.toIntList(): List<Int> = List(length()) { index -> getInt(index) }

private fun JSONArray.toStringList(): List<String> = List(length()) { index -> getString(index) }

private fun JSONArray.toNullableStringList(): List<String?> = List(length()) { index ->
    if (isNull(index)) null else getString(index)
}

private fun JSONArray.mapObjects(transform: (JSONObject) -> AdditionalInfoHeadSpec): List<AdditionalInfoHeadSpec> =
    List(length()) { index -> transform(getJSONObject(index)) }
