package com.gusanitolabs.robia.suggestions

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gusanitolabs.robia.BuildConfig
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.data.EmbeddingRepository
import com.gusanitolabs.robia.data.GarmentVectors
import com.gusanitolabs.robia.suggestions.engine.ImageInputEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Largest group the review shows; a bigger connected component is split by its strongest pairs (REQ-DU-02). */
const val DUPLICATE_GROUP_MAX_SIZE = 6

/** A rescan is due once this many garments were added since the last scan (REQ-DU-04). */
const val DUPLICATE_RESCAN_NEW_GARMENTS = 5

/** Number of same-category pairs the calibration screen shows (G-DUP). */
const val CALIBRATION_PAIR_COUNT = 30

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private const val DUPLICATE_LOG_TAG = "RobiaDuplicates"

/** The tag category whose tags name a garment's kind (DefaultTags). */
private const val CATEGORY_CATEGORY_ID = "category"

/** An unordered garment pair. Build with [of] so that (a, b) and (b, a) are equal. */
data class DuplicatePairKey(val first: String, val second: String) {
    companion object {
        fun of(a: String, b: String): DuplicatePairKey = if (a <= b) DuplicatePairKey(a, b) else DuplicatePairKey(b, a)
    }
}

/** DUP1 produces [SamePhoto] only; the cosine tier arrives with DUP2 after calibration (G-DUP). */
enum class DuplicateTier { SamePhoto }

/** Garments that may be the same item. [garmentIds] are sorted and hold 2..[DUPLICATE_GROUP_MAX_SIZE] ids. */
data class DuplicateGroup(val garmentIds: List<String>, val tier: DuplicateTier) {
    /** Every unordered pair inside the group; "Not duplicates" dismisses all of them. */
    fun pairKeys(): Set<DuplicatePairKey> =
        garmentIds.unorderedPairs().map { (a, b) -> DuplicatePairKey.of(a, b) }.toSet()
}

/** One calibration candidate: two garments of the same category and their IMAGE cosine. */
data class CalibrationPair(val a: String, val b: String, val cosine: Float)

/** When a scan ran and how many active garments it saw. */
data class DuplicateScanRecord(val atEpochMillis: Long, val garmentCount: Int)

/**
 * The scan of this process. It is not persisted, so a restart scans again on the next Browse visit; persisting it
 * needs a settings key that DUP1 does not add.
 */
object DuplicateScanHistory {
    @Volatile
    var last: DuplicateScanRecord? = null
}

/**
 * The duplicate groups and "Not duplicates" dismissals of this process. Activity recreation (rotation, theme change)
 * keeps them; nothing is written to disk. Persisted dismissals arrive with DUP2 (REQ-DU-02).
 */
object DuplicateSession {
    private val _groups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val groups: StateFlow<List<DuplicateGroup>> = _groups.asStateFlow()
    private val _dismissals = MutableStateFlow(emptySet<DuplicatePairKey>())
    val dismissals: StateFlow<Set<DuplicatePairKey>> = _dismissals.asStateFlow()

    fun replaceGroups(scanned: List<DuplicateGroup>) {
        _groups.value = scanned
    }

    /** "Not duplicates": the group leaves the list and none of its pairs merge again in this process. */
    fun dismiss(group: DuplicateGroup) {
        _dismissals.update { it + group.pairKeys() }
        _groups.update { it - group }
    }

    /** Garments that left the active wardrobe (archived or deleted, on any route) leave their groups. */
    fun keepActive(activeIds: Set<String>) {
        _groups.update { it.keepingActive(activeIds) }
    }

    /** Tests only: the holder is process-wide. */
    internal fun clear() {
        _groups.value = emptyList()
        _dismissals.value = emptySet()
    }
}

/**
 * Exact tier (REQ-DU-01 "Same photo"): active garments whose stored IMAGE `input_hash` equals the hash of the current
 * photo and that share that hash. Garments with a stale or missing vector are left out. [dismissed] pairs are ignored.
 */
fun scan(
    items: List<ClothingItem>,
    vectors: Map<String, GarmentVectors>,
    currentPhotoHashes: Map<String, String>,
    dismissed: Set<DuplicatePairKey> = emptySet(),
): List<DuplicateGroup> {
    val edges = eligibleGarments(items, vectors, currentPhotoHashes)
        .groupBy(Eligible::inputHash)
        .values
        .filter { sameHash -> sameHash.size > 1 }
        .flatMap { sameHash -> sameHash.map(Eligible::id).unorderedPairs() }
        .map { (a, b) -> Edge(a, b, strength = 1f) }
        .filterNot { edge -> DuplicatePairKey.of(edge.a, edge.b) in dismissed }
    return groupsFromEdges(edges, DuplicateTier.SamePhoto, dismissed)
}

