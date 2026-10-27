package com.gusanitolabs.robia.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.media.ImagePurpose
import com.gusanitolabs.robia.suggestions.AppliedDelta
import com.gusanitolabs.robia.suggestions.ApplyResult
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.ReviewButton
import com.gusanitolabs.robia.suggestions.ReviewPlanner
import com.gusanitolabs.robia.suggestions.ReviewProposal
import com.gusanitolabs.robia.suggestions.TagChangeSet
import com.gusanitolabs.robia.suggestions.TagDelta
import com.gusanitolabs.robia.suggestions.TagOperation
import com.gusanitolabs.robia.suggestions.engine.EmbeddingFailure
import com.gusanitolabs.robia.suggestions.noTagsLeft
import com.gusanitolabs.robia.suggestions.labelTextFor
import com.gusanitolabs.robia.suggestions.primaryButtonFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private const val TAG_REVIEW_THUMBNAIL_MAX_EDGE_PX = 384
internal const val TAG_KIND_CATEGORY = "category"
private const val TAG_KIND_OCCASION = "occasion"
private const val TAG_REVIEW_SCAN_LOG_TAG = "TagReviewScan"
private const val TAG_REVIEW_OFFER_LOG_TAG = "TagReviewOffer"
private val ReviewedTagKinds = setOf(TAG_KIND_CATEGORY, TAG_KIND_OCCASION)

internal data class TagReviewCandidate(
    val item: ClothingItem,
    val proposal: ReviewProposal,
    /** [item] as the database holds it after the change (see [liveViewOf]): the tags the card reads and the commit writes onto. */
    val live: ClothingItem,
)

/**
 * The change set plus the garments as they were before the change was saved (a deleted tag is still on them). Archived
 * garments are included so the undo can put a deleted tag back on them; the scan only reads the active ones.
 */
internal data class TagReviewRequest(
    val changeSet: TagChangeSet,
    val garments: List<ClothingItem>,
)

/**
 * Garments the change affects, for the header. A deletion counts the garments that carried a deleted tag, known before
 * the scan. Add and rename only know it as the scan runs, so they count the proposals found so far.
 */
internal fun affectedGarmentCount(changeSet: TagChangeSet, garments: List<ClothingItem>, proposalCount: Int): Int =
    if (changeSet.operation == TagOperation.Deleted) {
        garments.count { garment -> garment.tags.any { tag -> tag.id in changeSet.touchedTagIds } }
    } else {
        proposalCount
    }

/** Picker label for a tag: the localized label, else the stored name, else the id, never blank. */
internal fun tagPickerLabel(tagId: String, name: String, labelsById: Map<String, String>): String =
    labelsById[tagId]?.takeIf(String::isNotBlank) ?: name.takeIf(String::isNotBlank) ?: tagId

/** Picker title: the capitalised kind on its own when the garment has no name, so it never reads `for “”`. */
internal fun tagReviewPickerTitle(noun: String, garmentName: String, namedTitle: String): String =
    if (garmentName.isBlank()) noun.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) } else namedTitle

/**
 * A card can be decided only when its choice is made: a `needsChoice` card without a chosen tag writes nothing and stays
 * Pending, so the review stays open until the user picks a tag (or leaves).
 */
internal fun canDecideNow(card: ReviewCardState, chosenTagId: String?): Boolean =
    !card.changes.needsChoice || chosenTagId != null

/**
 * Back with nothing applied discards the review (the whole tag change is undone); with a garment applied it is left. The count
 * is [TagReviewSession.appliedGarmentCount], so a write still in flight counts: its undo would run after it otherwise.
 */
internal fun tagReviewLeaveKind(appliedCount: Int): LeaveKind = TagReviewSession().leaveKind(appliedCount)

/** Applied cards whose write has come back with a change: a result with no item, and a write in flight, are not counted. */
internal fun writtenGarmentCount(session: TagReviewSession): Int = session.cards.count { card ->
    val applied = (card.decision as? ReviewDecision.Applied)?.applied
    applied != null && (applied.removed.isNotEmpty() || applied.added.isNotEmpty())
}

/** True while a decided card's write has not come back yet. */
internal fun writesInFlight(session: TagReviewSession): Boolean = session.cards.any { card ->
    val decision = card.decision
    decision is ReviewDecision.Applied && decision.applied == null
}

/** Done is available once the scan has ended and no write is in flight: a write's undo must not race a finish. */
internal fun doneEnabled(isScanning: Boolean, session: TagReviewSession): Boolean = !isScanning && !writesInFlight(session)

/**
 * Done ends the review as the automatic finish does, once: not while a write is in flight. Cards still Pending are left as they
 * are; nothing is written for them.
 */
internal fun doneFinishes(finished: Boolean, session: TagReviewSession): Boolean = !finished && !writesInFlight(session)

/**
 * The review finishes once the scan has ended and every found card is decided, with no write or revert in flight, no picker or
 * discard dialog open, and only once ([finished]). A revert in flight keeps its garment Applied, so the review waits for it:
 * a landed revert puts the card back to Pending, a failed one keeps it decided and the finish follows with the right count.
 */
