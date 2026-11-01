package com.gusanitolabs.robia.ui

// Public privacy policy (R4-DEC-013). The text lives in doby-llm/robia-privacy-policy; the app only links to it.
const val PRIVACY_POLICY_URL = "https://github.com/doby-llm/robia-privacy-policy/blob/main/README.md"

// Developer mode (benchmarks, debug dumps) is for debug and staging builds; release never unlocks or shows it.
fun developerModeAllowed(buildType: String): Boolean = buildType != "release"