/**
 * Same-category pairs with their IMAGE cosine, most similar first, for the developer calibration screen. Only
 * garments eligible for [scan] count. Pairs with no category are left out.
 */
fun calibrationPairs(
    items: List<ClothingItem>,
    vectors: Map<String, GarmentVectors>,
    currentPhotoHashes: Map<String, String>,
    top: Int = CALIBRATION_PAIR_COUNT,
): List<CalibrationPair> {
    val garments = eligibleGarments(items, vectors, currentPhotoHashes).filter { it.category != null }
    val unit = garments.map { it.vector.normalized() }
    return garments.indices
        .flatMap { i -> (i + 1 until garments.size).map { j -> i to j } }
        .filter { (i, j) -> garments[i].category == garments[j].category }
        .map { (i, j) -> CalibrationPair(garments[i].id, garments[j].id, dot(unit[i], unit[j])) }
        .sortedWith(compareByDescending<CalibrationPair> { it.cosine }.thenBy { it.a }.thenBy { it.b })
        .take(top)
}

/**
 * Export of the calibration marks: `[{"a","b","cos","same"}]`, in the order of [pairs]. `same` is null for a pair
 * that is not marked yet. The garment ids are written, never the photos.
 */
fun calibrationMarksJson(pairs: List<CalibrationPair>, marks: Map<Int, Boolean>): String =
    JSONArray().apply {
        pairs.forEachIndexed { index, pair ->
            put(
                JSONObject()
                    .put("a", pair.a)
                    .put("b", pair.b)
                    .put("cos", String.format(Locale.ROOT, "%.6f", pair.cosine).toDouble())
                    .put("same", marks[index] ?: JSONObject.NULL),
            )
        }
    }.toString()

/** Groups without the garments not in [activeIds]; a group left with fewer than two garments is no longer a duplicate. */
fun List<DuplicateGroup>.keepingActive(activeIds: Set<String>): List<DuplicateGroup> = mapNotNull { group ->
    group.copy(garmentIds = group.garmentIds.filter(activeIds::contains)).takeIf { it.garmentIds.size >= 2 }
}

/**
 * REQ-DU-04: a scan is due before the first run, after [DUPLICATE_RESCAN_NEW_GARMENTS] new garments, or once a day
 * when the number of garments changed.
 */
fun duplicateScanDue(last: DuplicateScanRecord?, garmentCount: Int, nowEpochMillis: Long): Boolean {
    if (last == null) return true
    val change = garmentCount - last.garmentCount
    return change >= DUPLICATE_RESCAN_NEW_GARMENTS || (change != 0 && nowEpochMillis - last.atEpochMillis >= DAY_MILLIS)
}

/**
 * The background scan (REQ-DU-04): a one-shot vector snapshot, then the current photo hash of the candidates only. A
 * garment whose stored hash is unique cannot pair, so its photo is not re-encoded. [dismissed] is read after the
 * snapshot and the hashes, so a dismissal made while they were read is still honoured. A failed scan returns no groups.
 */
suspend fun scanDuplicates(
    context: Context,
    items: List<ClothingItem>,
    embeddingRepository: EmbeddingRepository,
    dismissed: () -> Set<DuplicatePairKey>,
): List<DuplicateGroup> = withContext(Dispatchers.IO) {
    try {
        val vectors = embeddingRepository.snapshotVectors(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID)
        val currentHashes = currentPhotoHashes(context, sharedHashCandidates(items, vectors), items)
        scan(items, vectors, currentHashes, dismissed())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        if (BuildConfig.DEBUG) Log.w(DUPLICATE_LOG_TAG, "Duplicate scan failed: ${failure::class.java.simpleName}", failure)
        emptyList()
    }
}

/**
 * The calibration list (G-DUP): every active garment is checked, because same-category pairs are not limited to a shared
 * hash. A failed scan returns no pairs.
 */
suspend fun scanCalibrationPairs(
    context: Context,
    items: List<ClothingItem>,
    embeddingRepository: EmbeddingRepository,
): List<CalibrationPair> = withContext(Dispatchers.IO) {
    try {
        val vectors = embeddingRepository.snapshotVectors(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID)
        val active = items.filterNot(ClothingItem::isArchived).map(ClothingItem::id).toSet()
        val currentHashes = currentPhotoHashes(context, active, items)
        calibrationPairs(items, vectors, currentHashes)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        if (BuildConfig.DEBUG) Log.w(DUPLICATE_LOG_TAG, "Calibration scan failed: ${failure::class.java.simpleName}", failure)
        emptyList()
    }
}

