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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cogelasuave.R
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
    onOpen: () -> Unit,
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
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = unlocked, enter = fadeIn()) {
            Text(
                text = stringResourceCompat(R.string.overlay_question),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }

        Button(
            onClick = onOpen,
            enabled = unlocked,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
        ) {
            Text(
                text = if (unlocked) {
                    stringResourceCompat(R.string.overlay_open)
                } else {
                    stringResourceCompat(R.string.overlay_wait_seconds, remaining)
                },
                fontSize = 16.sp,
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text(text = stringResourceCompat(R.string.overlay_dismiss), fontSize = 16.sp)
        }
    }
}
