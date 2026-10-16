package com.gusanitolabs.robia.ui

/** First-run prompts. At most one is on screen; the order is the order the user should see them. */
enum class FirstRunPrompt { CloudSetup, MergeConfirmation, BackupConflict, ModelDownload }

/** Each flag says whether that prompt still needs an answer from the user. */
data class FirstRunPromptInputs(
    val settingsLoaded: Boolean,
    val cloudAuthorizationInFlight: Boolean,
    val anotherDialogOpen: Boolean,
    val cloudSetupPending: Boolean,
    val mergeConfirmationPending: Boolean,
    val backupConflictPending: Boolean,
    val modelDownloadPending: Boolean,
)

/**
 * The prompt to show next, or null while it must wait. Nothing shows before settings load, while the Google
 * account chooser is open (the model prompt used to appear behind it), or while another dialog is open.
 */
fun nextFirstRunPrompt(inputs: FirstRunPromptInputs): FirstRunPrompt? {
    if (!inputs.settingsLoaded || inputs.cloudAuthorizationInFlight || inputs.anotherDialogOpen) return null
    return FirstRunPrompt.values().firstOrNull(inputs::isPending)
}

fun FirstRunPromptInputs.isPending(prompt: FirstRunPrompt): Boolean = when (prompt) {
    FirstRunPrompt.CloudSetup -> cloudSetupPending
    FirstRunPrompt.MergeConfirmation -> mergeConfirmationPending
    FirstRunPrompt.BackupConflict -> backupConflictPending
    FirstRunPrompt.ModelDownload -> modelDownloadPending
}

internal const val FIRST_RUN_QUIET_MS = 3_000L

/** Whether [nowMs] is at least [quietMs] after the last busy transition; no transition yet counts as quiet. */
internal fun quietEnoughSince(lastBusyAtMs: Long?, nowMs: Long, quietMs: Long = FIRST_RUN_QUIET_MS): Boolean =
    lastBusyAtMs == null || nowMs - lastBusyAtMs >= quietMs

/**
 * Inputs for the model prompt only. Cloud, merge and conflict prompts keep their own order in [nextFirstRunPrompt].
 * [syncRunning] covers a running Drive restore too; [cloudImportArmed] covers the window before the import starts.
 */
data class ModelPromptGateInputs(
    val onBrowse: Boolean,
    val syncRunning: Boolean,
    val cloudImportArmed: Boolean,
    val cloudAuthorizationInFlight: Boolean,
    /** Browse dialogs and review offers, merged by the caller: any of them open means the prompt waits. */
    val browseDialogOpen: Boolean,
    val mergeCombining: Boolean,
    val mergeOrConflictPending: Boolean,
    /** An empty wardrobe with a Drive connection whose fresh-install restore has not been attempted yet. */
    val emptyWardrobeRestorePending: Boolean,
    val quietEnough: Boolean,
)

/** The model prompt may show only on Browse, at rest, after a calm period, with no other prompt or review waiting. */
fun firstRunGateOpen(inputs: ModelPromptGateInputs): Boolean =
    inputs.onBrowse &&
        !inputs.syncRunning &&
        !inputs.cloudImportArmed &&
        !inputs.cloudAuthorizationInFlight &&
        !inputs.browseDialogOpen &&
        !inputs.mergeCombining &&
        !inputs.mergeOrConflictPending &&
        !inputs.emptyWardrobeRestorePending &&
        inputs.quietEnough
