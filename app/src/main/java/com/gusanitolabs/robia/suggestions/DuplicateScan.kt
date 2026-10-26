package com.gusanitolabs.robia.suggestions

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.data.EmbeddingRepository
import com.gusanitolabs.robia.data.GarmentVectors
import com.gusanitolabs.robia.suggestions.engine.ImageInputEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap

/** Largest group the review shows; a bigger connected component is split by its strongest pairs (REQ-DU-02). */
const val DUPLICATE_GROUP_MAX_SIZE = 6

/** Number of same-category pairs the calibration screen shows (G-DUP). */
const val CALIBRATION_PAIR_COUNT = 30

private const val DUPLICATE_LOG_TAG = "RobiaDuplicates"

/** Photos hashed at once; each decodes a bitmap of about 4 MB (REQ-FB-18 a). */
private const val HASH_PARALLELISM = 3

/** Rows of the pair pass between two yields, so a long calibration stays cancellable. */
private const val ROWS_PER_YIELD = 32

/** The tag category whose tags name a garment's kind (DefaultTags). */
private const val CATEGORY_CATEGORY_ID = "category"

/** An unordered garment pair. Build with [of] so that (a, b) and (b, a) are equal. */
data class DuplicatePairKey(val first: String, val second: String) {
    companion object {
        fun of(a: String, b: String): DuplicatePairKey = if (a <= b) DuplicatePairKey(a, b) else DuplicatePairKey(b, a)
    }
}

/**
 * [SamePhoto]: the group holds two garments with the same stored photo hash. [VerySimilar]: the cosine tier (REQ-DU-01 tier 2),
 * for a group with no such pair.
 */
enum class DuplicateTier { SamePhoto, VerySimilar }

/**
 * Cosine from which two same-category garments of the same primary colour are "Very similar" (R3-DEC-047). Fixed by Manu:
 * different black dresses reach 0.955-0.977, so the persisted dismissals ship with this tier. Do not change it here.
 */
const val T_DUP = 0.96f

/** Garments that may be the same item. [garmentIds] are sorted and hold 2..[DUPLICATE_GROUP_MAX_SIZE] ids. */
data class DuplicateGroup(val garmentIds: List<String>, val tier: DuplicateTier) {
    /** Every unordered pair inside the group; "Not duplicates" dismisses all of them. */
    fun pairKeys(): Set<DuplicatePairKey> =
        garmentIds.unorderedPairs().map { (a, b) -> DuplicatePairKey.of(a, b) }.toSet()
}

/** One calibration candidate: two garments of the same category and their IMAGE cosine. */
data class CalibrationPair(val a: String, val b: String, val cosine: Float)

/**
 * Why a calibration list can be short. A funnel: each count is over the garments that passed the step before it, so
 * [categorised] is how many garments can form a pair.
 */
data class CalibrationCounts(val active: Int, val withVector: Int, val hashMatched: Int, val categorised: Int) {
    companion object {
        val NONE = CalibrationCounts(active = 0, withVector = 0, hashMatched = 0, categorised = 0)
    }
}

/** The calibration pairs of one scan, with the counts that explain an empty list. */
data class CalibrationScan(val pairs: List<CalibrationPair>, val counts: CalibrationCounts)

/** The result of one scan: [Success] with its groups, or [Failed] when the vectors or the photos could not be read. */
sealed interface DuplicateScanOutcome {
    data class Success(val groups: List<DuplicateGroup>) : DuplicateScanOutcome
    data object Failed : DuplicateScanOutcome
}

/**
 * The [duplicateScanSignature] of the last successful scan of this process. It is not persisted, so a restart scans
 * again on the next Browse visit; persisting it needs a settings key that DUP1 does not add.
 */
object DuplicateScanHistory {
    @Volatile
    var lastSignature: String? = null
}

