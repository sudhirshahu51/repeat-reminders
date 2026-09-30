package com.sudhirshahu.loopalarm.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.schedule.ScheduleCalculator
import com.sudhirshahu.loopalarm.util.Fmt
import kotlinx.coroutines.delay

@Composable
fun AlarmListScreen(
    alarms: List<Alarm>,
    use24: Boolean,
    missingPermissions: Int,
    contentPadding: PaddingValues,
    onToggle: (Alarm, Boolean) -> Unit,
    onEdit: (Alarm) -> Unit,
    onDuplicate: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onRingNow: (Alarm) -> Unit,
    onQuickAdd: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    // Re-evaluate "next ring" times as the clock moves.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val nextByAlarm = remember(alarms, now) {
        alarms.associate { a -> a.id to if (a.enabled) ScheduleCalculator.nextIncludingSnooze(a, now) else null }
    }
    val soonest = alarms.filter { it.enabled }.mapNotNull { a -> nextByAlarm[a.id]?.let { a to it } }.minByOrNull { it.second }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (missingPermissions > 0) {
                item {
                    Card(
                        onClick = onOpenPermissions,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                "$missingPermissions permission${if (missingPermissions > 1) "s" else ""} needed for reliable alarms. Tap to fix.",
                                modifier = Modifier.padding(start = 12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    if (soonest != null) {
                        Text("Next alarm ${Fmt.relative(soonest.second, now)}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${soonest.first.displayName} · ${Fmt.dateTime(soonest.second, use24)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("No upcoming alarms", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (alarms.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No alarms yet", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap Quick add to create a repeating alarm: a name, how often it repeats, when it starts and on which days.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(alarms, key = { it.id }) { a ->
                AlarmCard(a, nextByAlarm[a.id], now, use24, onToggle, onEdit, onDuplicate, onDelete, onRingNow)
            }
        }
        ExtendedFloatingActionButton(
            onClick = onQuickAdd,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Quick add") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun AlarmCard(
    a: Alarm,
    next: Long?,
    now: Long,
    use24: Boolean,
    onToggle: (Alarm, Boolean) -> Unit,
    onEdit: (Alarm) -> Unit,
    onDuplicate: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onRingNow: (Alarm) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val dim = if (a.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Card(Modifier.fillMaxWidth().clickable { onEdit(a) }) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.minuteOfDay(a.startMinute, use24), style = MaterialTheme.typography.headlineMedium, color = dim)
                Text(a.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = dim)
                Text("${Fmt.interval(a)} · ${Fmt.window(a, use24)}", style = MaterialTheme.typography.bodyMedium, color = dim)
                Text(Fmt.schedule(a), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (a.enabled) {
                    val label = when {
                        next == null -> "No upcoming rings for this schedule"
                        a.snoozeUntil > now && a.snoozeUntil == next -> "Snoozed until ${Fmt.time(next, use24)}"
                        else -> "Rings ${Fmt.dateTime(next, use24)} (${Fmt.relative(next, now)})"
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Switch(checked = a.enabled, onCheckedChange = { onToggle(a, it) })
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(a) })
                    DropdownMenuItem(text = { Text("Test ring now") }, onClick = { menu = false; onRingNow(a) })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate(a) })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete(a) })
                }
            }
        }
    }
}
