package com.cogelasuave.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cogelasuave.R
import com.cogelasuave.domain.model.InterceptionDecision
import com.cogelasuave.domain.model.WatchedApp
import com.cogelasuave.domain.usecase.AddReasonTimeUseCase
import com.cogelasuave.domain.usecase.ObserveWatchedAppsUseCase
import com.cogelasuave.domain.usecase.RecordAttemptUseCase
import com.cogelasuave.domain.usecase.RecordDecisionUseCase
import com.cogelasuave.domain.usecase.RecordOpenUseCase
import com.cogelasuave.domain.usecase.ResolveWaitSecondsUseCase
import com.cogelasuave.service.overlay.CountdownChipController
import com.cogelasuave.service.overlay.OverlayController
import com.cogelasuave.service.overlay.OverlaySpec
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
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
    @Inject lateinit var recordOpen: RecordOpenUseCase
    @Inject lateinit var addReasonTime: AddReasonTimeUseCase
    @Inject lateinit var lastOpenStore: com.cogelasuave.data.system.LastOpenStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlay: OverlayController

    /** Draggable floating pill showing the session countdown over the watched app. */
    private lateinit var countdownChip: CountdownChipController

    /**
     * Snooze/strict-mode state. Instantiated directly with the service [Context]
     * (no Hilt) and read synchronously on the event path so we can cheaply skip
     * interception while the user has paused watching.
     */
    private lateinit var snoozeManager: SnoozeManager

    /** App label, used to spot our own uninstall dialog by its text. */
    private lateinit var appLabel: String

    /** Snapshot of watched apps kept in memory for synchronous lookups in the event path. */
    @Volatile private var watchedByPackage: Map<String, WatchedApp> = emptyMap()

    /** The foreground package we have most recently reacted to (handled or left). */
    private var lastHandledPackage: String? = null

    /** Guards against re-triggering while one interception is being prepared/shown. */
    @Volatile private var interceptInProgress = false

    /**
     * Active "time inside a watched app" session, started when the user opens with
     * a reason and closed when they leave. Null while no session is running.
     */
    private var sessionPackage: String? = null
    private var sessionReason: String? = null
    private var sessionStartMs: Long = 0L
    /** Minutes the user said they'd spend before the session auto-closes. */
    private var sessionPlannedMinutes: Int = 0
    /** Epoch millis at which the running session auto-closes, or 0 if none. */
    private var sessionEndMs: Long = 0L
    /** Ticker that updates the countdown UI each second and ends the session at 0. */
    private var sessionTimeoutJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        countdownChip = CountdownChipController(this)
        snoozeManager = SnoozeManager(this)
        createCountdownChannel()
        appLabel = getString(R.string.app_name)
        observeWatchedApps()
            .onEach { list -> watchedByPackage = list.associateBy { it.packageName } }
            .launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only care about a new window coming to the foreground.
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // --- Self-protection: while strict mode is on, refuse to let the user
        // reach the screens that would remove the app (device-admin deactivation
        // or its uninstall dialog). Kick them home before they can confirm.
        if (snoozeManager.isStrictMode && isSelfRemovalScreen(event, pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, R.string.strict_uninstall_blocked, Toast.LENGTH_SHORT).show()
            return
        }

        // --- 0. Close any open "time inside app" session ---------------------
        // If a timed session is running and the foreground moved to something that
        // is neither that app, our own UI, nor a soft surface (keyboard/systemui
        // floating over it), the user has left: bank the elapsed time.
        maybeEndSession(pkg)

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

    /**
     * Detects the two screens that can remove the app:
     *  - the device-admin *deactivation* confirmation (Settings `DeviceAdminAdd`);
     *  - the package-installer *uninstall* dialog naming this app.
     */
    private fun isSelfRemovalScreen(event: AccessibilityEvent, pkg: String): Boolean {
        val cls = event.className?.toString().orEmpty()
        // Settings device-admin add/deactivate screen.
        if (pkg == "com.android.settings" && cls.contains("DeviceAdminAdd")) return true
        // Uninstall confirmation: only block when the dialog mentions our app.
        if (pkg in INSTALLER_PACKAGES) {
            val text = event.text.joinToString(" ")
            if (text.contains(appLabel, ignoreCase = true)) return true
        }
        return false
    }

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
                    lastOpenedAtMs = lastOpenStore.getLastOpen(watched.packageName),
                    onOpen = { reason, minutes -> onUserOpened(watched.packageName, reason, minutes) },
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

    /**
     * User chose to open [packageName] under [reason]: drop the overlay, let them
     * in, record the open, and start the timed session that [maybeEndSession] will
     * close when they leave.
     */
    private fun onUserOpened(packageName: String, reason: String, plannedMinutes: Int) {
        overlay.dismiss()
        interceptInProgress = false
        sessionPackage = packageName
        sessionReason = reason
        sessionPlannedMinutes = plannedMinutes
        sessionStartMs = System.currentTimeMillis()
        lastOpenStore.setLastOpen(packageName, sessionStartMs)

        sessionTimeoutJob?.cancel()
        sessionEndMs = 0L
        if (plannedMinutes > 0) {
            startCountdown(packageName, plannedMinutes)
        }
        scope.launch { recordOpen(packageName, reason) }
    }

    /**
     * Shows the countdown chip + ongoing notification and starts a 1-second ticker
     * that refreshes the chip until the planned time runs out, then ends the
     * session (kicks the user home). Tapping the chip ends it early.
     */
    private fun startCountdown(packageName: String, plannedMinutes: Int) {
        sessionEndMs = System.currentTimeMillis() + plannedMinutes.toLong() * 60_000L
        countdownChip.show(formatRemaining(sessionEndMs)) {
            onSessionTimedOut(packageName)   // tap = end now
        }
        showCountdownNotification(sessionEndMs)

        sessionTimeoutJob = scope.launch {
            while (true) {
                val remaining = sessionEndMs - System.currentTimeMillis()
                if (remaining <= 0) break
                countdownChip.update(formatRemaining(sessionEndMs))
                delay(1_000)
            }
            onSessionTimedOut(packageName)
        }
    }

    /**
     * The planned minutes elapsed (or the user tapped the chip) while still inside
     * the app: kick the user home and bank the session. No-op if they already left
     * or switched sessions.
     */
    private fun onSessionTimedOut(packageName: String) {
        if (sessionPackage != packageName) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, R.string.session_time_up, Toast.LENGTH_LONG).show()
        maybeEndSession(foregroundPkg = "")   // "" != pkg -> banks elapsed time and clears
    }

    /** Banks the running session's elapsed time if [foregroundPkg] means the user left. */
    private fun maybeEndSession(foregroundPkg: String) {
        val pkg = sessionPackage ?: return
        val reason = sessionReason ?: return
        if (foregroundPkg == pkg) return                 // still inside the app
        if (foregroundPkg == packageName) return         // our own overlay / app
        if (isSoftSurface(foregroundPkg)) return         // keyboard/systemui floating over it

        val startMs = sessionStartMs
        sessionPackage = null
        sessionReason = null
        sessionStartMs = 0L
        sessionPlannedMinutes = 0
        sessionEndMs = 0L
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = null
        clearCountdownUi()

        val rawSeconds = (System.currentTimeMillis() - startMs) / 1000
        // Cap to guard against screen-off / killed-process inflation.
        val seconds = rawSeconds.coerceIn(0, MAX_SESSION_SECONDS)
        if (seconds <= 0) return
        val epochDay = Instant.ofEpochMilli(startMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        scope.launch { addReasonTime(pkg, reason, epochDay, seconds) }
    }

    /**
     * Soft surfaces float *over* the current app without the user leaving it:
     * keyboards and the system UI shade. Unlike launchers, these must NOT end a
     * session. Subset of [SYSTEM_SURFACE_PREFIXES] limited to IME / systemui.
     */
    private fun isSoftSurface(pkg: String): Boolean =
        pkg == "com.android.systemui" ||
            SOFT_SURFACE_PREFIXES.any { pkg.startsWith(it) }

    // --- Countdown UI helpers ------------------------------------------------

    /** "MM:SS" left until [endMs], clamped at 00:00. */
    private fun formatRemaining(endMs: Long): String {
        val secs = ((endMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0L)
        return "⏱ %02d:%02d".format(secs / 60, secs % 60)
    }

    private fun createCountdownChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            COUNTDOWN_CHANNEL_ID,
            getString(R.string.session_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Ongoing notification with a system-driven countdown chronometer. Best-effort. */
    private fun showCountdownNotification(endMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(this, COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.session_notification_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) setChronometerCountDown(true) }
            .setWhen(endMs)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            getSystemService(NotificationManager::class.java)
                ?.notify(COUNTDOWN_NOTIFICATION_ID, notification)
        }
    }

    private fun clearCountdownUi() {
        if (::countdownChip.isInitialized) countdownChip.dismiss()
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(COUNTDOWN_NOTIFICATION_ID)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (::overlay.isInitialized) overlay.dismiss()
        sessionTimeoutJob?.cancel()
        clearCountdownUi()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        sessionTimeoutJob?.cancel()
        clearCountdownUi()
        if (::overlay.isInitialized) overlay.dismiss()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        /** Hard ceiling on a single banked session, to absorb screen-off gaps. */
        const val MAX_SESSION_SECONDS = 4L * 60 * 60

        const val COUNTDOWN_CHANNEL_ID = "session_countdown"
        const val COUNTDOWN_NOTIFICATION_ID = 4201

        /** Package-installer packages whose uninstall dialog we guard against. */
        val INSTALLER_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
        )

        /**
         * Prefixes for surfaces that float *over* the current app (keyboards,
         * systemui) and so must not end a timed session. Launchers are excluded
         * on purpose: going home does count as leaving the app.
         */
        val SOFT_SURFACE_PREFIXES = listOf(
            "com.android.systemui",
            "com.android.inputmethod",
            "com.google.android.inputmethod",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey",
        )

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