/**
 * The duplicate groups and "Not duplicates" dismissals of this process. Activity recreation (rotation, theme change)
 * keeps them. The dismissals are also stored in Room (REQ-DU-02): [dismiss] does not write them, the caller does, and
 * [loadDismissals] reads the stored ones back before a scan.
 */
object DuplicateSession {
    private val _groups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val groups: StateFlow<List<DuplicateGroup>> = _groups.asStateFlow()
    private val _dismissals = MutableStateFlow(emptySet<DuplicatePairKey>())
    val dismissals: StateFlow<Set<DuplicatePairKey>> = _dismissals.asStateFlow()

    /** The photo hashes the background scans of this process have read; see [PhotoHashCache]. */
    internal val photoHashes = PhotoHashCache()

    fun replaceGroups(scanned: List<DuplicateGroup>) {
        _groups.value = scanned
    }

    /** "Not duplicates": the group leaves the list and none of its pairs merge again in this process. */
    fun dismiss(group: DuplicateGroup) {
        _dismissals.update { it + group.pairKeys() }
        _groups.update { it - group }
    }

    /** The dismissals stored on this phone, read before a scan so that a dismissed pair stays out after a restart. */
    fun loadDismissals(stored: Set<DuplicatePairKey>) {
        _dismissals.update { it + stored }
    }

    /** Garments that left the active wardrobe (archived or deleted, on any route) leave their groups. */
    fun keepActive(activeIds: Set<String>) {
        _groups.update { it.keepingActive(activeIds) }
    }

    /** Tests only: the holder is process-wide. */
    internal fun clear() {
        _groups.value = emptyList()
        _dismissals.value = emptySet()
        photoHashes.clear()
    }
}

/**
 * The current photo hash of each (garment, photo URI) of this process. A photo that is replaced gets a new URI (see
 * [duplicateScanSignature]), so its old entry is never read again. Never persisted: a restart hashes every photo once more.
 * [retainOnly] drops the entries of garments that left the active set and of photos that were replaced.
 */
internal class PhotoHashCache {
    private val hashes = ConcurrentHashMap<Pair<String, String>, String>()

    fun get(garmentId: String, photoUri: String): String? = hashes[garmentId to photoUri]

    fun put(garmentId: String, photoUri: String, hash: String) {
        hashes[garmentId to photoUri] = hash
    }

    fun retainOnly(active: Set<Pair<String, String>>) {
        hashes.keys.retainAll(active)
    }

    fun clear() {
        hashes.clear()
    }
}

/**
 * Two tiers (REQ-DU-01). "Same photo": active garments whose stored IMAGE `input_hash` equals the hash of the current photo
 * and that share that hash. "Very similar" (tier 2): a same-category pair with cosine ≥ [T_DUP] and the same primary colour
 * when both have one. A group is [DuplicateTier.SamePhoto] when two of its garments share a hash, else [VerySimilar].
 * Garments with a stale or missing vector are left out. [dismissed] pairs are never in one group.
 */
fun scan(
    items: List<ClothingItem>,
    vectors: Map<String, GarmentVectors>,
    currentPhotoHashes: Map<String, String>,
    dismissed: Set<DuplicatePairKey> = emptySet(),
): List<DuplicateGroup> {
    val garments = eligibleGarments(items, vectors, currentPhotoHashes)
    val samePhotoEdges = garments
        .groupBy(Eligible::inputHash)
        .values
        .filter { sameHash -> sameHash.size > 1 }
        .flatMap { sameHash -> sameHash.map(Eligible::id).unorderedPairs() }
        .map { (a, b) -> Edge(a, b, strength = 1f) }
    val garmentsById = garments.associateBy(Eligible::id)
    val verySimilarEdges = mutableListOf<Edge>()
    scanBlocks(garments, onRow = {}) { a, b, cosine ->
        if (cosine >= T_DUP && sameColourIfKnown(garmentsById.getValue(a), garmentsById.getValue(b))) {
            verySimilarEdges += Edge(a, b, strength = cosine)
        }
    }
    val hashById = garments.associate { garment -> garment.id to garment.inputHash }
    return groupsFromEdges(
        edges = (samePhotoEdges + verySimilarEdges).filterNot { edge -> DuplicatePairKey.of(edge.a, edge.b) in dismissed },
        hashById = hashById,
        dismissed = dismissed,
    )
}

