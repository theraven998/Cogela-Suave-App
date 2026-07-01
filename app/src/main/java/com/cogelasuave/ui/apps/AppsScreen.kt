package com.cogelasuave.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.cogelasuave.domain.model.AppInfo

@Composable
fun AppsScreen(viewModel: AppsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingApp by remember { mutableStateOf<AppInfo?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Buscar app") },
        )

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        strictMode = state.strictMode,
                        onWatchedChange = { viewModel.onWatchedChange(app, it) },
                        onEditWait = { editingApp = app },
                    )
                    Divider()
                }
            }
        }
    }

    editingApp?.let { app ->
        WaitOverrideDialog(
            app = app,
            onDismiss = { editingApp = null },
            onSelect = { seconds ->
                viewModel.onCustomWaitChange(app, seconds)
                editingApp = null
            },
        )
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    strictMode: Boolean,
    onWatchedChange: (Boolean) -> Unit,
    onEditWait: () -> Unit,
) {
    // While strict mode is on, a watched app is locked: can't un-watch or edit wait.
    val locked = strictMode && app.isWatched
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
            if (app.isWatched) {
                val waitLabel = app.customWaitSeconds?.let { "$it s" } ?: "tiempo global"
                val suffix = if (locked) "· bloqueada (modo estricto)" else "· toca para cambiar"
                Text(
                    text = "Espera: $waitLabel $suffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (locked) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = if (locked) Modifier else Modifier.clickable(onClick = onEditWait),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = app.isWatched,
            onCheckedChange = onWatchedChange,
            enabled = !locked,
        )
    }
}

private val WAIT_OPTIONS = listOf<Int?>(null, 5, 10, 20, 30, 60)

@Composable
private fun WaitOverrideDialog(
    app: AppInfo,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
        title = { Text("Espera para ${app.label}") },
        text = {
            Column {
                WAIT_OPTIONS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = app.customWaitSeconds == option,
                            onClick = { onSelect(option) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = option?.let { "$it segundos" } ?: "Usar tiempo global",
                            fontWeight = if (app.customWaitSeconds == option) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    }
                }
            }
        },
    )
}
