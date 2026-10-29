package com.gusanitolabs.robia.ui

/**
 * Holds the last exit hint and the context it was shown in, around the pure [decideBack] table, so the
 * wiring in `RobiaApp` can be tested with a fake clock. Pure Kotlin: no Android types.
 *
 * A stored hint is forgotten when the context key differs (route, query, similar-to, filters, selection) or when
 * it is later than the clock (the clock went backwards). Every action other than [BackAction.ShowExitHint]
 * forgets the hint too.
 */
class BackExitMachine(private val clock: () -> Long) {
    private var hintMs: Long? = null
    private var hintContext: Any? = null

    fun onBack(state: BackState, contextKey: Any): BackAction {
        val now = clock()
        val action = decideBack(state.copy(lastExitHintMs = liveHintMs(contextKey, now)), now)
        if (action == BackAction.ShowExitHint) {
            hintMs = now
            hintContext = contextKey
        } else {
            forget()
        }
        return action
    }

    /** True while the hint is stored and still inside the exit window. Drives the in-app pill. */
    fun hintVisible(): Boolean {
        val hint = hintMs ?: return false
        return clock() - hint in 0L..EXIT_WINDOW_MILLIS
    }

    /** Milliseconds since the hint that a press in [contextKey] would still see, or null. For the log only. */
    fun liveHintAgeMs(contextKey: Any): Long? {
        val now = clock()
        return liveHintMs(contextKey, now)?.let { now - it }
    }

    private fun liveHintMs(contextKey: Any, now: Long): Long? {
        val hint = hintMs ?: return null
        return if (hintContext == contextKey && hint <= now) hint else null
    }

    private fun forget() {
        hintMs = null
        hintContext = null
    }
}
