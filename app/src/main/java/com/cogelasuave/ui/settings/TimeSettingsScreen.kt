package com.cogelasuave.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cogelasuave.service.CogelaSuaveAdminReceiver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cogelasuave.service.StrictModeSecurity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cogelasuave.R
import com.cogelasuave.data.backup.BackupManager
import com.cogelasuave.domain.model.AppSettings

@Composable
fun TimeSettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Snooze/strict-mode state lives in its own (Hilt-free) ViewModel backed by
    // SnoozeManager/SharedPreferences, so SettingsViewModel stays untouched.
    val context = LocalContext.current
    val snoozeViewModel: SnoozeViewModel =
        viewModel(factory = SnoozeViewModel.Factory(context))
    val snoozeState by snoozeViewModel.state.collectAsStateWithLifecycle()

    // Export/import via the Storage Access Framework, so the file lives outside
    // app storage and survives reinstalls / destructive DB migrations.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE),
    ) { uri -> uri?.let(viewModel::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFrom) }

    // Surface export/import results as toasts.
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingCard(
            title = "Tiempo de espera global",
            value = "${settings.globalWaitSeconds} s",
            description = "Pausa obligatoria antes de poder abrir una app vigilada. " +
                "Puedes anularla por app en la pestaña Apps.",
        ) {
            Slider(
                value = settings.globalWaitSeconds.toFloat(),
                onValueChange = { viewModel.onGlobalWaitChange(it.toInt()) },
                valueRange = AppSettings.MIN_WAIT_SECONDS.toFloat()..AppSettings.MAX_WAIT_SECONDS.toFloat(),
            )
        }

        SettingCard(
            title = "Duración estimada de una sesión",
            value = "${settings.estimatedSessionMinutes} min",
            description = "Se usa para estimar el tiempo ahorrado cada vez que eliges «Mejor no».",
        ) {
            Slider(
                value = settings.estimatedSessionMinutes.toFloat(),
                onValueChange = { viewModel.onEstimatedSessionChange(it.toInt()) },
                valueRange = 1f..30f,
            )
        }

        SnoozeCard(
            state = snoozeState,
            onSnooze = snoozeViewModel::onSnoozeSelected,
            onCancel = snoozeViewModel::onCancelSnooze,
        )

        StrictModeCard(
            state = snoozeState,
            onEnable = snoozeViewModel::onEnableStrict,
            onTryDisable = snoozeViewModel::onTryDisableStrict,
        )

        BackupCard(
            onExport = { exportLauncher.launch(BackupManager.DEFAULT_FILE_NAME) },
            onImport = { importLauncher.launch(arrayOf(BackupManager.MIME_TYPE, "text/*", "*/*")) },
        )
    }
}

@Composable
private fun BackupCard(
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Copia de seguridad",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Exporta tus apps y estadísticas a un archivo, o impórtalas para no " +
                    "perderlas al actualizar o reinstalar la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Text("Exportar")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Text("Importar")
                }
            }
        }
    }
}

/** Formats a remaining-millis value as "Xh Ym" / "Xm" / "Xd Yh". */
private fun formatRemaining(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt() + 1 // round up
    val days = totalMinutes / 1440
    val hours = (totalMinutes % 1440) / 60
    val mins = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}min"
        else -> "${mins}min"
    }
}

