package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.data.GarmentVectors
import org.json.JSONObject

/** Ratings log in `filesDir` (REQ-OF-01). It holds ids and ratings only: no photos, no prompt text. */
const val OUTFIT_LAB_RATINGS_FILE_NAME = "outfit_lab.jsonl"

/** Slots of an outfit. [DRESS] holds dresses and jumpsuits, which replace [TOP] and [BOTTOM]. */
enum class OutfitSlot { TOP, BOTTOM, DRESS, OUTER, SHOES, ACCESSORY }

/** One proposal: the garment id per slot, the non-category tags every piece shares, and whether two pieces share a primary colour. */
data class Outfit(
    val garmentIds: Map<OutfitSlot, String>,
    val sharedTagIds: List<String>,
    val similarColours: Boolean,
)

/**
 * Outfit lab (REQ-OF-01, developer mode only). Proposes outfits from the stored IMAGE and TEXT vectors and tags; no model
 * runs here. Each outfit is top + bottom or dress/jumpsuit, plus outerwear (Fall or Winter only), shoes and an accessory
 * when the pool has them. Pieces are added greedily by mean IMAGE cosine to the pieces already chosen, plus the cosine of
 * the prompt with the garment's TEXT vector. The first free pick of each outfit differs from the first free picks of the
 * outfits before it, so the outfits differ.
 */
object OutfitComposer {
    const val OUTFIT_COUNT = 3

    private const val CATEGORY_GROUP = "category"
    private val OUTER_SEASON_TAG_IDS = setOf("season-fall", "season-winter")
    private val SLOT_BY_CATEGORY = mapOf(
        "category-tops" to OutfitSlot.TOP,
        "category-shirts" to OutfitSlot.TOP,
        "category-blouses" to OutfitSlot.TOP,
        "category-knitwear" to OutfitSlot.TOP,
        "category-sweaters" to OutfitSlot.TOP,
        "category-cardigans" to OutfitSlot.TOP,
        "category-vests" to OutfitSlot.TOP,
        "category-trousers" to OutfitSlot.BOTTOM,
        "category-skirts" to OutfitSlot.BOTTOM,
        "category-shorts" to OutfitSlot.BOTTOM,
        "category-dresses" to OutfitSlot.DRESS,
        "category-jumpsuits" to OutfitSlot.DRESS,
        "category-jackets" to OutfitSlot.OUTER,
        "category-blazers" to OutfitSlot.OUTER,
        "category-coats" to OutfitSlot.OUTER,
        "category-shoes" to OutfitSlot.SHOES,
        "category-accessories" to OutfitSlot.ACCESSORY,
        "category-bags" to OutfitSlot.ACCESSORY,
        "category-jewelry" to OutfitSlot.ACCESSORY,
    )

    /** Slot of a garment from its category tag, or null for categories that are not outfit pieces (socks, underwear). */
    fun slotOf(item: ClothingItem): OutfitSlot? =
        item.tags.firstOrNull { tag -> tag.categoryId == CATEGORY_GROUP }?.let { tag -> SLOT_BY_CATEGORY[tag.id] }

    /**
     * Up to [n] outfits. [seed] is a garment id that every outfit contains; [prompt] is the embedded prompt (search prefix).
     * [season] and [occasion] are tag ids; when given, every piece except the seed carries that tag. Garments without an
     * IMAGE vector, archived garments and garments outside [OutfitSlot] are never proposed. Same input, same output.
     */
    fun compose(
        seed: String?,
        prompt: FloatArray?,
        season: String?,
        occasion: String?,
        items: List<ClothingItem>,
        vectors: Map<String, GarmentVectors>,
        n: Int = OUTFIT_COUNT,
    ): List<Outfit> {
        val candidates = items.filterNot(ClothingItem::isArchived).mapNotNull { item -> candidateOf(item, vectors) }
        val seedCandidate = if (seed == null) null else candidates.firstOrNull { candidate -> candidate.id == seed }
        if (seed != null && seedCandidate == null) return emptyList()
        val pool = candidates
            .filter { candidate -> candidate.id != seed && candidate.hasTag(season) && candidate.hasTag(occasion) }
            .groupBy(Candidate::slot)
        val outerAllowed = (season != null && season in OUTER_SEASON_TAG_IDS) ||
            seedCandidate?.tagIds?.any { tag -> tag in OUTER_SEASON_TAG_IDS } == true

        val outfits = mutableListOf<Outfit>()
        val openers = mutableSetOf<String>()
        while (outfits.size < n) {
            val (chosen, opener) = build(seedCandidate, prompt, pool, outerAllowed, openers) ?: break
            val outfit = outfitOf(chosen)
            if (outfits.none { existing -> existing.garmentIds == outfit.garmentIds }) outfits += outfit
            if (opener == null) break
            openers += opener
        }
        return outfits
    }

