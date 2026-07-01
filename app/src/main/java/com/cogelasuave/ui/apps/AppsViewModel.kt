package com.cogelasuave.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cogelasuave.domain.model.AppInfo
import com.cogelasuave.domain.usecase.ObserveInstalledAppsUseCase
import com.cogelasuave.domain.usecase.SetAppCustomWaitUseCase
import com.cogelasuave.domain.usecase.SetAppWatchedUseCase
import com.cogelasuave.service.SnoozeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppsUiState(
    val query: String = "",
    val apps: List<AppInfo> = emptyList(),
    val loading: Boolean = true,
    /** When true the watch list is locked: apps can't be un-watched or weakened. */
    val strictMode: Boolean = false,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    observeInstalledApps: ObserveInstalledAppsUseCase,
    private val setAppWatched: SetAppWatchedUseCase,
    private val setAppCustomWait: SetAppCustomWaitUseCase,
    private val snoozeManager: SnoozeManager,
) : ViewModel() {

    private val query = MutableStateFlow("")

    // SnoozeManager is SharedPreferences-backed (no stream), so poll the strict
    // flag once a second to keep the list lock state fresh.
    private val strictMode = flow {
        while (true) {
            emit(snoozeManager.isStrictMode)
            delay(1_000L)
        }
    }

    val uiState: StateFlow<AppsUiState> =
        combine(observeInstalledApps(), query, strictMode) { apps, q, strict ->
            val filtered =
                if (q.isBlank()) apps
                else apps.filter { it.label.contains(q, ignoreCase = true) }
            AppsUiState(query = q, apps = filtered, loading = false, strictMode = strict)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppsUiState(),
        )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onWatchedChange(app: AppInfo, watched: Boolean) {
        // Strict lock: forbid un-watching. Adding new watched apps is still fine.
        if (snoozeManager.isStrictMode && !watched) return
        viewModelScope.launch {
            setAppWatched(app.packageName, app.label, watched)
        }
    }

    fun onCustomWaitChange(app: AppInfo, seconds: Int?) {
        // Strict lock: forbid changing the per-app wait (would weaken the pause).
        if (snoozeManager.isStrictMode) return
        viewModelScope.launch {
            setAppCustomWait(app.packageName, seconds)
        }
    }
}