@Composable
private fun durationLabel(minutes: Int): String = when {
    minutes >= 1440 -> stringResource(R.string.strict_duration_day)
    minutes >= 60 -> stringResource(R.string.strict_duration_hours, minutes / 60)
    else -> stringResource(R.string.strict_duration_minutes, minutes)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnoozeCard(
    state: SnoozeUiState,
    onSnooze: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.snooze_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.snooze_card_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))

            when {
                // Strict mode wins: snoozing is not allowed.
                state.strictMode -> {
                    Text(
                        text = stringResource(R.string.strict_blocks_snooze),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // Already paused: show the remaining time and a resume button.
                state.snoozeActive -> {
                    Text(
                        text = stringResource(
                            R.string.snooze_active_until,
                            state.remainingMinutes,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.snooze_cancel))
                    }
                }
                // Idle: offer the duration choices.
                else -> {
                    Text(
                        text = stringResource(R.string.snooze_inactive),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.options.forEach { minutes ->
                            FilterChip(
                                selected = false,
                                onClick = { onSnooze(minutes) },
                                label = {
                                    Text(stringResource(R.string.snooze_option_minutes, minutes))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrictModeCard(
    state: SnoozeUiState,
    onEnable: (Int) -> String,
    onTryDisable: (String) -> Boolean,
) {
    // Dialog flow state. The reveal dialog holds the just-generated password.
    var showDuration by remember { mutableStateOf(false) }
    var revealedPassword by remember { mutableStateOf<String?>(null) }
    var showUnlock by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.strict_card_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                // The switch only *starts* the flow when off. While the lock is on
                // it cannot be flipped off here — that needs the master password.
                Switch(
                    checked = state.strictMode,
                    onCheckedChange = { wantOn ->
                        if (wantOn && !state.strictMode) showDuration = true
                        else if (!wantOn && state.strictMode) showUnlock = true
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.strict_card_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            AntiUninstallRow()

            if (state.strictMode) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.strict_active_remaining,
                        formatRemaining(state.strictRemainingMillis),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = { showUnlock = true }) {
                    Text(stringResource(R.string.strict_unlock_button))
                }
            }
        }
    }

    if (showDuration) {
        DurationPickerDialog(
            options = state.strictOptions,
            onDismiss = { showDuration = false },
            onPick = { minutes ->
                showDuration = false
                revealedPassword = onEnable(minutes)
            },
        )
    }

    revealedPassword?.let { password ->
        PasswordRevealDialog(
            password = password,
            onDone = { revealedPassword = null },
        )
    }

    if (showUnlock) {
        UnlockDialog(
            onDismiss = { showUnlock = false },
            onConfirm = onTryDisable,
        )
    }
}

/** Status/CTA for the device-admin anti-uninstall protection. */
@Composable
private fun AntiUninstallRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var adminActive by remember { mutableStateOf(CogelaSuaveAdminReceiver.isActive(context)) }

    // Re-check on resume (the user toggles admin in a system screen, then returns).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                adminActive = CogelaSuaveAdminReceiver.isActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Spacer(Modifier.height(8.dp))
    if (adminActive) {
        Text(
            text = stringResource(R.string.strict_admin_active),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    } else {
        TextButton(onClick = { context.startActivity(CogelaSuaveAdminReceiver.enableIntent(context)) }) {
            Text(stringResource(R.string.strict_admin_protect))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationPickerDialog(
    options: List<Int>,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.strict_duration_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.strict_duration_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { minutes ->
                        FilterChip(
                            selected = false,
                            onClick = { onPick(minutes) },
                            label = { Text(durationLabel(minutes)) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PasswordRevealDialog(
    password: String,
    onDone: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {}, // force an explicit acknowledgement
        title = { Text(stringResource(R.string.strict_password_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.strict_password_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = StrictModeSecurity.formatForDisplay(password),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDone) {
                Text(stringResource(R.string.strict_password_done))
            }
        },
    )
}

@Composable
private fun UnlockDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean,
) {
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.strict_unlock_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.strict_unlock_message,
                        StrictModeSecurity.PASSWORD_LENGTH,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it.filter { c -> c.isDigit() }
                        wrong = false
                    },
                    singleLine = true,
                    isError = wrong,
                    label = { Text(stringResource(R.string.strict_unlock_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (wrong) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.strict_unlock_wrong),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (onConfirm(input)) onDismiss() else wrong = true
            }) {
                Text(stringResource(R.string.strict_unlock_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SettingCard(
    title: String,
    value: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