internal fun shouldFinishTagReview(
    session: TagReviewSession,
    isScanning: Boolean,
    pickerOpen: Boolean,
    discardDialogOpen: Boolean,
    finished: Boolean,
    revertsInFlight: Boolean,
): Boolean =
    !finished && !pickerOpen && !discardDialogOpen && !revertsInFlight && session.isComplete(isScanning) && !writesInFlight(session)

/**
 * The session after the write of [garmentIds] came back: each result decides its card; a failed write puts the cards back
 * in the list as Pending, with their chips kept.
 */
internal fun TagReviewSession.afterWrite(garmentIds: List<String>, outcome: Result<List<ApplyResult>>): TagReviewSession =
    outcome.fold(
        onSuccess = { results -> results.fold(this) { session, result -> session.appliedResult(result.applied.garmentId, result) } },
        onFailure = { garmentIds.fold(this) { session, garmentId -> session.applyFailed(garmentId) } },
    )

/**
 * The key of a card's list item. A card whose write failed comes back with a new key, so the exit animation it had
 * finished starts over and the card shows again.
 */
internal fun reviewCardKey(garmentId: String, returns: Int): String = if (returns == 0) garmentId else "$garmentId#$returns"

/** What a decision says in the undo snackbar and in the Reviewed section: changes written (a swap counts as one), or kept as it was. */
internal sealed interface ReviewOutcome {
    data class Changes(val units: Int) : ReviewOutcome

    data object KeptAsIs : ReviewOutcome
}

/** The undo snackbar: the outcome of the latest decision (null for Got it, which says nothing). [serial] makes each decision a new one. */
private data class ReviewUndoSnack(val garmentId: String, val outcome: ReviewOutcome?, val serial: Int)

/** The units the card's button counts: what Apply writes. */
internal fun cardUnits(card: ReviewCardState): Int =
    when (val button = primaryButtonFor(card.changes, card.skipped, card.chosenTagId)) {
        is ReviewButton.Apply -> button.count
        is ReviewButton.ApplyPartial -> button.on
        ReviewButton.KeepAsIs, ReviewButton.GotIt -> 0
    }

/**
 * The snackbar outcome of a decided card, or null when there is none: Got it writes nothing and changes nothing. A rejection,
 * and an apply whose write found nothing to change (a null item, see [TagReviewSession.appliedResult]), is kept as it was.
 * An apply in flight counts its units already.
 */
internal fun undoSnackOutcome(card: ReviewCardState): ReviewOutcome? =
    when (val decision = card.decision) {
        ReviewDecision.Pending -> null
        is ReviewDecision.Rejected -> ReviewOutcome.KeptAsIs
        is ReviewDecision.Applied -> if (decision.intended.isEmpty) null else ReviewOutcome.Changes(cardUnits(card))
    }

/** The outcome a Reviewed row shows: Got it reads as kept, since nothing changed. */
internal fun reviewOutcomeOf(card: ReviewCardState): ReviewOutcome = undoSnackOutcome(card) ?: ReviewOutcome.KeptAsIs

/**
 * Undo and "Review again" are for a card that was applied (and written) or rejected. Refused while its write is in flight
 * (the session refuses too) and while its revert is running ([reverting]), so a second Undo for the same decision does nothing.
 */
internal fun canUndoNow(session: TagReviewSession, garmentId: String, reverting: Collection<String>): Boolean {
    if (garmentId in reverting) return false
    return when (val decision = session.cards.firstOrNull { card -> card.garmentId == garmentId }?.decision) {
        is ReviewDecision.Applied -> decision.applied != null
        is ReviewDecision.Rejected -> true
        ReviewDecision.Pending, null -> false
    }
}

/**
 * A revert landed when its write came back and every result has its garment. A null item (an archived or missing garment, or
 * a revert that finds nothing left to reverse, which the write cannot tell apart) is a failed revert: the garment stays decided.
 */
internal fun revertLanded(outcome: Result<List<ApplyResult>>): Boolean =
    outcome.getOrNull()?.all { result -> result.item != null } == true

/** A landed revert takes its garment out of the written list: the finish counts what is still written. */
internal fun MutableList<ClothingItem>.removeWritten(garmentId: String) {
    removeAll { written -> written.id == garmentId }
}

/**
 * Snapshot, then write, in one coroutine. The snapshot is read after the previous review write's database part, never its
 * sync (RobiaApp), and the write always lands. Returns the request to offer: null when there is no change set, when the
 * snapshot failed, or when no active garment would be reviewed.
 */
internal suspend fun offerTagReviewRequest(
    changeSet: TagChangeSet?,
    readGarments: suspend () -> List<ClothingItem>,
    write: () -> Unit,
): TagReviewRequest? {
    val garments = try {
        readGarments()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        // No snapshot, no offer: the review could not restore the garments it would touch. The write still lands.
        Log.e(TAG_REVIEW_OFFER_LOG_TAG, "Review snapshot failed; the change is saved without a review offer", failure)
        null
    }
    write()
    if (changeSet == null || garments == null || !hasReviewableGarments(garments, ReviewKind.Tag)) return null
    return TagReviewRequest(changeSet, garments)
}

