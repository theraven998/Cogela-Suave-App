package com.cogelasuave.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.cogelasuave.domain.model.InterceptionDecision
import com.cogelasuave.domain.model.WatchedApp
import com.cogelasuave.domain.usecase.ObserveWatchedAppsUseCase
import com.cogelasuave.domain.usecase.RecordAttemptUseCase
import com.cogelasuave.domain.usecase.RecordDecisionUseCase
import com.cogelasuave.domain.usecase.ResolveWaitSecondsUseCase
import com.cogelasuave.service.overlay.OverlayController
import com.cogelasuave.service.overlay.OverlaySpec
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Watches foreground-app changes and, when the user opens one of their watched apps,
 * raises the breathing overlay before letting them in.
 */
@AndroidEntryPoint
class InterceptAccessibilityService : AccessibilityService() {

    @Inject lateinit var observeWatchedApps: ObserveWatchedAppsUseCase
    @Inject lateinit var resolveWaitSeconds: ResolveWaitSecondsUseCase
    @Inject lateinit var recordAttempt: RecordAttemptUseCase
    @Inject lateinit var recordDecision: RecordDecisionUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlay: OverlayController

    /**
     * Snooze/strict-mode state. Instantiated directly with the service [Context]
     * (no Hilt) and read synchronously on the event path so we can cheaply skip
     * interception while the user has paused watching.
     */
    private lateinit var snoozeManager: SnoozeManager

    /** Snapshot of watched apps kept in memory for synchronous lookups in the event path. */
    @Volatile private var watchedByPackage: Map<String, WatchedApp> = emptyMap()

    /** The foreground package we have most recently reacted to (handled or left). */
    private var lastHandledPackage: String? = null

    /** Guards against re-triggering while one interception is being prepared/shown. */
    @Volatile private var interceptInProgress = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        snoozeManager = SnoozeManager(this)
        observeWatchedApps()
            .onEach { list -> watchedByPackage = list.associateBy { it.packageName } }
            .launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only care about a new window coming to the foreground.
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // --- 1. Ignore "non-app" surfaces ------------------------------------
        // Keyboards (IMEs), the launcher, the system UI shade, popups, etc. fire
        // window-state-changed events too. If we let those overwrite
        // lastHandledPackage we would (a) think the user "left" the watched app
        // when really a keyboard just popped up, and then (b) re-intercept the
        // moment focus returns to that same watched app — an annoying loop.
        // So these surfaces are transparent to our state machine: skip without
        // touching lastHandledPackage.
        if (pkg == packageName) return            // our own windows / overlay
        if (isTransparentSurface(pkg)) return     // systemui, IMEs, launchers…

        // --- 2. Re-trigger guard ---------------------------------------------
        // While an interception is being prepared or its overlay is on screen we
        // must not start another one.
        if (interceptInProgress || overlay.isShowing) return

        val watched = watchedByPackage[pkg]
        if (watched == null) {
            // User is in some other (non-watched, non-system) app; remember it so
            // that genuinely re-entering a watched app later re-triggers the pause.
            lastHandledPackage = pkg
            return
        }

        // --- 3. Snooze / pause check -----------------------------------------
        // If the user paused watching (snooze) we treat the watched app like any
        // ordinary app: record it as the current foreground and let them in. We
        // update lastHandledPackage so that, once the snooze expires while still
        // inside the app, we don't immediately intercept on the next stray event.
        if (snoozeManager.isSnoozeActive()) {
            lastHandledPackage = pkg
            return
        }

        // --- 4. Already inside this watched app ------------------------------
        if (pkg == lastHandledPackage) return

        // --- 5. Intercept -----------------------------------------------------
        intercept(watched)
    }

    /**
     * True for packages that are part of the system shell rather than a real app
     * the user "switched to": the system UI, input methods (keyboards), and the
     * common stock launchers. Events from these are ignored entirely so they
     * never disturb the foreground-app tracking in [onAccessibilityEvent].
     */
    private fun isTransparentSurface(pkg: String): Boolean =
        pkg in SYSTEM_SURFACES ||
            SYSTEM_SURFACE_PREFIXES.any { pkg.startsWith(it) }

    private fun intercept(watched: WatchedApp) {
        interceptInProgress = true
        lastHandledPackage = watched.packageName

        scope.launch {
            val attempts = recordAttempt(watched.packageName)
            val waitSeconds = resolveWaitSeconds(watched.customWaitSeconds)

            overlay.show(
                OverlaySpec(
                    packageName = watched.packageName,
                    appLabel = watched.label,
                    waitSeconds = waitSeconds,
                    attemptsToday = attempts,
                    onOpen = { onUserChose(watched.packageName, InterceptionDecision.OPENED) },
                    onDismiss = { onUserChose(watched.packageName, InterceptionDecision.DISMISSED) },
                )
            )
            // If the overlay failed to attach (e.g. permission revoked) don't get stuck.
            if (!overlay.isShowing) interceptInProgress = false
        }
    }

    private fun onUserChose(packageName: String, decision: InterceptionDecision) {
        overlay.dismiss()
        if (decision == InterceptionDecision.DISMISSED) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        interceptInProgress = false
        scope.launch { recordDecision(packageName, decision) }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (::overlay.isInitialized) overlay.dismiss()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (::overlay.isInitialized) overlay.dismiss()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        /**
         * Exact package names that must never count as "the user opened an app".
         * These are full matches; for vendor variants that share a known prefix
         * use [SYSTEM_SURFACE_PREFIXES] instead.
         */
        val SYSTEM_SURFACES = setOf(
            "com.android.systemui",          // status bar, shade, recents overlay
            "android",                       // framework-internal windows
            "com.google.android.inputmethod.latin", // Gboard
            "com.android.launcher",          // AOSP launcher
            "com.android.launcher3",         // AOSP launcher3 / many OEMs
            "com.google.android.apps.nexuslauncher", // Pixel launcher
            "com.sec.android.app.launcher",  // Samsung One UI launcher
            "com.miui.home",                 // Xiaomi launcher
            "com.android.intentresolver",    // share / "open with" chooser
        )

        /**
         * Package-name prefixes for whole families of system / IME surfaces. A
         * prefix match keeps the list robust across OEM and version differences
         * (e.g. the many `*.inputmethod.*` keyboards, vendor system UIs, and the
         * common launcher namespaces) without having to enumerate every variant.
         */
        val SYSTEM_SURFACE_PREFIXES = listOf(
            "com.android.systemui",          // any systemui sub-package
            "com.android.inputmethod",       // AOSP keyboards
            "com.google.android.inputmethod", // Gboard / voice / handwriting
            "com.samsung.android.honeyboard", // Samsung keyboard
            "com.touchtype.swiftkey",        // SwiftKey
            "com.android.launcher",          // launcher family
        )
    }
}
