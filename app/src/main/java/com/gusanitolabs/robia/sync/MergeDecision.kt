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
 * 2. The phone has data and either this phone wrote the backup, or the backup is not newer than the revision this phone
 *    last recorded (the base): an ordinary cycle. It uploads only if local work is pending.
 * 3. The phone was empty when the cycle started: take the backup silently.
 * 4. Both sides already hold the same data: record the base, uploading only if local work is pending.
 * 5. Nothing pending and the phone's data is contained in the backup: take the backup silently.
 * 6. Otherwise both sides hold data and diverge: propose.
 *
 * Pending local work never reaches rule 5, so a silent take-over cannot drop an unsynced edit. The taking-over outcomes
 * are restores, which merge the phone with the backup before they write.
 */
internal fun decideMerge(
    local: WardrobeSyncSnapshot,
    remote: WardrobeSyncSnapshot,
    lastKnownRemoteRevision: Long,
    deviceId: String,
    pendingLocalWork: Boolean,
    localWasEmptyAtStart: Boolean,
): MergeDecision {
    if (!remote.hasUserData()) return MergeDecision.UploadLocal
    val baseKnown = lastKnownRemoteRevision > 0L
    val ownLineage = remote.metadata.writerDeviceId == deviceId || (baseKnown && remote.metadata.revision <= lastKnownRemoteRevision)
    if (localWasEmptyAtStart) return MergeDecision.RestoreSilently
    if (ownLineage) return if (pendingLocalWork) MergeDecision.UploadLocal else MergeDecision.NoOp
    if (local.equivalentForUpload(remote)) return if (pendingLocalWork) MergeDecision.UploadLocal else MergeDecision.RecordBase
    if (!pendingLocalWork && mergeSnapshots(local, remote).equivalentForUpload(remote)) return MergeDecision.FastForward
    return MergeDecision.Propose
}