/**
 * Label text the review scan embeds for a tag. An added or restored tag with no stored description is labelled by its bare
 * name, like a custom tag: a default's built-in description ranks close to its neighbours (Dresses against Jumpsuits) and
 * then matches no garment. Every other tag keeps [labelTextFor].
 */
internal fun reviewScanLabelText(tag: GarmentTag, touchedTagIds: Set<String>): String =
    if (tag.id in touchedTagIds && tag.description.isNullOrBlank()) {
        EmbeddingRecipe.CLASSIFICATION_PREFIX + tag.name
    } else {
        labelTextFor(tag)
    }

/** Debug log line per review scan: the label text used for each touched tag and how many proposals were found. */
internal fun tagReviewScanLogLine(changeSet: TagChangeSet, proposalCount: Int): String {
    val labels = changeSet.afterTags
        .filter { tag -> tag.categoryId == changeSet.categoryId && tag.id in changeSet.touchedTagIds }
        .joinToString(" | ") { tag -> reviewScanLabelText(tag, changeSet.touchedTagIds) }
    return "${changeSet.operation} ${changeSet.categoryId} labels=[$labels] proposals=$proposalCount"
}

/**
 * Change set for a saved tag (TS§3.10). Null for a new tag of another kind, or when the text the garments are matched on
 * did not change: a description edit changes it (and so triggers the review), a suggest-occasions switch does not.
 */
internal fun savedTagChangeSet(before: List<GarmentTag>, saved: GarmentTag): TagChangeSet? {
    if (saved.categoryId !in ReviewedTagKinds) return null
    val existing = before.firstOrNull { tag -> tag.id == saved.id }
    val operation = when {
        existing == null -> TagOperation.Added
        labelTextFor(existing) == labelTextFor(saved) -> return null
        else -> TagOperation.Renamed
    }
    val after = before.filterNot { tag -> tag.id == saved.id } + saved
    return tagChangeSet(operation, saved.categoryId, setOf(saved.id), before, after)
}

/** Categories and occasions are both reviewed after a deletion (PLAN-CHANGE 15 supersedes DEC-010). */
internal fun deletedTagChangeSet(before: List<GarmentTag>, deleted: GarmentTag): TagChangeSet? {
    if (deleted.categoryId !in ReviewedTagKinds) return null
    val after = before.filterNot { tag -> tag.id == deleted.id }
    return tagChangeSet(TagOperation.Deleted, deleted.categoryId, setOf(deleted.id), before, after)
}

/** Only the default tags that are added or renamed by the restore are touched (DEC-010). */
internal fun restoredTagChangeSet(
    before: List<GarmentTag>,
    categoryId: String,
    defaults: List<GarmentTag>,
): TagChangeSet? {
    if (categoryId !in ReviewedTagKinds) return null
    val namesById = before.associate { tag -> tag.id to tag.name }
    val touched = defaults
        .filter { tag -> namesById[tag.id] != tag.name }
        .mapTo(mutableSetOf(), GarmentTag::id)
    if (touched.isEmpty()) return null
    val restoredIds = defaults.mapTo(mutableSetOf(), GarmentTag::id)
    val after = before.filterNot { tag -> tag.id in restoredIds } + defaults
    return tagChangeSet(TagOperation.RestoredDefault, categoryId, touched, before, after)
}

/**
 * Discard = undo for a saved tag change. Restores every tag whose value differs from the one before the change (a rename,
 * a deleted tag, a restored default), removes the tags the change created, and gives back the garment tags a deletion took.
 * A tag that a current garment still references is never removed, archived garments included (the caller passes every garment):
 * a restore that merged a custom row into a built-in one
 * repointed those garments onto the built-in id, and removing it would strip their tags.
 */
internal fun tagReviewUndoPlan(
    request: TagReviewRequest,
    currentGarments: List<ClothingItem>,
    now: Long,
): ReviewUndoPlan {
    val changeSet = request.changeSet
    val beforeIds = changeSet.beforeTags.mapTo(mutableSetOf(), GarmentTag::id)
    val afterById = changeSet.afterTags.associateBy(GarmentTag::id)
    val referencedIds = currentGarments.flatMapTo(mutableSetOf()) { garment -> garment.tags.map(GarmentTag::id) }
    val restoreTags = changeSet.beforeTags.filter { before -> afterById[before.id] != before }
    val removeTags = changeSet.afterTags.filter { after -> after.id !in beforeIds && after.id !in referencedIds }
    val restoreGarments = if (changeSet.operation == TagOperation.Deleted) {
        request.garments.mapNotNull { snapshot ->
            restoredGarmentTags(snapshot, currentGarments, changeSet.touchedTagIds, now)
        }
    } else {
        emptyList()
    }
    return ReviewUndoPlan(restoreTags = restoreTags, removeTags = removeTags, restoreGarments = restoreGarments)
}

/**
 * Compare-and-restore: a garment that lost a deleted tag gets its snapshot tags back only while it still has exactly the
 * tags it kept (the snapshot minus the deleted ones). Any other change since then is left alone.
 */
