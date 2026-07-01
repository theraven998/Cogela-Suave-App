package com.cogelasuave.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cogelasuave.service.SnoozeManager
import com.cogelasuave.service.StrictModeSecurity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Immutable snapshot of the snooze/strict-mode state for the UI to render. */
data class SnoozeUiState(
    val snoozeActive: Boolean = false,
    val remainingMinutes: Int = 0,
    val strictMode: Boolean = false,
    val strictRemainingMillis: Long = 0L,
    val options: List<Int> = SnoozeManager.SNOOZE_OPTIONS_MINUTES,
    val strictOptions: List<Int> = SnoozeManager.STRICT_OPTIONS_MINUTES,
)

/**
 * Plain [ViewModel] (no Hilt) that bridges the Compose settings UI to
 * [SnoozeManager]. We avoid Hilt here on purpose to keep [SnoozeManager]
 * Hilt-free and shared with the service/tile; the [Factory] below builds it from
 * the application [Context].
 *
 * Since [SnoozeManager] is backed by SharedPreferences (no observable stream),
 * the ViewModel polls once per second while alive to keep the remaining-time
 * label fresh and to flip the UI when a snooze expires.
 */
class SnoozeViewModel(context: Context) : ViewModel() {

    private val snoozeManager = SnoozeManager(context.applicationContext)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<SnoozeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _state.value = readState()
                delay(1_000L)
            }
        }
    }

    fun onSnoozeSelected(minutes: Int) {
        snoozeManager.snoozeFor(minutes)
        _state.value = readState()
    }

    fun onCancelSnooze() {
        snoozeManager.clearSnooze()
        _state.value = readState()
    }

    /**
     * Enables the strict lock for [durationMinutes] and returns the freshly
     * generated master password (plaintext) so the UI can show it once for the
     * user to write down. Only the hash is persisted.
     */
    fun onEnableStrict(durationMinutes: Int): String {
        val password = StrictModeSecurity.generatePassword()
        snoozeManager.enableStrictMode(durationMinutes, StrictModeSecurity.hash(password))
        _state.value = readState()
        return password
    }

    /** Tries to release the strict lock with [password]; true if it unlocked. */
    fun onTryDisableStrict(password: String): Boolean {
        val ok = snoozeManager.tryDisableStrictMode(password)
        _state.value = readState()
        return ok
    }

    private fun readState(): SnoozeUiState {
        val active = snoozeManager.isSnoozeActive()
        // Round up so "59s left" still reads as "1 min".
        val minsLeft = if (active) (snoozeManager.remainingMillis() / 60_000L).toInt() + 1 else 0
        return SnoozeUiState(
            snoozeActive = active,
            remainingMinutes = minsLeft,
            strictMode = snoozeManager.isStrictMode,
            strictRemainingMillis = snoozeManager.strictRemainingMillis(),
        )
    }

    /** Builds a [SnoozeViewModel] without Hilt, capturing the application context. */
    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SnoozeViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return SnoozeViewModel(appContext) as T
        }
    }
}
