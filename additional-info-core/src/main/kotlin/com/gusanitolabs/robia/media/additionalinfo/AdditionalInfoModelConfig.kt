package com.gusanitolabs.robia.media.additionalinfo

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

object AdditionalInfoModelManifest {
    fun parse(json: String): AdditionalInfoModelConfig {
        val root = JsonParser(json).parseObject()
        val input = root.objectValue("input")
        val heads = root.arrayValue("outputs").objects().map { output ->
            AdditionalInfoHeadSpec(
                name = output.stringValue("name"),
                shape = output.arrayValue("shape").ints(),
                labels = output.arrayValue("labels").strings(),
                tagIds = output.arrayValue("tagIds").nullableStrings(),
                threshold = output.floatValue("threshold"),
                margin = output.optionalFloat("margin"),
                multiSelectThreshold = output.optionalFloat("multiSelectThreshold"),
                nearTieMargin = output.optionalFloat("nearTieMargin"),
                multiSeasonLabel = output.optionalString("multiSeasonLabel")?.takeIf(String::isNotBlank),
                multiSeasonTagIds = output.optionalArray("multiSeasonTagIds")?.strings().orEmpty().toSet(),
            )
        }
        return AdditionalInfoModelConfig(
            modelVersion = root.stringValue("modelVersion"),
            modelFile = root.stringValue("modelFile"),
            input = AdditionalInfoInputSpec(
                name = input.stringValue("name"),
                shape = input.arrayValue("shape").ints(),
                normalizationType = input.objectValue("normalization").stringValue("type"),
            ),
            heads = heads,
            noiseBaseline = root.objectValue("deterministicNoiseBaseline").let { baseline ->
                listOf("category", "occasion", "season").associateWith { headName ->
                    val head = baseline.objectValue(headName)
                    AdditionalInfoBaseline(
                        argmax = head.intValue("argmax"),
                        maxScore = head.floatValue("maxScore"),
                        shape = head.arrayValue("shape").ints(),
                    )
                }
            },
        )
    }

    fun validate(config: AdditionalInfoModelConfig): Boolean {
        if (!AdditionalInfoModelAssets.isSafeModelFile(config.modelFile)) return false
        if (config.input.shape != AdditionalInfoPreprocessingPolicy.expectedShape) return false
        if (config.input.normalizationType != AdditionalInfoPreprocessingPolicy.normalizationType) return false
        return config.heads.all { head ->
            head.shape.size == 2 &&
                head.shape.first() == 1 &&
                head.width == head.labels.size &&
                head.width == head.tagIds.size
        }
    }
}

private sealed interface JsonValue
private data class JsonObjectValue(val entries: Map<String, JsonValue>) : JsonValue
private data class JsonArrayValue(val values: List<JsonValue>) : JsonValue
private data class JsonStringValue(val value: String) : JsonValue
private data class JsonNumberValue(val value: Double) : JsonValue
private data object JsonNullValue : JsonValue
private data class JsonBooleanValue(val value: Boolean) : JsonValue

private class JsonParser(private val source: String) {
    private var index = 0

    fun parseObject(): JsonObjectValue = parseValue().also {
        skipWhitespace()
        require(index == source.length) { "Unexpected trailing JSON at offset $index" }
    } as? JsonObjectValue ?: error("Expected JSON object")

    private fun parseValue(): JsonValue {
        skipWhitespace()
        return when (peek()) {
            '{' -> parseObjectValue()
            '[' -> parseArrayValue()
            '"' -> JsonStringValue(parseString())
            't' -> parseLiteral("true", JsonBooleanValue(true))
            'f' -> parseLiteral("false", JsonBooleanValue(false))
            'n' -> parseLiteral("null", JsonNullValue)
            else -> parseNumber()
        }
    }

    private fun parseObjectValue(): JsonObjectValue {
        expect('{')
        skipWhitespace()
        if (tryConsume('}')) return JsonObjectValue(emptyMap())
        val entries = linkedMapOf<String, JsonValue>()
        while (true) {
            val key = parseString()
            skipWhitespace()
            expect(':')
            entries[key] = parseValue()
            skipWhitespace()
            if (tryConsume('}')) break
            expect(',')
            skipWhitespace()
        }
        return JsonObjectValue(entries)
    }