/** Same primary colour when both garments have one; a garment without a primary colour does not block the pair (R3-DEC-047). */
private fun sameColourIfKnown(a: Eligible, b: Eligible): Boolean =
    a.primaryColour == null || b.primaryColour == null || a.primaryColour == b.primaryColour

/**
 * Same-category pairs with their IMAGE cosine, most similar first, for the developer calibration screen. Only
 * garments eligible for [scan] count. Pairs with no category are left out.
 */
fun calibrationPairs(
    items: List<ClothingItem>,
    vectors: Map<String, GarmentVectors>,
    currentPhotoHashes: Map<String, String>,
    top: Int = CALIBRATION_PAIR_COUNT,
): List<CalibrationPair> = topCalibrationPairs(items, vectors, currentPhotoHashes, top, onRow = {})

/**
 * The [top] most similar same-category pairs, found block by block: no list of all pairs is built and only the best
 * [top] are kept, in the order of the old all-pairs sort (cosine desc, then ids). [onRow] runs once per row of the pass.
 */
internal inline fun topCalibrationPairs(
    items: List<ClothingItem>,
    vectors: Map<String, GarmentVectors>,
    currentPhotoHashes: Map<String, String>,
    top: Int,
    onRow: () -> Unit,
): List<CalibrationPair> {
    val best = CalibrationTop(top)
    scanBlocks(eligibleGarments(items, vectors, currentPhotoHashes), onRow) { a, b, cosine -> best.offer(a, b, cosine) }
    return best.sorted()
}

/**
 * Visits every pair inside a category block once, with its IMAGE cosine. [garments] are in id order, so each pair is
 * visited as (a, b) with a < b. [onRow] runs once per row. Returns the number of pairs visited. DUP2 reuses this pass.
 */
internal inline fun scanBlocks(
    garments: List<Eligible>,
    onRow: () -> Unit,
    visit: (a: String, b: String, cosine: Float) -> Unit,
): Long {
    var examined = 0L
    for (block in blocksByCategory(garments)) {
        val packed = packUnitVectors(block)
        val dimension = block.first().vector.size
        for (i in block.indices) {
            onRow()
            for (j in i + 1 until block.size) {
                visit(block[i].id, block[j].id, blockDot(packed, dimension, i, j))
                examined++
            }
        }
    }
    return examined
}

/** The eligible garments that have a category, one list per category, each in the id order of [garments]. */
internal fun blocksByCategory(garments: List<Eligible>): Collection<List<Eligible>> =
    garments.filter { garment -> garment.category != null }.groupBy(Eligible::category).values

/** The unit vectors of one block in one array: row r occupies [r * dimension, (r + 1) * dimension). */
internal fun packUnitVectors(block: List<Eligible>): FloatArray {
    val dimension = block.first().vector.size
    val packed = FloatArray(block.size * dimension)
    block.forEachIndexed { row, garment ->
        val unit = garment.vector.normalized()
        require(unit.size == dimension) { "Vector sizes differ: ${unit.size} vs $dimension" }
        unit.copyInto(packed, row * dimension)
    }
    return packed
}

/** [dot] of two rows of a packed block, with the same sum in the same order, so the values equal the all-pairs ones. */
internal fun blockDot(packed: FloatArray, dimension: Int, i: Int, j: Int): Float {
    var sum = 0.0
    val rowI = i * dimension
    val rowJ = j * dimension
    for (k in 0 until dimension) sum += packed[rowI + k].toDouble() * packed[rowJ + k].toDouble()
    return sum.toFloat()
}

