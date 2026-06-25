package com.cogelasuave.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cogelasuave.ui.overlay.BreathingOverlayScreen
import com.cogelasuave.ui.theme.CogelaSuaveTheme

/** Everything the overlay needs to render one interception. */
data class OverlaySpec(
    val packageName: String,
    val appLabel: String,
    val waitSeconds: Int,
    val attemptsToday: Int,
    val onOpen: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Owns the single full-screen breathing window drawn over other apps via
 * [WindowManager]. Compose renders into a [ComposeView] that we back with a
 * standalone lifecycle/view-model/saved-state owner.
 */
class OverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var hostView: FrameLayout? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = hostView != null

    fun show(spec: OverlaySpec) {
        if (isShowing) return

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        val composeView = ComposeView(context).apply {
            setContent {
                CogelaSuaveTheme {
                    BreathingOverlayScreen(
                        appLabel = spec.appLabel,
                        waitSeconds = spec.waitSeconds,
                        attemptsToday = spec.attemptsToday,
                        onOpen = spec.onOpen,
                        onDismiss = spec.onDismiss,
                    )
                }
            }
        }

        // Root view captures the back key and routes it to "Mejor no".
        val host = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK &&
                    event.action == KeyEvent.ACTION_UP
                ) {
                    spec.onDismiss()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        host.addView(composeView)

        // Wire the view tree so Compose can find its owners.
        host.setViewTreeLifecycleOwner(owner)
        host.setViewTreeViewModelStoreOwner(owner)
        host.setViewTreeSavedStateRegistryOwner(owner)

        runCatching { windowManager.addView(host, buildLayoutParams()) }
            .onSuccess {
                hostView = host
                lifecycleOwner = owner
            }
            .onFailure { owner.onDestroy() }
    }

    fun dismiss() {
        val host = hostView ?: return
        runCatching { windowManager.removeViewImmediate(host) }
        lifecycleOwner?.onDestroy()
        hostView = null
        lifecycleOwner = null
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Focusable (so we receive the back key) but fullscreen and modal so the
            // app behind never receives the touch that opened it.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
    }
}
