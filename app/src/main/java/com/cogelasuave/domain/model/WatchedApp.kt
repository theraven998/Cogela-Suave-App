package com.cogelasuave.domain.model

/** An app the user has actively chosen to intercept. */
data class WatchedApp(
    val packageName: String,
    val label: String,
    val customWaitSeconds: Int?,
)
