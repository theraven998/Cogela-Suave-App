package com.cogelasuave.domain.model

/**
 * A launchable application installed on the device, joined with whether the user
 * has chosen to watch it and any per-app wait override.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val isWatched: Boolean,
    /** Per-app wait override in seconds, or null to use the global value. */
    val customWaitSeconds: Int?,
)
