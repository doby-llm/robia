package com.gusanitolabs.robia.core.model

enum class DriveSyncConnectionStatus {
    Disabled,
    NotConfigured,
    Disconnected,
    Connected,
    Syncing,
    NeedsAttention,
}

enum class DriveSyncDisabledReason {
    GoogleCloudSetupRequired,
    OAuthClientMissing,
    UserNotConnected,
}
