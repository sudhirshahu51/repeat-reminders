package com.sudhirshahu.loopalarm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sudhirshahu.loopalarm.data.HistoryEntry
import com.sudhirshahu.loopalarm.data.Outcome
import com.sudhirshahu.loopalarm.util.Fmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(history: List<HistoryEntry>, use24: Boolean, contentPadding: PaddingValues, onClear: () -> Unit) {
    val byDay = history.groupBy { Instant.ofEpochMilli(it.firedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
    LazyColumn(Modifier.fillMaxSize().padding(contentPadding), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Last 90 days", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        if (history.isEmpty()) {
            item {
                Text(
                    "Alarms that ring will be listed here, with whether they were dismissed, snoozed or stopped on their own.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        byDay.forEach { (day, entries) ->
            item(key = "h$day") {
                Text(
                    day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(entries, key = { it.id }) { e ->
                ListItem(
                    leadingContent = { Icon(icon(e.outcome), null) },
                    headlineContent = { Text(e.alarmName) },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(e.outcome.label)
                            val late = (e.firedAt - e.scheduledAt) / 1000
                            if (late > 60) Text("Scheduled ${Fmt.time(e.scheduledAt, use24)}, rang $late s late", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    trailingContent = { Text(Fmt.timeWithSeconds(e.firedAt, use24)) },
                )
            }
        }
    }
}

private fun icon(o: Outcome) = when (o) {
    Outcome.RINGING -> Icons.Filled.NotificationsActive
    Outcome.DISMISSED -> Icons.Filled.CheckCircle
    Outcome.SNOOZED -> Icons.Filled.Snooze
    Outcome.TIMED_OUT -> Icons.Filled.TimerOff
    Outcome.VIBRATED_IN_CALL, Outcome.SKIPPED_IN_CALL -> Icons.Filled.PhoneInTalk
    Outcome.REPLACED -> Icons.Filled.SwapHoriz
}
