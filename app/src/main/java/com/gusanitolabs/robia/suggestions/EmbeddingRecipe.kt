package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.TagDisplayPolicy

/** Embedding model and recipe identity. Vectors from any other pair are ignored, never mixed. */
object EmbeddingRecipe {
    const val MODEL_ID = "embeddinggemma-2-text-vision-440m@e301f74d"
    const val RECIPE_ID = "eg2-r1-vt70"
    const val DIMENSION = 768
    const val MODEL_FILE_NAME = "embeddinggemma-2-text-vision-440m.litertlm"
    const val MODEL_URL = "https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/e301f74d5551b0c2641bd5cb4652a76239d5c5f8/embeddinggemma-2-text-vision-440m.litertlm"
    const val MODEL_SHA256 = "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0"
    const val MODEL_APPROX_MB = 388
    const val VISION_TOKENS = 70
    const val IMAGE_MAX_EDGE_PX = 1024
    const val ENGINE_IDLE_RELEASE_MS = 60_000L
    const val CLASSIFICATION_PREFIX = "task: classification | text: "
    const val QUERY_PREFIX = "task: search query | text: "
    const val DOCUMENT_PREFIX = "title: none | text: "
    const val CATEGORY_MARGIN_TEXT = 0.010f
    const val CATEGORY_MARGIN_COMBINED = 0.50f
    const val OCCASION_MARGIN_TEXT = 0.010f
    const val OCCASION_MARGIN_COMBINED = 0.50f
    const val KNN_TOP = 3
    const val SEARCH_IMAGE_WEIGHT = 0.7f
    const val SEARCH_TEXT_WEIGHT = 0.3f
    val SUGGEST_OCCASIONS_DEFAULT_OFF = setOf("category-accessories", "category-jewelry", "category-socks", "category-underwear")

    /** Label descriptions for the default categories and occasions; copied from tools/embedding-precision/labels.py. */
    val DEFAULT_LABEL_DESCRIPTIONS: Map<String, String> = mapOf(
        "category-shorts" to "shorts: short pants that end above the knee",
        "category-jackets" to "a jacket: short outerwear with long sleeves and a front zip or buttons",
        "category-jumpsuits" to "a jumpsuit: one-piece garment combining a top and trousers",
        "category-blouses" to "a blouse: light, loose top in a soft or sheer fabric, often with buttons or ruffles",
        "category-dresses" to "a dress: one-piece garment with a bodice and a skirt",
        "category-skirts" to "a skirt: garment that hangs from the waist, without separate legs",
        "category-blazers" to "a blazer: tailored jacket with lapels, worn over a shirt",
        "category-cardigans" to "a cardigan: knitted top that opens at the front with buttons",
        "category-bags" to "a bag: handbag, backpack or tote for carrying things",
        "category-tops" to "a top: casual upper-body garment such as a t-shirt, tank top or camisole",
        "category-knitwear" to "knitwear: garment made of knitted yarn, such as a knit vest or knit top",
        "category-trousers" to "trousers: long pants covering each leg down to the ankle, such as jeans or chinos",
        "category-sweaters" to "a sweater: warm knitted pullover with long sleeves and no front opening",
        "category-shoes" to "shoes: footwear such as sneakers, boots, sandals or heels",
        "category-shirts" to "a shirt: collared top with buttons down the front",
        "category-vests" to "a vest: sleeveless upper garment worn over another top",
        "category-jewelry" to "jewelry: necklace, earrings, bracelet or ring",
        "category-accessories" to "an accessory: a belt or waist belt, a scarf, a hat, sunglasses or a similar item",
        "category-coats" to "a coat: long, warm outerwear reaching the thighs or knees",
        "category-socks" to "socks or tights: hosiery worn on the feet and legs",
        "category-underwear" to "underwear: bra, briefs, boxers or lingerie worn under clothes",
        "occasion-active" to "sportswear for exercise, the gym or running",
        "occasion-statement" to "a bold, eye-catching piece with a striking color, pattern or design",
        "occasion-dressed-up" to "an elegant outfit for going out, dinners or parties",
        "occasion-formal" to "formal wear for weddings, galas or ceremonies",
        "occasion-everyday" to "casual everyday clothing for errands and relaxing",
        "occasion-business" to "office or business attire for work",
    )
}

/**
 * Label text for a category or occasion tag (TS§3.1). A custom tag, or a default whose name was changed,
 * is labelled by its own name unless it has a description.
 */
fun labelTextFor(tag: GarmentTag): String {
    tag.description?.takeIf { it.isNotBlank() }?.let { return EmbeddingRecipe.CLASSIFICATION_PREFIX + it }
    val defaultDescription = EmbeddingRecipe.DEFAULT_LABEL_DESCRIPTIONS[tag.id]
    if (defaultDescription != null && !TagDisplayPolicy.shouldUseStoredName(tag)) {
        return EmbeddingRecipe.CLASSIFICATION_PREFIX + defaultDescription
    }
    return EmbeddingRecipe.CLASSIFICATION_PREFIX + tag.name
}

/** TEXT input for a garment vector, or null when name and notes are both blank (the TEXT vector is then deleted). */
fun garmentTextInput(name: String, notes: String): String? {
    val parts = listOf(name.trim(), notes.trim()).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    return EmbeddingRecipe.DOCUMENT_PREFIX + parts.joinToString(". ")
}