/** The best [limit] pairs so far. A heap whose head is the weakest pair, so it never holds more than [limit] pairs. */
internal class CalibrationTop(private val limit: Int) {
    private val weakestFirst = PriorityQueue<CalibrationPair>(maxOf(limit, 1)) { x, y -> BEST_FIRST.compare(y, x) }

    val size: Int get() = weakestFirst.size

    fun offer(a: String, b: String, cosine: Float) {
        if (limit <= 0) return
        if (weakestFirst.size < limit) {
            weakestFirst.add(CalibrationPair(a, b, cosine))
            return
        }
        val weakest = weakestFirst.peek()
        // Most pairs lose on cosine alone; only an equal cosine needs the ids.
        if (cosine < weakest.cosine) return
        val pair = CalibrationPair(a, b, cosine)
        if (BEST_FIRST.compare(pair, weakest) >= 0) return
        weakestFirst.poll()
        weakestFirst.add(pair)
    }

    fun sorted(): List<CalibrationPair> = weakestFirst.sortedWith(BEST_FIRST)
}

private val BEST_FIRST = compareByDescending<CalibrationPair> { it.cosine }.thenBy { it.a }.thenBy { it.b }

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
 * Identifies what a scan reads: the id and photo URI of each active garment, sorted by id. Order, name, tags and notes
 * do not change it. A replaced photo has a new URI (a new file name), so it changes it too.
 */
fun duplicateScanSignature(items: List<ClothingItem>): String = items
    .filterNot(ClothingItem::isArchived)
    .sortedBy(ClothingItem::id)
    .joinToString(separator = "\n") { item -> "${item.id}\t${item.photoUri.orEmpty()}" }

/** REQ-DU-04 (R3-DEC-051): a scan is due whenever the active garments differ from the last successful scan's. */
fun duplicateScanDue(lastSignature: String?, signature: String): Boolean = lastSignature != signature

/** The signature to remember after a scan: a Success records [signature]; a Failed keeps [previous], so the scan is retried. */
fun nextDuplicateScanSignature(previous: String?, signature: String, outcome: DuplicateScanOutcome): String? =
    when (outcome) {
        is DuplicateScanOutcome.Success -> signature
        DuplicateScanOutcome.Failed -> previous
    }

/** The groups to show after a scan: a Success replaces [current]; a Failed keeps them. */
fun DuplicateScanOutcome.groupsAfter(current: List<DuplicateGroup>): List<DuplicateGroup> = when (this) {
    is DuplicateScanOutcome.Success -> groups
    DuplicateScanOutcome.Failed -> current
}

/**
 * The background scan (REQ-DU-04): a one-shot vector snapshot, then the current photo hash of every garment that can pair
 * (see [scanHashCandidates]). A photo hashed by an earlier scan of this process is not read again ([DuplicateSession]'s cache).
 * [dismissed] is read after the snapshot and the hashes, so a dismissal made while they were read is still honoured.
 */
suspend fun scanDuplicates(
    context: Context,
    items: List<ClothingItem>,
    embeddingRepository: EmbeddingRepository,
    dismissed: () -> Set<DuplicatePairKey>,
): DuplicateScanOutcome = withContext(Dispatchers.IO) {
    collectDuplicateScan(
        items = items,
        snapshot = { embeddingRepository.snapshotVectors(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID) },
        hash = { uri -> currentPhotoHash(context, uri) },
        dismissed = dismissed,
        onFailure = { failure ->
            Log.w(DUPLICATE_LOG_TAG, "Duplicate scan failed: ${failure::class.java.simpleName}: ${failure.message}")
        },
        cache = DuplicateSession.photoHashes,
    )
}

/**
 * The body of [scanDuplicates] with its inputs injected, so it runs on the JVM. Any failure except cancellation is passed
 * to [onFailure] and gives [DuplicateScanOutcome.Failed]; cancellation propagates. [cache] holds the hashes of earlier scans.
 */
