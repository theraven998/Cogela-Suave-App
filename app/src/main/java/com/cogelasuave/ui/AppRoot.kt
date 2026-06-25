package com.cogelasuave.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.cogelasuave.ui.apps.AppsScreen
import com.cogelasuave.ui.permissions.PermissionsScreen
import com.cogelasuave.ui.permissions.rememberPermissionsState
import com.cogelasuave.ui.settings.TimeSettingsScreen
import com.cogelasuave.ui.stats.StatsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Apps("Apps", Icons.Outlined.Apps),
    Time("Tiempo", Icons.Outlined.Timer),
    Stats("Estadísticas", Icons.Outlined.BarChart),
}

@Composable
fun AppRoot() {
    val permissions = rememberPermissionsState()

    // Until both permissions are granted, the app only shows the guided setup.
    if (!permissions.allGranted) {
        PermissionsScreen(state = permissions)
        return
    }

    var selected by remember { mutableStateOf(Tab.Apps) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selected) {
                Tab.Apps -> AppsScreen()
                Tab.Time -> TimeSettingsScreen()
                Tab.Stats -> StatsScreen()
            }
        }
    }
}
