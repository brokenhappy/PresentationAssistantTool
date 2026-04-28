package com.woutwerkman.pa.ui.expanded

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.repository.BulletPointStats
import com.woutwerkman.pa.ui.components.TimerDisplay
import com.woutwerkman.pa.ui.components.formatTimer
import com.woutwerkman.pa.ui.components.formatTimestamp

@Composable
fun ExpandedView(
    state: PresentationState,
    onEvent: (PresentationEvent) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (state.profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No presentation loaded", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Surface
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                    Text("← Back")
                }
            }
            if (state.isActive) {
                ActiveHeader(state)
            } else {
                StatsHeader(state.stats)
            }

            HorizontalDivider()

            BulletPointList(
                state = state,
                onBulletPointClick = { index -> onEvent(PresentationEvent.GoTo(index)) },
            )

            if (!state.isActive && state.runs.isNotEmpty()) {
                HorizontalDivider()
                RunHistorySection(
                    runs = state.runs,
                    onToggleInclusion = { runId -> onEvent(PresentationEvent.ToggleRunInclusion(runId)) },
                )
            }
        }
    }
}

@Composable
private fun ActiveHeader(state: PresentationState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.profile?.title ?: "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TimerDisplay(elapsedMs = state.elapsedMs)
    }
}

@Composable
private fun StatsHeader(stats: BulletPointStats) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "Statistics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        if (stats.lastRunTotal == null) {
            Text(
                "No runs recorded yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Last run", formatTimer(stats.lastRunTotal!!))
                StatItem("Average", formatTimer(stats.totalAverage))
                StatItem("Last 3 avg", formatTimer(stats.totalLastThreeAverage))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColumnScope.BulletPointList(
    state: PresentationState,
    onBulletPointClick: (Int) -> Unit,
) {
    val profile = state.profile ?: return
    val listState = rememberLazyListState()

    LaunchedEffect(state.currentBulletIndex, state.isActive) {
        if (state.isActive) {
            val scrollTo = maxOf(0, state.currentBulletIndex - 3)
            listState.animateScrollToItem(scrollTo)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(profile.orderedKeys) { index, key ->
            val text = profile.bulletPoints[key] ?: ""
            val isPast = state.isActive && index < state.currentBulletIndex
            val isCurrent = state.isActive && index == state.currentBulletIndex

            BulletPointRow(
                index = index,
                text = text,
                isPast = isPast,
                isCurrent = isCurrent,
                onClick = { onBulletPointClick(index) },
            )
        }
    }
}

@Composable
private fun BulletPointRow(
    index: Int,
    text: String,
    isPast: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        when {
            isCurrent -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.background
        }
    )
    val textAlpha = if (isPast) 0.4f else 1f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                modifier = Modifier.width(28.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun RunHistorySection(
    runs: List<RunRecord>,
    onToggleInclusion: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(if (expanded) "Hide run history" else "Show run history (${runs.size} runs)")
        }

        if (expanded) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                itemsIndexed(runs.sortedByDescending { it.timestamp }) { _, run ->
                    RunListItem(run = run, onToggleInclusion = { onToggleInclusion(run.id) })
                }
            }
        }
    }
}

@Composable
private fun RunListItem(
    run: RunRecord,
    onToggleInclusion: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = run.isIncludedInStats,
            onCheckedChange = { onToggleInclusion() },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatTimer(run.totalDuration),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatTimestamp(run.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

