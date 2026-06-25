package com.cogelasuave.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cogelasuave.R

/**
 * Quick Settings tile to toggle the "watch snooze" (interception pause) without
 * opening the app.
 *
 * Behaviour:
 *  - Tile OFF (no active snooze): tapping starts a snooze for [DEFAULT_SNOOZE_MINUTES].
 *  - Tile ON (snooze active): tapping cancels it immediately.
 *  - Strict mode ON: the tile is shown UNAVAILABLE and tapping does nothing, so
 *    the user cannot bypass their own pause from the shade.
 *
 * State lives in [SnoozeManager] (SharedPreferences). `TileService` is a plain
 * Android service that Hilt does not inject, which is exactly why [SnoozeManager]
 * is deliberately Hilt-free and Context-constructible.
 */
class SnoozeTileService : TileService() {

    private val snoozeManager: SnoozeManager by lazy { SnoozeManager(this) }

    /** Called every time the tile becomes visible; refresh from current state. */
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        when {
            // Strict mode: refuse and just re-render the (unavailable) tile.
            snoozeManager.isStrictMode -> Unit
            // A snooze is running -> tapping cancels it.
            snoozeManager.isSnoozeActive() -> snoozeManager.clearSnooze()
            // Otherwise start a default-length snooze.
            else -> snoozeManager.snoozeFor(DEFAULT_SNOOZE_MINUTES)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return

        val strict = snoozeManager.isStrictMode
        val active = snoozeManager.isSnoozeActive()

        tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
        tile.label = getString(R.string.tile_snooze_label)

        when {
            strict -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitleCompat(getString(R.string.tile_snooze_strict))
            }
            active -> {
                tile.state = Tile.STATE_ACTIVE
                val minsLeft = (snoozeManager.remainingMillis() / 60_000L).toInt() + 1
                tile.subtitleCompat(getString(R.string.tile_snooze_active, minsLeft))
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitleCompat(getString(R.string.tile_snooze_inactive))
            }
        }
        tile.updateTile()
    }

    /** Subtitles are only honoured on Android 10+ (API 29); guard the call. */
    private fun Tile.subtitleCompat(text: CharSequence) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            subtitle = text
        }
    }

    private companion object {
        const val DEFAULT_SNOOZE_MINUTES = 15
    }
}