internal suspend fun collectDuplicateScan(
    items: List<ClothingItem>,
    snapshot: suspend () -> Map<String, GarmentVectors>,
    hash: suspend (uri: String) -> String?,
    dismissed: () -> Set<DuplicatePairKey>,
    onFailure: (Exception) -> Unit,
    cache: PhotoHashCache = PhotoHashCache(),
): DuplicateScanOutcome = try {
    val vectors = snapshot()
    val currentHashes = resolvePhotoHashes(scanHashCandidates(items, vectors), items, hash = hash, cache = cache)
    cache.retainOnly(activePhotos(items))
    DuplicateScanOutcome.Success(scan(items, vectors, currentHashes, dismissed()))
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    onFailure(failure)
    DuplicateScanOutcome.Failed
}

/**
 * The calibration list (G-DUP): every active garment is checked, because same-category pairs are not limited to a shared
 * hash. A failed scan returns no pairs and [CalibrationCounts.NONE], and logs one line outside DEBUG (no photo data).
 * [onProgress] reports (done, total) while the photos are prepared (REQ-FB-18 a).
 */
suspend fun scanCalibrationPairs(
    context: Context,
    items: List<ClothingItem>,
    embeddingRepository: EmbeddingRepository,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
): CalibrationScan = withContext(Dispatchers.IO) {
    try {
        val vectors = embeddingRepository.snapshotVectors(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID)
        val active = items.filterNot(ClothingItem::isArchived).map(ClothingItem::id).toSet()
        val currentHashes = resolvePhotoHashes(active, items, hash = { uri -> currentPhotoHash(context, uri) }, onProgress = onProgress)
        var rows = 0
        CalibrationScan(
            pairs = topCalibrationPairs(items, vectors, currentHashes, CALIBRATION_PAIR_COUNT, onRow = {
                if (++rows % ROWS_PER_YIELD == 0) yield()
            }),
            counts = calibrationCounts(items, vectors, currentHashes),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.w(DUPLICATE_LOG_TAG, "Calibration scan failed: ${failure::class.java.simpleName}: ${failure.message}")
        CalibrationScan(emptyList(), CalibrationCounts.NONE)
    }
}

/** The funnel behind [scanCalibrationPairs]: active, with an IMAGE vector, hash matched, then categorised. */
internal fun calibrationCounts(
    items: List<ClothingItem>,
    vectors: Map<String, GarmentVectors>,
    currentPhotoHashes: Map<String, String>,
): CalibrationCounts {
    val active = items.filterNot(ClothingItem::isArchived)
    val eligible = eligibleGarments(items, vectors, currentPhotoHashes)
    return CalibrationCounts(
        active = active.size,
        withVector = active.count { item -> vectors[item.id]?.image != null },
        hashMatched = eligible.size,
        categorised = eligible.count { garment -> garment.category != null },
    )
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

/**
 * The active garments a background scan hashes: every garment with a category and a stored IMAGE hash, since a Very similar
 * pair is always inside one category and its photos differ; plus the [sharedHashCandidates], which keeps SamePhoto for a
 * garment without a category. A garment with no stored hash can never be eligible, so its photo is not read.
 */
internal fun scanHashCandidates(items: List<ClothingItem>, vectors: Map<String, GarmentVectors>): Set<String> =
    sharedHashCandidates(items, vectors) + items
        .filterNot(ClothingItem::isArchived)
        .filter { item -> categoryOf(item) != null && vectors[item.id]?.let { it.image != null && it.imageInputHash != null } == true }
        .map(ClothingItem::id)

/** The (garment id, photo URI) of every active garment with a photo: the keys [PhotoHashCache] keeps. */
private fun activePhotos(items: List<ClothingItem>): Set<Pair<String, String>> = items
    .filterNot(ClothingItem::isArchived)
    .mapNotNull { item -> item.photoUri?.takeIf(String::isNotBlank)?.let { uri -> item.id to uri } }
    .toSet()

/** The category of a garment: the id of its tag in the category tag category, null when it has none. */
private fun categoryOf(item: ClothingItem): String? = item.tags.firstOrNull { it.categoryId == CATEGORY_CATEGORY_ID }?.id

/**
 * The current photo hash of each garment in [ids] that has a photo. At most [HASH_PARALLELISM] photos are read at once.
 * A photo that cannot be hashed leaves its garment out; cancellation propagates and stops the photos not yet started.
 * [onProgress] reports (done, total) before the first photo and after each one, never going backwards. A hash found in
 * [cache] counts as done at once and is not read again; a new hash is put into [cache].
 */
internal suspend fun resolvePhotoHashes(
    ids: Set<String>,
    items: List<ClothingItem>,
    hash: suspend (uri: String) -> String?,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    cache: PhotoHashCache? = null,
): Map<String, String> = coroutineScope {
    val photos = items.associateBy(ClothingItem::id)
    val work = ids.mapNotNull { id -> photos[id]?.photoUri?.takeIf(String::isNotBlank)?.let { uri -> id to uri } }
    val cached = work.mapNotNull { (id, uri) -> cache?.get(id, uri)?.let { id to it } }.toMap()
    val toHash = work.filterNot { (id, _) -> id in cached }
    val gate = Semaphore(HASH_PARALLELISM)
    val progressLock = Mutex()
    var done = cached.size
    onProgress(done, work.size)
    val fresh = toHash.map { (id, uri) ->
        async {
            val hashed = gate.withPermit {
                try {
                    hash(uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    null
                }
            }
            progressLock.withLock { onProgress(++done, work.size) }
            hashed?.also { cache?.put(id, uri, it) }?.let { id to it }
        }
    }.awaitAll().filterNotNull().toMap()
    cached + fresh
}

/** SHA-256 of the encoded input, the same value the IMAGE vector stored as `input_hash`. Null when the photo cannot be read. */
private suspend fun currentPhotoHash(context: Context, uri: String): String? = try {
    sha256Hex16(ImageInputEncoder.encode(context, Uri.parse(uri)).bytes)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    null
}

/**
 * An active garment whose stored IMAGE vector still matches its current photo (REQ-DU-01). [primaryColour] is the palette
 * colour id of the primary colour, null when the garment has none.
 */
internal class Eligible(
    val id: String,
    val vector: FloatArray,
    val inputHash: String,
    val category: String?,
    val primaryColour: String? = null,
)

private class Edge(val a: String, val b: String, val strength: Float)

internal fun eligibleGarments(
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
        Eligible(
            id = item.id,
            vector = vector,
            inputHash = storedHash,
            category = categoryOf(item),
            primaryColour = item.colorMetrics.primaryPaletteColorId,
        )
    }

/**
 * Connected components of [edges], capped at [DUPLICATE_GROUP_MAX_SIZE]. Edges are taken strongest first and an edge
 * that would join two groups past the cap is skipped, so a large component splits by its strongest pairs. Two groups
 * never join when a [dismissed] pair links them, so a dismissed pair never ends up in one group (R3-DEC-026).
 */
private fun groupsFromEdges(
    edges: List<Edge>,
    hashById: Map<String, String>,
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
        .map { ids -> DuplicateGroup(ids.sorted(), tierOf(ids, hashById)) }
        .sortedBy { it.garmentIds.first() }
}

/** [DuplicateTier.SamePhoto] when two garments of the group share a stored photo hash, else [DuplicateTier.VerySimilar]. */
private fun tierOf(ids: List<String>, hashById: Map<String, String>): DuplicateTier =
    if (ids.groupBy { id -> hashById.getValue(id) }.values.any { sameHash -> sameHash.size > 1 }) {
        DuplicateTier.SamePhoto
    } else {
        DuplicateTier.VerySimilar
    }

private fun <T> List<T>.unorderedPairs(): List<Pair<T, T>> =
    indices.flatMap { i -> (i + 1 until size).map { j -> this[i] to this[j] } }
