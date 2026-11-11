package com.gusanitolabs.robia.ui

// Public privacy policy (R4-DEC-013). The text lives in doby-llm/robia-privacy-policy; the app only links to it.
const val PRIVACY_POLICY_URL = "https://github.com/doby-llm/robia-privacy-policy/blob/main/README.md"

// Developer mode unlocks the same way in every build, release included (R6-DEC-006).
// Debug-only code (performance fixtures, cutout logs) stays behind BuildConfig.DEBUG.
fun developerModeAllowed(buildType: String): Boolean = true
