package com.cogelasuave.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cogelasuave.service.PermissionChecker

/**
 * First-run guidance. Walks the user through granting the overlay and accessibility
 * permissions; each card reflects its live state.
 */
@Composable
fun PermissionsScreen(state: PermissionsState) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Cógela suave",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Para poner una pausa antes de abrir tus apps, Cógela Suave necesita dos permisos. " +
                "Nada sale de tu teléfono.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(24.dp))

        PermissionCard(
            step = "1",
            title = "Mostrar sobre otras apps",
            description = "Permite dibujar la pantalla de respiración encima de la app que abres.",
            granted = state.overlayGranted,
            buttonText = "Conceder",
            onClick = { context.startActivity(PermissionChecker.overlaySettingsIntent(context)) },
        )

        Spacer(Modifier.height(16.dp))

        PermissionCard(
            step = "2",
            title = "Servicio de accesibilidad",
            description = "Permite detectar cuándo abres una app vigilada. Busca «Cógela Suave» en la lista " +
                "y actívalo.",
            granted = state.accessibilityEnabled,
            buttonText = "Abrir ajustes",
            onClick = { context.startActivity(PermissionChecker.accessibilitySettingsIntent()) },
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Cuando ambos estén activos, esta pantalla se cierra sola.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PermissionCard(
    step: String,
    title: String,
    description: String,
    granted: Boolean,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (granted) Icons.Filled.CheckCircle
                    else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    text = "  Paso $step · $title",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(16.dp))
            if (granted) {
                Text(
                    text = "Concedido ✓",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF2E7D32),
                )
            } else {
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(buttonText)
                }
            }
        }
    }
}
