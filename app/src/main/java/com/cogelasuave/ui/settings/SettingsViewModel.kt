package com.cogelasuave.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cogelasuave.domain.model.AppSettings
import com.cogelasuave.domain.usecase.ObserveSettingsUseCase
import com.cogelasuave.domain.usecase.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = observeSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun onGlobalWaitChange(seconds: Int) {
        viewModelScope.launch { updateSettings.setGlobalWaitSeconds(seconds) }
    }

    fun onEstimatedSessionChange(minutes: Int) {
        viewModelScope.launch { updateSettings.setEstimatedSessionMinutes(minutes) }
    }
}