internal fun restoredGarmentTags(
    snapshot: ClothingItem,
    currentGarments: List<ClothingItem>,
    deletedTagIds: Set<String>,
    now: Long,
): ClothingItem? {
    if (snapshot.tags.none { tag -> tag.id in deletedTagIds }) return null
    val current = currentGarments.firstOrNull { garment -> garment.id == snapshot.id } ?: return null
    val keptIds = snapshot.tags.mapTo(mutableSetOf(), GarmentTag::id) - deletedTagIds
    if (current.tags.mapTo(mutableSetOf(), GarmentTag::id) != keptIds) return null
    return current.copy(tags = snapshot.tags, updatedAtEpochMillis = now)
}

private fun tagChangeSet(
    operation: TagOperation,
    categoryId: String,
    touchedTagIds: Set<String>,
    before: List<GarmentTag>,
    after: List<GarmentTag>,
) = TagChangeSet(
    id = UUID.randomUUID().toString(),
    operation = operation,
    categoryId = categoryId,
    touchedTagIds = touchedTagIds,
    beforeTags = before,
    afterTags = after,
)

/**
 * Shown after a tag change is saved. Not now keeps the change; tapping outside or back cancels it (the caller undoes it);
 * accepting opens the scanning review.
 */
@Composable
internal fun TagReviewPromptDialog(
    modelReady: Boolean,
    onNotNow: () -> Unit,
    onCancel: () -> Unit,
    onReview: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.tag_review_prompt_title)) },
        text = { Text(stringResource(if (modelReady) R.string.tag_review_prompt_body else R.string.tag_review_needs_model)) },
        confirmButton = {
            Button(
                onClick = onReview,
                enabled = modelReady,
            ) {
                Text(stringResource(R.string.tag_review_prompt_confirm))
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.not_now))
            }
        },
    )
}

/**
 * A review finishes by itself once every candidate is closed: accepted, discarded or hidden ([closedIds]). Never for an empty
 * scan, never while the scan runs, a manual-choice picker is open, or the discard dialog is open, and only once per review ([committed]).
 */
internal fun shouldAutoCommit(
    candidateIds: Set<String>,
    closedIds: Set<String>,
    isScanning: Boolean,
    committed: Boolean,
    pickerOpen: Boolean,
    discardDialogOpen: Boolean,
): Boolean =
    !committed && !isScanning && !pickerOpen && !discardDialogOpen &&
        candidateIds.isNotEmpty() && closedIds.containsAll(candidateIds)

/**
 * A tag action waits for the review snapshot of the one before it: while that snapshot is in flight or a prompt is
 * pending, a new action is dropped before it reads or writes, so no snapshot can miss refs an earlier write removed.
 */
internal fun canStartTagReviewAction(offerInFlight: Boolean, promptPending: Boolean): Boolean =
    !offerInFlight && !promptPending

/**
 * Review of a category or occasion change. Garments are scanned incrementally on Dispatchers.Default, so the screen
 * opens at once and proposals appear as they are found. Each decided garment is written as it leaves the list ([onApply]);
 * leaving the screen cancels the scan, keeps every garment already written, and never undoes a write. [onFinish] runs once:
 * when the scan has ended and every found card is decided, or when Done is tapped (cards still Pending are left as they are).
 */