    /**
     * The pieces of one outfit and the id of its first free pick (null when the seed alone decided the base and no
     * optional piece was added). Null when no complete base (top + bottom, or dress) exists.
     */
    private fun build(
        seed: Candidate?,
        prompt: FloatArray?,
        pool: Map<OutfitSlot, List<Candidate>>,
        outerAllowed: Boolean,
        openers: Set<String>,
    ): Pair<Map<OutfitSlot, Candidate>, String?>? {
        val chosen = linkedMapOf<OutfitSlot, Candidate>()
        seed?.let { chosen[it.slot] = it }
        var opener: String? = null
        val hasBase = OutfitSlot.DRESS in chosen || OutfitSlot.TOP in chosen || OutfitSlot.BOTTOM in chosen
        if (!hasBase) {
            // The first pick chooses the route: a dress, or a top or bottom whose partner exists in the pool.
            val eligible = buildList {
                addAll(pool[OutfitSlot.DRESS].orEmpty())
                if (pool[OutfitSlot.BOTTOM].orEmpty().isNotEmpty()) addAll(pool[OutfitSlot.TOP].orEmpty())
                if (pool[OutfitSlot.TOP].orEmpty().isNotEmpty()) addAll(pool[OutfitSlot.BOTTOM].orEmpty())
            }.filter { candidate -> candidate.id !in openers }
            val first = rank(eligible, emptyList(), prompt).firstOrNull() ?: return null
            chosen[first.slot] = first
            opener = first.id
        }
        if (OutfitSlot.DRESS !in chosen) {
            for (slot in listOf(OutfitSlot.TOP, OutfitSlot.BOTTOM)) {
                if (slot !in chosen) chosen[slot] = best(slot, chosen.values, prompt, pool) ?: return null
            }
        }
        val optional = buildList {
            if (outerAllowed) add(OutfitSlot.OUTER)
            add(OutfitSlot.SHOES)
            add(OutfitSlot.ACCESSORY)
        }
        for (slot in optional) {
            if (slot !in chosen) best(slot, chosen.values, prompt, pool)?.let { candidate -> chosen[slot] = candidate }
        }
        return chosen to opener
    }

    private fun best(
        slot: OutfitSlot,
        chosen: Collection<Candidate>,
        prompt: FloatArray?,
        pool: Map<OutfitSlot, List<Candidate>>,
    ): Candidate? = rank(pool[slot].orEmpty(), chosen, prompt).firstOrNull()

    /** Highest score first; equal scores by name, then id, so the same input always gives the same order. */
    private fun rank(candidates: List<Candidate>, chosen: Collection<Candidate>, prompt: FloatArray?): List<Candidate> =
        candidates
            .map { candidate -> candidate to score(candidate, chosen, prompt) }
            .sortedWith(
                compareByDescending<Pair<Candidate, Float>> { (_, score) -> score }
                    .thenBy { (candidate, _) -> candidate.name }
                    .thenBy { (candidate, _) -> candidate.id },
            )
            .map { (candidate, _) -> candidate }

    /** Mean IMAGE cosine to the chosen pieces, plus the prompt's cosine with the TEXT vector when both exist. */
    private fun score(candidate: Candidate, chosen: Collection<Candidate>, prompt: FloatArray?): Float {
        val imageMean = if (chosen.isEmpty()) {
            0f
        } else {
            chosen.sumOf { piece -> cosine(candidate.image, piece.image).toDouble() }.toFloat() / chosen.size
        }
        val textTerm = if (prompt != null && candidate.text != null) cosine(prompt, candidate.text) else 0f
        return imageMean + textTerm
    }

    private fun outfitOf(chosen: Map<OutfitSlot, Candidate>): Outfit {
        val pieces = chosen.values.toList()
        val shared = pieces.map(Candidate::tagIds).reduce { acc, tags -> acc intersect tags }.sorted()
        val colourCounts = pieces.mapNotNull(Candidate::colourId).groupingBy { colour -> colour }.eachCount()
        return Outfit(
            garmentIds = OutfitSlot.entries.mapNotNull { slot -> chosen[slot]?.let { piece -> slot to piece.id } }.toMap(),
            sharedTagIds = shared,
            similarColours = (colourCounts.values.maxOrNull() ?: 0) >= 2,
        )
    }

    private fun candidateOf(item: ClothingItem, vectors: Map<String, GarmentVectors>): Candidate? {
        val slot = slotOf(item) ?: return null
        val garment = vectors[item.id] ?: return null
        val image = garment.image ?: return null
        return Candidate(
            id = item.id,
            name = item.name,
            slot = slot,
            image = image,
            text = garment.text,
            tagIds = item.tags.filterNot { tag -> tag.categoryId == CATEGORY_GROUP }.mapTo(HashSet(), GarmentTag::id),
            colourId = item.colorMetrics.primaryPaletteColorId,
        )
    }

    private fun Candidate.hasTag(tagId: String?): Boolean = tagId == null || tagId in tagIds
}

private class Candidate(
    val id: String,
    val name: String,
    val slot: OutfitSlot,
    val image: FloatArray,
    val text: FloatArray?,
    val tagIds: Set<String>,
    val colourId: String?,
)

/**
 * One line of [OUTFIT_LAB_RATINGS_FILE_NAME]: the outfit's garment ids by slot, the seed and tag filters as ids, and the
 * rating (`up` or `down`). The prompt text and photos are never written.
 */
fun outfitRatingLine(
    atEpochMillis: Long,
    outfit: Outfit,
    seed: String?,
    season: String?,
    occasion: String?,
    thumbsUp: Boolean,
): String {
    val items = JSONObject()
    outfit.garmentIds.forEach { (slot, id) -> items.put(slot.name.lowercase(), id) }
    return JSONObject()
        .put("at", atEpochMillis)
        .put("seed", seed ?: JSONObject.NULL)
        .put("season", season ?: JSONObject.NULL)
        .put("occasion", occasion ?: JSONObject.NULL)
        .put("items", items)
        .put("rating", if (thumbsUp) "up" else "down")
        .toString()
}
