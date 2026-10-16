package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot

/**
 * What a cycle does with a remote backup that holds user data (PLAN-CHANGE 19, K01). The merge dialog is the last
 * resort: it appears only when both sides hold user data and diverge.
 */
internal enum class MergeDecision {
    /** Ordinary upload of the phone's snapshot. The caller uploads; the rules below decide when that is the outcome. */
    UploadLocal,

    /** Ordinary cycle with nothing pending: Drive is not newer than this phone, so there is nothing to write. */
    NoOp,

    /** The phone held no user data when the cycle started: the backup is taken silently. */
    RestoreSilently,

    /** Both sides already hold the same data: remember Drive's revision as the base, no write, no dialog. */
    RecordBase,

    /** Nothing pending and the phone's data is already inside the backup: the backup is taken silently. */
    FastForward,

    /** Both sides hold user data and diverge: the user chooses. */
    Propose,
}

/**
 * Pure decision, evaluated in this order; the first rule that matches wins.
 *
 * 1. The backup has no user data: upload the phone's snapshot.
 * 2. The phone has data and either this phone wrote the backup, or the backup is exactly the revision this phone last
 *    recorded (the base) and, when the base's writer is known, was written by that same writer. An ordinary cycle: it
 *    uploads only if local work is pending. A lower revision is not own lineage: phone clocks skew, so a lower revision may
 *    still be another writer's newer data. Two writers can share a revision under skew, so the writer is checked too.
 *    A base with an unknown writer (recorded before the writer was stored) keeps the revision-only check: a known residual.
 * 3. The phone was empty when the cycle started: take the backup silently. Code checks this before rule 2.
 * 4. Both sides already hold the same data, with the same wardrobe and schema: record the base, uploading only if local work
 *    is pending.
 * 5. Nothing pending and the phone's data is contained in the backup, with the same wardrobe and schema: take the backup silently.
 * 6. Otherwise both sides hold data and diverge: propose.
 *
 * [pendingLocalWork] is true when any synced row has not reached Drive in any state (see WardrobeDao.hasAnyUnsyncedLocalRow),
 * so a silent take-over cannot drop an unsynced edit. The taking-over outcomes are restores, which merge the phone with the
 * backup before they write.
 *
 * Rule 3 is checked before rule 2 on purpose: an empty phone is restored even when the backup's writer is this device.
 * Pending work is never considered by rule 3: a phone with no user data has nothing that a restore could drop.
 */
internal fun decideMerge(
    local: WardrobeSyncSnapshot,
    remote: WardrobeSyncSnapshot,
    lastKnownRemoteRevision: Long,
    lastKnownRemoteWriter: String?,
    deviceId: String,
    pendingLocalWork: Boolean,
    localWasEmptyAtStart: Boolean,
): MergeDecision {
    if (!remote.hasUserData()) return MergeDecision.UploadLocal
    val baseKnown = lastKnownRemoteRevision > 0L
    // Exactly the base, from the base's writer, is this phone's own state. Any other revision is not: clocks skew, and a legacy
    // backup has no writer. The writer is not required alone: the writer-only rule would send photo-bearing restores to a proposal.
    val isBase = baseKnown && remote.metadata.revision == lastKnownRemoteRevision &&
        (lastKnownRemoteWriter == null || remote.metadata.writerDeviceId == lastKnownRemoteWriter)
    val ownLineage = remote.metadata.writerDeviceId == deviceId || isBase
    // A different wardrobe or schema is never "the same data", whatever the garments look like.
    val sameWardrobe = local.metadata.schemaVersion == remote.metadata.schemaVersion &&
        local.metadata.wardrobeId == remote.metadata.wardrobeId
    if (localWasEmptyAtStart) return MergeDecision.RestoreSilently
    if (ownLineage) return if (pendingLocalWork) MergeDecision.UploadLocal else MergeDecision.NoOp
    if (sameWardrobe && local.equivalentForUpload(remote)) return if (pendingLocalWork) MergeDecision.UploadLocal else MergeDecision.RecordBase
    if (sameWardrobe && !pendingLocalWork && mergeSnapshots(local, remote).equivalentForUpload(remote)) return MergeDecision.FastForward
    return MergeDecision.Propose
}