@Composable
internal fun TagReviewScreen(
    innerPadding: PaddingValues,
    request: TagReviewRequest,
    prepareScan: suspend (TagChangeSet, List<ClothingItem>) -> ReviewPlanner.Scan,
    discardDialogOpen: Boolean,
    onApply: suspend (List<TagDelta>) -> Result<List<ApplyResult>>,
    onRevert: suspend (List<AppliedDelta>) -> Result<List<ApplyResult>>,
    onAppliedCountChange: (writtenCount: Int, leaveCount: Int) -> Unit,
    onFinish: (List<ClothingItem>) -> Unit,
    onRequestDiscard: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val applyFailedMessage = stringResource(R.string.review_apply_failed)
    val revertFailedMessage = stringResource(R.string.review_revert_failed)
    val undoLabel = stringResource(R.string.undo)
    val keptMessage = stringResource(R.string.review_snack_kept)
    val gridState = rememberLazyGridState()
    val changeSet = request.changeSet
    val labelsById = remember(changeSet.id) {
        (changeSet.beforeTags + changeSet.afterTags).associate { tag -> tag.id to tag.localizedTagLabel(context) }
    }
    val kindTags = remember(changeSet.id) {
        changeSet.afterTags
            .filter { tag -> tag.categoryId == changeSet.categoryId }
            .sortedAlphabetically(label = { tag -> tagPickerLabel(tag.id, tag.name, labelsById) })
    }
    // Before and after: a deleted tag is still the "current" value of a garment, but it can no longer be picked.
    val kindIds = remember(changeSet.id) {
        (changeSet.beforeTags + changeSet.afterTags)
            .filter { tag -> tag.categoryId == changeSet.categoryId }
            .mapTo(mutableSetOf(), GarmentTag::id)
    }
    // Pinned per change set: a later sync emission neither restarts the scan nor drops the decisions made so far.
    val garments = remember(changeSet.id) { request.garments.filterNot(ClothingItem::isArchived) }
    val afterTagIds = remember(changeSet.id) { changeSet.afterTags.mapTo(mutableSetOf(), GarmentTag::id) }
    val collator = remember(changeSet.id) { browseCollator() }
    val candidates = remember(changeSet.id) { mutableStateListOf<TagReviewCandidate>() }
    // The chip choices and decisions of every card, from the moment its garment is found.
    var session by remember(changeSet.id) { mutableStateOf(TagReviewSession()) }
    // Hidden ids are the cards whose exit has finished, and the garments with nothing to toggle. [returns] counts the failed
    // writes of each card, so a card that comes back gets a new key (see reviewCardKey). [writtenGarments] is what the finish reports.
    val hiddenIds = remember(changeSet.id) { mutableStateListOf<String>() }
    val returns = remember(changeSet.id) { mutableStateMapOf<String, Int>() }
    val writtenGarments = remember(changeSet.id) { mutableStateListOf<ClothingItem>() }
    var scannedCount by remember(changeSet.id) { mutableIntStateOf(0) }
    var skippedCount by remember(changeSet.id) { mutableIntStateOf(0) }
    var isPreparing by remember(changeSet.id) { mutableStateOf(true) }
    var isScanning by remember(changeSet.id) { mutableStateOf(true) }
    var modelUnavailable by remember(changeSet.id) { mutableStateOf(false) }
    var pickerGarmentId by remember(changeSet.id) { mutableStateOf<String?>(null) }
    var finished by remember(changeSet.id) { mutableStateOf(false) }
    // Undo: the snackbar of the latest decision (null: none), the garments whose revert write is in flight, and the Reviewed section.
    var undoSnack by remember(changeSet.id) { mutableStateOf<ReviewUndoSnack?>(null) }
    val revertingIds = remember(changeSet.id) { mutableStateListOf<String>() }
    var reviewedOpen by remember(changeSet.id) { mutableStateOf(false) }

    // Keyed on the change set: a new change cancels the running scan, and only the current change set's results land.
    LaunchedEffect(changeSet.id) {
        candidates.clear()
        session = TagReviewSession()
        scannedCount = 0
        isPreparing = true
        isScanning = true
        modelUnavailable = false
        val scan = try {
            prepareScan(changeSet, garments)
        } catch (unavailable: EmbeddingFailure) {
            modelUnavailable = true
            isPreparing = false
            isScanning = false
            return@LaunchedEffect
        }
        isPreparing = false
        skippedCount = scan.skippedCount
        scan.scanEach(garments) { garment, proposal ->
            if (proposal != null) {
                val live = liveViewOf(garment, changeSet)
                candidates += TagReviewCandidate(garment, proposal, live)
                session = session.withCard(
                    reviewCardStateFor(
                        proposal = proposal,
                        liveGroupTagIds = live.tags.filter { tag -> tag.categoryId == changeSet.categoryId }.mapTo(mutableSetOf(), GarmentTag::id),
                        groupTagIds = kindIds,
                        isCategoryGroup = changeSet.categoryId == TAG_KIND_CATEGORY,
                        existingTagIds = afterTagIds,
                        label = { tagId -> labelsById[tagId] ?: tagId },
                        collator = collator,
                    ),
                )
                // A garment with nothing to toggle (only locked changes) is not offered: it is closed here, and nothing is written.
                if (isHiddenOnScan(session, garment.id)) hiddenIds += garment.id
            }
            scannedCount += 1
        }
        isScanning = false
        Log.d(TAG_REVIEW_SCAN_LOG_TAG, tagReviewScanLogLine(changeSet, candidates.size))
    }

    // The shell's leave question is decided on the applied count, in-flight writes included; its text counts the written ones.
    val writtenCount = writtenGarmentCount(session)
    val leaveCount = session.appliedGarmentCount
    LaunchedEffect(writtenCount, leaveCount) { onAppliedCountChange(writtenCount, leaveCount) }

    // Decide, then write: the card leaves the list at once. The write's result settles the card when it comes back; a failed
    // write puts the card back in the list with the chips it had, and says so in a snackbar.
    // A card that comes back (a failed write, or an undo) is shown again: out of the hidden set, and with a new key.
    fun bringBack(garmentId: String) {
        hiddenIds.remove(garmentId)
        returns[garmentId] = (returns[garmentId] ?: 0) + 1
    }

    // The undo snackbar of a decision: a new decision replaces the one up. A pending card has no decision to show.
    fun showUndoSnack(garmentId: String, next: TagReviewSession) {
        val card = next.cards.firstOrNull { cardState -> cardState.garmentId == garmentId } ?: return
        if (card.decision == ReviewDecision.Pending) return
        undoSnack = ReviewUndoSnack(garmentId, undoSnackOutcome(card), (undoSnack?.serial ?: 0) + 1)
    }

    fun writeDecision(garmentIds: List<String>, delta: TagDelta) {
        scope.launch {
            val outcome = onApply(listOf(delta))
            session = session.afterWrite(garmentIds, outcome)
            val results = outcome.getOrNull()
            if (results != null) {
                results.mapNotNullTo(writtenGarments) { result -> result.item }
                // A write that found nothing to change turns the snackbar into "Kept as it was", and the Undo of it is gone.
                garmentIds.forEach { garmentId -> if (undoSnack?.garmentId == garmentId) showUndoSnack(garmentId, session) }
            } else {
                garmentIds.forEach { garmentId -> bringBack(garmentId) }
                // The undo snackbar of this write says "changes applied", which is wrong now: it goes before the failure shows.
                garmentIds.forEach { garmentId ->
                    if (undoSnack?.garmentId == garmentId) {
                        undoSnack = null
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                }
                snackbarHostState.showSnackbar(applyFailedMessage)
            }
        }
    }

    // Undo and "Review again" are the same step: the latest decision's revert. A card with nothing written (a rejection, Got it)
    // is pending again at once. A written one is pending again when its revert has landed; until then it stays decided and
    // Undo is refused. A failed revert keeps the garment decided and says so.
    fun undoDecision(garmentId: String) {
        if (!canUndoNow(session, garmentId, revertingIds)) return
        val (reverted, delta) = session.revert(garmentId)
        if (delta == null) {
            session = reverted
            bringBack(garmentId)
            return
        }
        revertingIds.add(garmentId)
        scope.launch {
            val outcome = onRevert(listOf(delta))
            revertingIds.remove(garmentId)
            if (revertLanded(outcome)) {
                session = session.revert(garmentId).first
                writtenGarments.removeWritten(garmentId)
                bringBack(garmentId)
            } else {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(revertFailedMessage)
            }
        }
    }

    // One undo snackbar at a time: a new decision restarts this effect, which replaces the one up. Undo acts on its garment.
    LaunchedEffect(undoSnack) {
        val snack = undoSnack ?: return@LaunchedEffect
        val outcome = snack.outcome
        snackbarHostState.currentSnackbarData?.dismiss()
        if (outcome == null) return@LaunchedEffect
        val message = when (outcome) {
            is ReviewOutcome.Changes -> context.resources.getQuantityString(R.plurals.review_snack_applied, outcome.units, outcome.units)
            ReviewOutcome.KeptAsIs -> keptMessage
        }
        val result = snackbarHostState.showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) undoDecision(snack.garmentId)
    }

    // Finish once: the scan has ended, every found card is decided and no write is in flight (see shouldFinishTagReview).
    val finishNow = shouldFinishTagReview(
        session = session,
        isScanning = isScanning,
        pickerOpen = pickerGarmentId != null,
        discardDialogOpen = discardDialogOpen,
        finished = finished,
        revertsInFlight = revertingIds.isNotEmpty(),
    )
    LaunchedEffect(finishNow) {
        if (finishNow) {
            finished = true
            onFinish(writtenGarments.toList())
        }
    }

    val scanProgress = reviewScanProgress(scannedCount, garments.size, isScanning)

    BackHandler { onRequestDiscard() }

    // The chooser offers only the tags the garment does not have and that exist now; the card is never named in its title.
    pickerGarmentId?.let { garmentId ->
        session.cards.firstOrNull { cardState -> cardState.garmentId == garmentId }?.let { card ->
            TagReviewPickerDialog(
                title = tagReviewPickerTitle(
                    noun = stringResource(
                        if (changeSet.categoryId == TAG_KIND_OCCASION) R.string.tag_review_occasion_noun else R.string.tag_review_category_noun,
                    ),
                    garmentName = "",
                    namedTitle = "",
                ),
                options = kindTags.filter { tag -> tag.id in card.offeredTagIds },
                selectedIds = emptySet(),
                multiChoice = false,
                labelsById = labelsById,
                onDismiss = { pickerGarmentId = null },
                onConfirm = { chosen ->
                    session = session.choose(garmentId, chosen.singleOrNull())
                    pickerGarmentId = null
                },
            )
        }
    }

    val headerState = reviewHeaderState(
        isScanning = isScanning,
        isPreparing = isPreparing,
        leftCount = session.leftCount,
        decided = session.reviewed.size,
        found = session.leftCount + session.reviewed.size,
    )

    // The grid index of the "Reviewed" header: the empty or unavailable card (if any), then the pending cards as composed.
    fun reviewedSectionIndex(): Int {
        val leading = if (modelUnavailable || (!isScanning && candidates.isEmpty())) 1 else 0
        return leading + candidates.count { candidate -> candidate.item.id !in hiddenIds }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned above the cards: it does not scroll with them.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (headerState.showsScan) {
                    ReviewProgressHeader(
                        title = stringResource(R.string.tag_review_scan_title),
                        countText = if (isPreparing) {
                            stringResource(R.string.tag_review_preparing)
                        } else {
                            stringResource(R.string.tag_review_scanning_count, scannedCount, garments.size)
                        },
                        progress = if (isPreparing) 0f else scanProgress,
                    )
                } else {
                    ReviewLeftHeader(leftCount = headerState.leftCount, progress = headerState.progress)
                }
                if (session.reviewed.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            reviewedOpen = true
                            scope.launch { gridState.animateScrollToItem(reviewedSectionIndex()) }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(pluralStringResource(R.plurals.review_reviewed_n, session.reviewed.size, session.reviewed.size))
                        Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                }
                TagReviewHeader(
                    changeSet = changeSet,
                    labelsById = labelsById,
                    affectedCount = affectedGarmentCount(changeSet, garments, candidates.size),
                )
                Text(
                    text = stringResource(R.string.review_hint_changes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(reviewColumnCount()),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 128.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (modelUnavailable) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ReviewEmptyCard(
                            title = stringResource(R.string.tag_review_needs_model),
                            body = stringResource(R.string.tag_review_needs_model_body),
                        )
                    }
                } else if (!isScanning && candidates.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ReviewEmptyCard(
                            title = stringResource(R.string.tag_review_empty_title),
                            body = stringResource(R.string.tag_review_empty_body),
                        )
                    }
                }

                items(
                    items = candidates.filterNot { candidate -> candidate.item.id in hiddenIds },
                    key = { candidate -> reviewCardKey(candidate.item.id, returns[candidate.item.id] ?: 0) },
                ) { candidate ->
                    val garmentId = candidate.item.id
                    val card = session.cards.firstOrNull { cardState -> cardState.garmentId == garmentId }
                    val chosen = card?.let { cardState -> chosenTagOf(cardState, afterTagIds) }
                    // A decided card (applied, written, kept or rejected) leaves the list; a write that fails brings it back.
                    val resolved = card == null || card.decision !is ReviewDecision.Pending
                    ReviewAcceptedCard(
                        resolved = resolved,
                        onGone = { hiddenIds.add(garmentId) },
                        direction = if (card?.decision is ReviewDecision.Rejected) ExitDirection.Discard else ExitDirection.Accept,
                    ) {
                        if (card != null) {
                            val groupLabels = candidate.live.tags
                                .filter { tag -> tag.categoryId == changeSet.categoryId }
                                .map { tag -> tag.localizedTagLabel(context) }
                                .sortedAlphabetically(label = { label -> label })
                            TagReviewCard(
                                card = card,
                                identity = tagReviewIdentityLine(candidate.live),
                                nowLine = if (changeSet.categoryId == TAG_KIND_OCCASION) {
                                    stringResource(
                                        R.string.review_now,
                                        nowLine(groupLabels, stringResource(R.string.review_none)),
                                    )
                                } else {
                                    null
                                },
                                chosenTagId = chosen,
                                noTagsLeft = noTagsLeft(
                                    isOccasionGroup = changeSet.categoryId == TAG_KIND_OCCASION,
                                    beforeGroupIds = card.liveGroupTagIds,
                                    changes = card.changes,
                                    skipped = card.skipped,
                                    chosenTagId = chosen,
                                ),
                                labelOf = { tagId -> labelsById[tagId] ?: tagId },
                                resolved = resolved,
                                photo = {
                                    TagReviewPhoto(
                                        item = candidate.item,
                                        swatch = candidate.item.colorMetrics.primaryDisplayLabel ?: DisplayColorLabel.Unknown,
                                    )
                                },
                                onToggle = { key -> session = session.toggle(garmentId, key) },
                                onChoose = { pickerGarmentId = garmentId },
                                onApply = {
                                    if (canDecideNow(card, chosen)) {
                                        val (next, delta) = session.apply(garmentId, afterTagIds)
                                        session = next
                                        showUndoSnack(garmentId, next)
                                        if (delta != null) writeDecision(listOf(garmentId), delta)
                                    }
                                },
                                onReject = {
                                    session = session.reject(garmentId)
                                    showUndoSnack(garmentId, session)
                                },
                            )
                        }
                    }
                }

                // The decided garments, collapsed by default, most recent first, after the last pending card.
                if (session.reviewed.isNotEmpty()) {
                    item(key = "reviewed-header", span = { GridItemSpan(maxLineSpan) }) {
                        ReviewedSectionHeader(
                            count = session.reviewed.size,
                            expanded = reviewedOpen,
                            onToggle = { reviewedOpen = !reviewedOpen },
                        )
                    }
                    if (reviewedOpen) {
                        items(
                            items = session.reviewed,
                            key = { card -> "reviewed-${card.garmentId}" },
                            span = { GridItemSpan(maxLineSpan) },
                        ) { card ->
                            candidates.firstOrNull { candidate -> candidate.item.id == card.garmentId }?.let { candidate ->
                                ReviewedRow(
                                    candidate = candidate,
                                    outcome = reviewOutcomeOf(card),
                                    enabled = canUndoNow(session, card.garmentId, revertingIds),
                                    onReviewAgain = { undoDecision(card.garmentId) },
                                )
                            }
                        }
                    }
                }

                if (isScanning || (!modelUnavailable && skippedCount > 0)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ReviewHelperRow(
                            isScanning = isScanning,
                            text = if (isScanning) {
                                stringResource(R.string.tag_review_checking_helper)
                            } else {
                                stringResource(R.string.tag_review_skipped, skippedCount)
                            },
                        )
                    }
                }
            }
        }

        // Done ends the review (the same finish as the automatic one). Back, the top bar and the system back still ask Discard or Leave.
        // Not while a revert is in flight: its garment would be counted as updated after the finish.
        ReviewBottomBar(
            label = stringResource(R.string.done),
            enabled = doneEnabled(isScanning, session) && revertingIds.isEmpty(),
            onClick = {
                if (revertingIds.isEmpty() && doneFinishes(finished, session)) {
                    finished = true
                    onFinish(writtenGarments.toList())
                }
            },
        )
        // Above the bottom bar (about 104 dp tall): a failed save or undo says so here, and the undo snackbar offers Undo.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 112.dp),
        ) { data ->
            val actionLabel = data.visuals.actionLabel
            val undoEnabled = undoSnack?.let { snack -> canUndoNow(session, snack.garmentId, revertingIds) } == true
            Snackbar(
                action = {
                    if (actionLabel != null) {
                        TextButton(onClick = data::performAction, enabled = undoEnabled) {
                            Text(actionLabel)
                        }
                    }
                },
            ) {
                Text(data.visuals.message)
            }
        }
    }
}