    private fun parseArrayValue(): JsonArrayValue {
        expect('[')
        skipWhitespace()
        if (tryConsume(']')) return JsonArrayValue(emptyList())
        val values = mutableListOf<JsonValue>()
        while (true) {
            values += parseValue()
            skipWhitespace()
            if (tryConsume(']')) break
            expect(',')
        }
        return JsonArrayValue(values)
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < source.length) {
            when (val char = source[index++]) {
                '"' -> return result.toString()
                '\\' -> result.append(parseEscape())
                else -> result.append(char)
            }
        }
        error("Unterminated JSON string")
    }

    private fun parseEscape(): Char = when (val escaped = source[index++]) {
        '"', '\\', '/' -> escaped
        'b' -> '\b'
        'f' -> '\u000C'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> source.substring(index, index + 4).toInt(16).toChar().also { index += 4 }
        else -> error("Unsupported JSON escape: \\$escaped")
    }

    private fun parseNumber(): JsonNumberValue {
        val start = index
        if (peek() == '-') index++
        while (peekOrNull()?.isDigit() == true) index++
        if (peekOrNull() == '.') {
            index++
            while (peekOrNull()?.isDigit() == true) index++
        }
        if (peekOrNull() == 'e' || peekOrNull() == 'E') {
            index++
            if (peekOrNull() == '+' || peekOrNull() == '-') index++
            while (peekOrNull()?.isDigit() == true) index++
        }
        require(index > start) { "Expected JSON number at offset $start" }
        return JsonNumberValue(source.substring(start, index).toDouble())
    }

    private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
        require(source.startsWith(literal, index)) { "Expected $literal at offset $index" }
        index += literal.length
        return value
    }

    private fun expect(char: Char) {
        require(peek() == char) { "Expected '$char' at offset $index" }
        index++
    }

    private fun tryConsume(char: Char): Boolean {
        if (peekOrNull() != char) return false
        index++
        return true
    }

    private fun skipWhitespace() {
        while (peekOrNull()?.isWhitespace() == true) index++
    }

    private fun peek(): Char = peekOrNull() ?: error("Unexpected end of JSON")
    private fun peekOrNull(): Char? = source.getOrNull(index)
}

private fun JsonObjectValue.value(name: String): JsonValue = entries[name] ?: error("Missing JSON field: $name")
private fun JsonObjectValue.objectValue(name: String): JsonObjectValue = value(name) as? JsonObjectValue ?: error("Expected object: $name")
private fun JsonObjectValue.arrayValue(name: String): JsonArrayValue = value(name) as? JsonArrayValue ?: error("Expected array: $name")
private fun JsonObjectValue.optionalArray(name: String): JsonArrayValue? = entries[name] as? JsonArrayValue
private fun JsonObjectValue.stringValue(name: String): String = (value(name) as? JsonStringValue)?.value ?: error("Expected string: $name")
private fun JsonObjectValue.optionalString(name: String): String? = (entries[name] as? JsonStringValue)?.value
private fun JsonObjectValue.intValue(name: String): Int = (value(name) as? JsonNumberValue)?.value?.toInt() ?: error("Expected integer: $name")
private fun JsonObjectValue.floatValue(name: String): Float = (value(name) as? JsonNumberValue)?.value?.toFloat() ?: error("Expected float: $name")
private fun JsonObjectValue.optionalFloat(name: String): Float? = (entries[name] as? JsonNumberValue)?.value?.toFloat()
private fun JsonArrayValue.objects(): List<JsonObjectValue> = values.map { it as? JsonObjectValue ?: error("Expected object array item") }
private fun JsonArrayValue.ints(): List<Int> = values.map { (it as? JsonNumberValue)?.value?.toInt() ?: error("Expected integer array item") }
private fun JsonArrayValue.strings(): List<String> = values.map { (it as? JsonStringValue)?.value ?: error("Expected string array item") }
private fun JsonArrayValue.nullableStrings(): List<String?> = values.map {
    when (it) {
        JsonNullValue -> null
        is JsonStringValue -> it.value
        else -> error("Expected nullable string array item")
    }
}
