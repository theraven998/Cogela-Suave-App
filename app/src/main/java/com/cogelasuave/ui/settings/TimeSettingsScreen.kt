package com.cogelasuave.ui.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cogelasuave.R
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
            enabled = snoozeState.strictMode,
            onChange = snoozeViewModel::onStrictModeChange,
        )
    }
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

@Composable
private fun StrictModeCard(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
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
                Switch(checked = enabled, onCheckedChange = onChange)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.strict_card_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
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
