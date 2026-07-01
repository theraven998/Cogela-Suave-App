package com.cogelasuave.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cogelasuave.R
import com.cogelasuave.domain.model.DailyStat
import com.cogelasuave.domain.model.DayStats
import com.cogelasuave.domain.model.ReasonStat
import com.cogelasuave.domain.model.WeeklySummary
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.stats_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(text = stringResource(R.string.stats_today), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile(
                    modifier = Modifier.weight(1f),
                    value = state.totalAttempts.toString(),
                    label = stringResource(R.string.stats_label_attempts),
                )
                SummaryTile(
                    modifier = Modifier.weight(1f),
                    value = state.totalDismissed.toString(),
                    label = stringResource(R.string.stats_label_dismissed),
                )
                SummaryTile(
                    modifier = Modifier.weight(1f),
                    value = stringResource(R.string.stats_minutes_short, state.estimatedMinutesSaved),
                    label = stringResource(R.string.stats_label_saved),
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            WeeklySection(state.weekly)
        }

        item {
            Spacer(Modifier.height(12.dp))
            ReasonsSection(
                reasons = state.reasons,
                range = state.reasonRange,
                onRangeChange = viewModel::setReasonRange,
            )
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text(text = stringResource(R.string.stats_per_app), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }

        if (state.perApp.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.stats_empty_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(state.perApp, key = { it.packageName }) { stat ->
                StatRow(stat)
            }
        }
    }
}

@Composable
private fun WeeklySection(weekly: WeeklySummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_week_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            if (weekly.isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.stats_week_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStat(
                        modifier = Modifier.weight(1f),
                        value = weekly.totalAttempts.toString(),
                        label = stringResource(R.string.stats_week_total_attempts),
                    )
                    MiniStat(
                        modifier = Modifier.weight(1f),
                        value = weekly.totalDismissed.toString(),
                        label = stringResource(R.string.stats_week_total_dismissed),
                    )
                    MiniStat(
                        modifier = Modifier.weight(1f),
                        value = stringResource(R.string.stats_minutes_short, weekly.estimatedMinutesSaved),
                        label = stringResource(R.string.stats_week_total_saved),
                    )
                }
                Spacer(Modifier.height(16.dp))
                WeekBars(weekly.days)
            }
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier = Modifier, value: String, label: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

/** Per-day mini bar chart. Bar height encodes attempts, the dismissed share is highlighted. */
@Composable
private fun WeekBars(days: List<DayStats>) {
    val maxAttempts = (days.maxOfOrNull { it.attempts } ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val dismissedColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val today = LocalDate.now().toEpochDay()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
                    drawDayBar(
                        attempts = day.attempts,
                        dismissed = day.dismissed,
                        maxAttempts = maxAttempts,
                        track = trackColor,
                        attemptsColor = barColor,
                        dismissedColor = dismissedColor,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = dayLabel(day.epochDay),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (day.epochDay == today) FontWeight.Bold else FontWeight.Normal,
                    color = if (day.epochDay == today) MaterialTheme.colorScheme.primary else labelColor,
                )
            }
        }
    }
}

private fun DrawScope.drawDayBar(
    attempts: Int,
    dismissed: Int,
    maxAttempts: Int,
    track: Color,
    attemptsColor: Color,
    dismissedColor: Color,
) {
    val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    // Background track.
    drawRoundRect(color = track, cornerRadius = corner)

    if (attempts <= 0) return

    val fraction = attempts.toFloat() / maxAttempts.toFloat()
    val barHeight = size.height * fraction
    val top = size.height - barHeight
    // Attempts bar (grows from the bottom).
    drawRoundRect(
        color = attemptsColor,
        topLeft = androidx.compose.ui.geometry.Offset(0f, top),
        size = androidx.compose.ui.geometry.Size(size.width, barHeight),
        cornerRadius = corner,
    )
    // Dismissed share, drawn on top from the bottom.
    if (dismissed > 0) {
        val dismissedFraction = (dismissed.toFloat() / attempts.toFloat()).coerceIn(0f, 1f)
        val dismissedHeight = barHeight * dismissedFraction
        drawRoundRect(
            color = dismissedColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - dismissedHeight),
            size = androidx.compose.ui.geometry.Size(size.width, dismissedHeight),
            cornerRadius = corner,
        )
    }
}

private fun dayLabel(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay)
        .dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
        .take(3)
        .replaceFirstChar { it.uppercase() }

@Composable
private fun ReasonsSection(
    reasons: List<ReasonStat>,
    range: ReasonRange,
    onRangeChange: (ReasonRange) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_reasons_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            // Día / Semana toggle.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = range == ReasonRange.DAY,
                    onClick = { onRangeChange(ReasonRange.DAY) },
                    label = { Text(stringResource(R.string.stats_reasons_range_day)) },
                )
                FilterChip(
                    selected = range == ReasonRange.WEEK,
                    onClick = { onRangeChange(ReasonRange.WEEK) },
                    label = { Text(stringResource(R.string.stats_reasons_range_week)) },
                )
            }
            Spacer(Modifier.height(16.dp))

            if (reasons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.stats_reasons_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                val maxSeconds = (reasons.maxOfOrNull { it.seconds } ?: 0L).coerceAtLeast(1L)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    reasons.forEach { reason ->
                        ReasonRow(reason, maxSeconds)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasonRow(reason: ReasonStat, maxSeconds: Long) {
    val colors = MaterialTheme.colorScheme
    val fraction = (reason.seconds.toFloat() / maxSeconds.toFloat()).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = reason.reason,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.stats_reasons_time,
                    formatMinutes(reason.minutes),
                    reason.opens,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.onSurface.copy(alpha = 0.10f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.primary),
            )
        }
    }
}

/** "Xh Ym" once the hour mark is reached, otherwise "Ym". */
@Composable
private fun formatMinutes(minutes: Int): String =
    if (minutes >= 60) {
        stringResource(R.string.stats_minutes_hm, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.stats_minutes_short, minutes)
    }

@Composable
private fun SummaryTile(modifier: Modifier = Modifier, value: String, label: String) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun StatRow(stat: DailyStat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stat.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.stats_row_summary, stat.attempts, stat.dismissed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}
