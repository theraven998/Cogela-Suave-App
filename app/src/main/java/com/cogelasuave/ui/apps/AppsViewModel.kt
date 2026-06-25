package com.cogelasuave.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cogelasuave.domain.model.AppInfo
import com.cogelasuave.domain.usecase.ObserveInstalledAppsUseCase
import com.cogelasuave.domain.usecase.SetAppCustomWaitUseCase
import com.cogelasuave.domain.usecase.SetAppWatchedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppsUiState(
    val query: String = "",
    val apps: List<AppInfo> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    observeInstalledApps: ObserveInstalledAppsUseCase,
    private val setAppWatched: SetAppWatchedUseCase,
    private val setAppCustomWait: SetAppCustomWaitUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<AppsUiState> =
        combine(observeInstalledApps(), query) { apps, q ->
            val filtered =
                if (q.isBlank()) apps
                else apps.filter { it.label.contains(q, ignoreCase = true) }
            AppsUiState(query = q, apps = filtered, loading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppsUiState(),
        )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onWatchedChange(app: AppInfo, watched: Boolean) {
        viewModelScope.launch {
            setAppWatched(app.packageName, app.label, watched)
        }
    }

    fun onCustomWaitChange(app: AppInfo, seconds: Int?) {
        viewModelScope.launch {
            setAppCustomWait(app.packageName, seconds)
        }
    }
}
