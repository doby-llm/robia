package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.suggestions.DuplicateCheckStatus

/** One line of the "Check for duplicates" row: a string with its format arguments, or a plural counted by [count]. */
sealed interface DuplicateCheckLine {
    data class Plain(val id: Int, val args: List<Any> = emptyList()) : DuplicateCheckLine
    data class Counted(val id: Int, val count: Int) : DuplicateCheckLine
}

/** The row's lines: the summary before any check, the progress while one runs, then the result (R6-DEC-007). */
internal fun duplicateCheckLines(status: DuplicateCheckStatus): List<DuplicateCheckLine> = when (status) {
    DuplicateCheckStatus.Idle -> listOf(DuplicateCheckLine.Plain(R.string.duplicate_check_summary))
    is DuplicateCheckStatus.Running ->
        listOf(DuplicateCheckLine.Plain(R.string.duplicate_check_running, listOf(status.done, status.total)))
    DuplicateCheckStatus.Failed -> listOf(DuplicateCheckLine.Plain(R.string.duplicate_check_failed))
    is DuplicateCheckStatus.Done -> buildList {
        if (status.groupCount == 0) add(DuplicateCheckLine.Plain(R.string.duplicate_check_none))
        // Garments not compared are said in the result line, whatever the groups (R6-DEC-007).
        if (status.notCompared > 0) add(DuplicateCheckLine.Counted(R.plurals.duplicate_check_not_ready, status.notCompared))
        if (isEmpty()) add(DuplicateCheckLine.Plain(R.string.duplicate_check_summary))
    }
}

/** A tap starts a scan. While one runs the row only shows its progress. */
internal fun duplicateCheckTapEnabled(status: DuplicateCheckStatus): Boolean = status !is DuplicateCheckStatus.Running

/** Groups found while the user is still on Settings open "Possible duplicates". With none, nothing opens: no popup, no snackbar. */
internal fun duplicateCheckOpensReview(status: DuplicateCheckStatus, onSettings: Boolean): Boolean =
    status is DuplicateCheckStatus.Done && status.groupCount > 0 && onSettings