/** The collapsed "Reviewed · N" row after the pending cards, with Show or Hide. */
@Composable
private fun ReviewedSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.review_section_reviewed, count),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (expanded) R.string.review_hide else R.string.review_show))
        }
    }
}

/** One decided garment: its thumbnail, identity, outcome, and "Review again" (the same step as Undo). */
@Composable
private fun ReviewedRow(
    candidate: TagReviewCandidate,
    outcome: ReviewOutcome,
    enabled: Boolean,
    onReviewAgain: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(width = 36.dp, height = 42.dp)) {
            TagReviewPhoto(
                item = candidate.item,
                swatch = candidate.item.colorMetrics.primaryDisplayLabel ?: DisplayColorLabel.Unknown,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tagReviewIdentityLine(candidate.live),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (outcome) {
                    is ReviewOutcome.Changes -> pluralStringResource(R.plurals.review_snack_applied, outcome.units, outcome.units)
                    ReviewOutcome.KeptAsIs -> stringResource(R.string.review_snack_kept)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onReviewAgain, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.review_again))
        }
    }
}

@Composable
private fun TagReviewHeader(
    changeSet: TagChangeSet,
    labelsById: Map<String, String>,
    affectedCount: Int,
) {
    val context = LocalContext.current
    val noun = stringResource(
        if (changeSet.categoryId == TAG_KIND_OCCASION) R.string.tag_review_occasion_noun else R.string.tag_review_category_noun,
    )
    val touchedNames = changeSet.touchedTagIds.mapNotNull(labelsById::get).joinToString()
    val isDeleted = changeSet.operation == TagOperation.Deleted

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (isDeleted) {
                stringResource(R.string.tag_review_deleted_header, noun, touchedNames)
            } else {
                stringResource(R.string.tag_review_added_header, noun, touchedNames)
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = when {
                !isDeleted -> context.resources.getQuantityString(R.plurals.tag_review_added_body, affectedCount, affectedCount, touchedNames)
                changeSet.categoryId == TAG_KIND_OCCASION -> context.resources.getQuantityString(R.plurals.tag_review_deleted_occasion_body, affectedCount, affectedCount)
                else -> context.resources.getQuantityString(R.plurals.tag_review_deleted_body, affectedCount, affectedCount)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TagReviewPhoto(item: ClothingItem, swatch: DisplayColorLabel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(rememberGarmentSwatchBrush(swatch.swatchColor())),
        contentAlignment = Alignment.Center,
    ) {
        val photoUri = item.photoUri?.takeIf(String::isNotBlank)
        if (photoUri != null) {
            BoundedGarmentImage(
                photoUri = photoUri,
                thumbnailMaxEdgePx = TAG_REVIEW_THUMBNAIL_MAX_EDGE_PX,
                purpose = ImagePurpose.Browse,
                sourceRevision = item.id,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.Style,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

/** Single choice for categories (the "no category" row clears it); multi choice with "None" for occasions. */
@Composable
private fun TagReviewPickerDialog(
    title: String,
    options: List<GarmentTag>,
    selectedIds: Set<String>,
    multiChoice: Boolean,
    labelsById: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selection by remember(selectedIds) { mutableStateOf(selectedIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // Scrolls: a long category list must stay reachable on a small screen, the dialog itself does not scroll.
            ReviewPickerList(optionCount = options.size) {
                TagReviewPickerRow(
                    label = stringResource(if (multiChoice) R.string.tag_review_none_occasion else R.string.tag_review_none),
                    selected = selection.isEmpty(),
                    multiChoice = multiChoice,
                    onClick = {
                        if (multiChoice) selection = emptySet() else onConfirm(emptySet())
                    },
                )
                options.forEach { tag ->
                    val isSelected = tag.id in selection
                    TagReviewPickerRow(
                        label = tagPickerLabel(tag.id, tag.name, labelsById),
                        selected = isSelected,
                        multiChoice = multiChoice,
                        onClick = {
                            if (multiChoice) {
                                selection = if (isSelected) selection - tag.id else selection + tag.id
                            } else {
                                onConfirm(setOf(tag.id))
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            if (multiChoice) {
                Button(onClick = { onConfirm(selection) }) {
                    Text(stringResource(R.string.done))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun TagReviewPickerRow(
    label: String,
    selected: Boolean,
    multiChoice: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiChoice) {
            Checkbox(checked = selected, onCheckedChange = null)
        } else {
            RadioButton(selected = selected, onClick = null)
        }
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
    }
}
