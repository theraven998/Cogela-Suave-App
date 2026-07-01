package com.cogelasuave.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cogelasuave.R
import com.cogelasuave.domain.model.Reasons
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Duration of one inhale or one exhale, in milliseconds. */
private const val BREATH_PHASE_MS = 4_000

/** Thin wrapper so the overlay can format string resources from a non-Activity context. */
@Composable
private fun stringResourceCompat(id: Int, vararg formatArgs: Any): String =
    stringResource(id, *formatArgs)

/**
 * The full-screen pause. Plays a breathing animation, enforces an obligatory wait,
 * shows today's attempt count, and then offers the open / back-out choice.
 */
@Composable
fun BreathingOverlayScreen(
    appLabel: String,
    waitSeconds: Int,
    attemptsToday: Int,
    lastOpenedAtMs: Long?,
    onOpen: (reason: String, plannedMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val totalWait = remember(waitSeconds) { waitSeconds.coerceAtLeast(0) }
    var remaining by remember { mutableIntStateOf(totalWait) }
    val unlocked = remaining <= 0

    // Obligatory countdown.
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
    }

    // Smoothly-animated progress of the timer (0f at start → 1f when unlocked).
    val targetProgress = if (totalWait == 0) 1f else (totalWait - remaining).toFloat() / totalWait
    val progress by animateFloatAsState(
        targetValue = targetProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "timerProgress",
    )

    // Breathing phase toggles between inhale (expanded) and exhale (contracted).
    var inhaling by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(BREATH_PHASE_MS.toLong())
            inhaling = !inhaling
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (inhaling) 1f else 0.55f,
        animationSpec = tween(durationMillis = BREATH_PHASE_MS, easing = FastOutSlowInEasing),
        label = "breathScale",
    )

    val colors = MaterialTheme.colorScheme
    // Inhala uses the primary tone, exhala drifts toward the cooler tertiary.
    val coreColor by animateColorAsState(
        targetValue = if (inhaling) colors.primary else colors.tertiary,
        animationSpec = tween(durationMillis = BREATH_PHASE_MS, easing = FastOutSlowInEasing),
        label = "coreColor",
    )

    // A continuous, very soft pulse layered under the breath, so the halo never feels static.
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.background, colors.surface, colors.background)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top: which app + how many times today.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResourceCompat(R.string.overlay_attempts_today, attemptsToday),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(12.dp))
                AbstinenceStreak(lastOpenedAtMs = lastOpenedAtMs)
            }

            // Middle: the breathing circle with halo, color transition and timer ring.
            BreathingCircle(
                scale = scale,
                pulseScale = pulseScale,
                inhaling = inhaling,
                coreColor = coreColor,
                progress = progress,
                showProgressRing = !unlocked,
            )

            // Bottom: countdown while locked, decision once unlocked.
            DecisionArea(
                unlocked = unlocked,
                remaining = remaining,
                onOpen = onOpen,
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * The fun incentive: a live-ticking "how long you've gone without opening this app"
 * streak. Ticks once a minute so it stays cheap while the overlay is up.
 */
@Composable
private fun AbstinenceStreak(lastOpenedAtMs: Long?) {
    if (lastOpenedAtMs == null) return
    val colors = MaterialTheme.colorScheme

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }
    val elapsed = (now - lastOpenedAtMs).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.14f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "🌱 " + stringResourceCompat(R.string.overlay_streak, formatStreak(elapsed)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Human "Xd Yh" / "Xh Ym" / "Xm" / "Xs" from an elapsed-millis duration. */
private fun formatStreak(millis: Long): String {
    val totalSeconds = millis / 1000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

/** Inclusive minute range offered by the usage-time wheel. Tope = 10 min. */
private const val MIN_MINUTES = 1
private const val MAX_MINUTES = 10
private const val DEFAULT_MINUTES = 5

@Composable
private fun BreathingCircle(
    scale: Float,
    pulseScale: Float,
    inhaling: Boolean,
    coreColor: Color,
    progress: Float,
    showProgressRing: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Box(contentAlignment = Alignment.Center) {
        // Outermost diffuse halo (softest, follows breath + idle pulse).
        Box(
            modifier = Modifier
                .size(300.dp)
                .scale(scale * pulseScale)
                .alpha(0.08f)
                .clip(CircleShape)
                .background(coreColor),
        )
        // Middle halo.
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(scale)
                .alpha(0.18f)
                .clip(CircleShape)
                .background(coreColor),
        )

        // Timer progress ring, drawn just outside the core while the wait is active.
        if (showProgressRing) {
            val ringColor = colors.primary
            val ringTrack = colors.onBackground.copy(alpha = 0.12f)
            Canvas(modifier = Modifier.size(196.dp)) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(
                    size.width - stroke,
                    size.height - stroke,
                )
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(
                    color = ringTrack,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        // Solid core.
        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(coreColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (inhaling) {
                    stringResourceCompat(R.string.breathe_in)
                } else {
                    stringResourceCompat(R.string.breathe_out)
                },
                color = colors.onPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
            )
        }
    }
}

@Composable
private fun DecisionArea(
    unlocked: Boolean,
    remaining: Int,
    onOpen: (reason: String, plannedMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var showOther by remember { mutableStateOf(false) }
    var otherText by remember { mutableStateOf("") }
    // Once a reason is picked we move to the time step instead of opening straight away.
    var chosenReason by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // While the wait runs, the only control is the disabled countdown button.
        if (!unlocked) {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(
                    text = stringResourceCompat(R.string.overlay_wait_seconds, remaining),
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(text = stringResourceCompat(R.string.overlay_dismiss), fontSize = 16.sp)
            }
            return@Column
        }

        // Step 2: reason chosen → ask how long they plan to use it.
        val reason = chosenReason
        if (reason != null) {
            TimeChoiceArea(
                onConfirm = { minutes -> onOpen(reason, minutes) },
                onBack = { chosenReason = null },
            )
            return@Column
        }

        // Step 1: pick a reason or back out.
        Text(
            text = stringResourceCompat(R.string.overlay_reason_question),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (showOther) {
            OutlinedTextField(
                value = otherText,
                onValueChange = { otherText = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResourceCompat(R.string.overlay_reason_other_label)) },
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { chosenReason = otherText.trim() },
                enabled = otherText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Text(text = stringResourceCompat(R.string.overlay_open), fontSize = 15.sp)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showOther = false; otherText = "" }) {
                Text(text = stringResourceCompat(R.string.overlay_reason_back))
            }
        } else {
            // Four fixed reasons in a 2×2 grid.
            Reasons.FIXED.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { fixedReason ->
                        Button(
                            onClick = { chosenReason = fixedReason },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                            ),
                        ) {
                            Text(text = fixedReason, fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { showOther = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(text = stringResourceCompat(R.string.overlay_reason_other), fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text(text = stringResourceCompat(R.string.overlay_dismiss), fontSize = 15.sp)
        }
    }
}

/**
 * Step 2 of the unlocked flow: a draggable circular dial to pick how many minutes
 * the user plans to spend in the app (capped at [MAX_MINUTES]), then confirm.
 */
@Composable
private fun TimeChoiceArea(
    onConfirm: (minutes: Int) -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var selected by remember { mutableIntStateOf(DEFAULT_MINUTES) }

    Text(
        text = stringResourceCompat(R.string.overlay_time_question),
        style = MaterialTheme.typography.titleMedium,
        color = colors.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp),
    )

    CircularDurationPicker(
        value = selected,
        onValueChange = { selected = it },
    )

    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { onConfirm(selected) },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        ),
    ) {
        Text(text = stringResourceCompat(R.string.overlay_open), fontSize = 15.sp)
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onBack) {
        Text(text = stringResourceCompat(R.string.overlay_reason_back))
    }
}

/** Diameter of the dial. */
private val DIAL_SIZE = 230.dp

/**
 * A circular, drag-to-set dial (think a sleep-timer ring): a thick progress arc
 * runs clockwise from the top, a draggable handle marks the end, minute ticks
 * sit just inside, and the chosen value is shown big in the center. Tapping or
 * dragging anywhere on the ring snaps the handle to the nearest whole minute.
 */
@Composable
private fun CircularDurationPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val sizePx = with(density) { DIAL_SIZE.toPx() }
    val center = Offset(sizePx / 2f, sizePx / 2f)

    // Map a touch point to a whole minute (clockwise from the top = minimum).
    fun offsetToValue(pos: Offset): Int {
        val dx = pos.x - center.x
        val dy = pos.y - center.y
        var a = atan2(dy.toDouble(), dx.toDouble()) + PI / 2  // shift so top == 0
        if (a < 0) a += 2 * PI
        val fraction = (a / (2 * PI)).toFloat()
        return (fraction * MAX_MINUTES).roundToInt().coerceIn(MIN_MINUTES, MAX_MINUTES)
    }

    val fraction = value.toFloat() / MAX_MINUTES
    val trackColor = colors.onBackground.copy(alpha = 0.10f)
    val arcColor = colors.primary
    val tickColor = colors.onBackground.copy(alpha = 0.25f)
    val tickActive = colors.primary

    Box(
        modifier = Modifier
            .size(DIAL_SIZE)
            .pointerInput(Unit) {
                detectTapGestures { onValueChange(offsetToValue(it)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onValueChange(offsetToValue(change.position))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(DIAL_SIZE)) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2f + 6.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - inset * 2,
                size.height - inset * 2,
            )
            val topLeft = Offset(inset, inset)
            val radius = arcSize.width / 2f

            // Background track + the filled progress arc.
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Minute ticks just inside the ring.
            val tickOuter = radius - stroke / 2f - 8.dp.toPx()
            val tickInner = tickOuter - 8.dp.toPx()
            for (i in 1..MAX_MINUTES) {
                val ang = -PI / 2 + 2 * PI * (i.toFloat() / MAX_MINUTES)
                val ca = cos(ang).toFloat()
                val sa = sin(ang).toFloat()
                drawLine(
                    color = if (i <= value) tickActive else tickColor,
                    start = Offset(center.x + ca * tickInner, center.y + sa * tickInner),
                    end = Offset(center.x + ca * tickOuter, center.y + sa * tickOuter),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Draggable handle at the arc's end.
            val handleAng = -PI / 2 + 2 * PI * fraction
            val hx = center.x + cos(handleAng).toFloat() * radius
            val hy = center.y + sin(handleAng).toFloat() * radius
            drawCircle(color = colors.onPrimary, radius = stroke / 2f + 4.dp.toPx(), center = Offset(hx, hy))
            drawCircle(color = arcColor, radius = stroke / 2f - 2.dp.toPx(), center = Offset(hx, hy))
        }

        // Center readout.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$value",
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
            Text(
                text = if (value == 1) "minuto" else "minutos",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}
