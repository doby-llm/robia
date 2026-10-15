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
