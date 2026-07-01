package com.cogelasuave.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cogelasuave.data.backup.BackupManager
import com.cogelasuave.domain.model.AppSettings
import com.cogelasuave.domain.usecase.ObserveSettingsUseCase
import com.cogelasuave.domain.usecase.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = observeSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    /** One-shot user-facing results (export/import), drained by the UI as toasts. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun onGlobalWaitChange(seconds: Int) {
        viewModelScope.launch { updateSettings.setGlobalWaitSeconds(seconds) }
    }

    fun onEstimatedSessionChange(minutes: Int) {
        viewModelScope.launch { updateSettings.setEstimatedSessionMinutes(minutes) }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { backupManager.export(it) }
                    ?: error("stream null")
            }.isSuccess
            _messages.emit(if (ok) "Copia de seguridad guardada." else "No se pudo guardar la copia.")
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.use { backupManager.import(it) }
                    ?: error("stream null")
            }
            _messages.emit(
                result.fold(
                    onSuccess = {
                        "Datos restaurados: ${it.watchedApps} apps, " +
                            "${it.dailyStats + it.reasonStats} registros."
                    },
                    onFailure = { "No se pudo leer la copia (archivo inválido)." },
                ),
            )
        }
    }
}
