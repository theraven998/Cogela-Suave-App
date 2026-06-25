package com.cogelasuave.ui.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cogelasuave.service.PermissionChecker

data class PermissionsState(
    val overlayGranted: Boolean,
    val accessibilityEnabled: Boolean,
) {
    val allGranted: Boolean get() = overlayGranted && accessibilityEnabled
}

/**
 * Reads the live permission state and re-checks it every time the screen resumes,
 * so returning from system settings immediately reflects the new grants.
 */
@Composable
fun rememberPermissionsState(): PermissionsState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var overlay by remember { mutableStateOf(PermissionChecker.canDrawOverlays(context)) }
    var accessibility by remember {
        mutableStateOf(PermissionChecker.isAccessibilityServiceEnabled(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlay = PermissionChecker.canDrawOverlays(context)
                accessibility = PermissionChecker.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return PermissionsState(overlayGranted = overlay, accessibilityEnabled = accessibility)
}