/** Active garments whose stored IMAGE hash is shared with another garment that has an IMAGE vector. */
internal fun sharedHashCandidates(items: List<ClothingItem>, vectors: Map<String, GarmentVectors>): Set<String> =
    items
        .filterNot(ClothingItem::isArchived)
        .mapNotNull { item ->
            val garment = vectors[item.id] ?: return@mapNotNull null
            if (garment.image == null) null else garment.imageInputHash?.let { hash -> hash to item.id }
        }
        .groupBy({ it.first }, { it.second })
        .values
        .filter { sameHash -> sameHash.size > 1 }
        .flatten()
        .toSet()

private suspend fun currentPhotoHashes(context: Context, ids: Set<String>, items: List<ClothingItem>): Map<String, String> {
    val photos = items.associateBy(ClothingItem::id)
    return ids.mapNotNull { id ->
        val uri = photos[id]?.photoUri?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        currentPhotoHash(context, uri)?.let { hash -> id to hash }
    }.toMap()
}

/** SHA-256 of the encoded input, the same value the IMAGE vector stored as `input_hash`. Null when the photo cannot be read. */
private suspend fun currentPhotoHash(context: Context, uri: String): String? = try {
    sha256Hex16(ImageInputEncoder.encode(context, Uri.parse(uri)).bytes)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    null
}

/** An active garment whose stored IMAGE vector still matches its current photo (REQ-DU-01). */
private class Eligible(val id: String, val vector: FloatArray, val inputHash: String, val category: String?)

private class Edge(val a: String, val b: String, val strength: Float)

private fun eligibleGarments(
    items: List<ClothingItem>,
    vectors: Map<String, GarmentVectors>,
    currentPhotoHashes: Map<String, String>,
): List<Eligible> = items
    .filterNot(ClothingItem::isArchived)
    .sortedBy(ClothingItem::id)
    .mapNotNull { item ->
        val garment = vectors[item.id] ?: return@mapNotNull null
        val vector = garment.image ?: return@mapNotNull null
        val storedHash = garment.imageInputHash ?: return@mapNotNull null
        if (currentPhotoHashes[item.id] != storedHash) return@mapNotNull null
        Eligible(item.id, vector, storedHash, item.tags.firstOrNull { it.categoryId == CATEGORY_CATEGORY_ID }?.id)
    }

/**
 * Connected components of [edges], capped at [DUPLICATE_GROUP_MAX_SIZE]. Edges are taken strongest first and an edge
 * that would join two groups past the cap is skipped, so a large component splits by its strongest pairs. Two groups
 * never join when a [dismissed] pair links them, so a dismissed pair never ends up in one group (R3-DEC-026).
 */
private fun groupsFromEdges(
    edges: List<Edge>,
    tier: DuplicateTier,
    dismissed: Set<DuplicatePairKey>,
): List<DuplicateGroup> {
    val parent = HashMap<String, String>()
    val members = HashMap<String, MutableList<String>>()
    fun root(id: String): String {
        val p = parent.getOrPut(id) { id }
        return if (p == id) id else root(p).also { parent[id] = it }
    }
    edges
        .sortedWith(compareByDescending<Edge> { it.strength }.thenBy { it.a }.thenBy { it.b })
        .forEach { edge ->
            val ra = root(edge.a)
            val rb = root(edge.b)
            if (ra == rb) return@forEach
            val ma = members.getOrPut(ra) { mutableListOf(ra) }
            val mb = members.getOrPut(rb) { mutableListOf(rb) }
            if (ma.size + mb.size > DUPLICATE_GROUP_MAX_SIZE) return@forEach
            if (ma.any { a -> mb.any { b -> DuplicatePairKey.of(a, b) in dismissed } }) return@forEach
            parent[rb] = ra
            ma.addAll(mb)
            members.remove(rb)
        }
    return members.values
        .filter { it.size >= 2 }
        .map { DuplicateGroup(it.sorted(), tier) }
        .sortedBy { it.garmentIds.first() }
}

private fun <T> List<T>.unorderedPairs(): List<Pair<T, T>> =
    indices.flatMap { i -> (i + 1 until size).map { j -> this[i] to this[j] } }
