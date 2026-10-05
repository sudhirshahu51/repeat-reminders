package com.sudhirshahu.loopalarm.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onToggleGroup: (String, Boolean) -> Unit,
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
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }

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
            // Ungrouped reminders first, then each group under its own header.
            alarms.groupBy { it.groupName.trim() }.toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it }).forEach { (group, members) ->
                if (group.isNotEmpty()) {
                    item(key = "group:$group") {
                        GroupHeader(
                            group, members.size, members.any { it.enabled }, group in collapsed,
                            onToggleCollapsed = { collapsed = if (group in collapsed) collapsed - group else collapsed + group },
                            onToggle = { onToggleGroup(group, it) },
                        )
                    }
                }
                if (group.isEmpty() || group !in collapsed) {
                    items(members, key = { it.id }) { a ->
                        AlarmCard(a, nextByAlarm[a.id], now, use24, onToggle, onEdit, onDuplicate, onDelete, onRingNow)
                    }
                }
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
private fun GroupHeader(
    name: String,
    count: Int,
    anyEnabled: Boolean,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggleCollapsed).padding(top = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, if (collapsed) "Expand" else "Collapse")
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("$count reminder${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // On when any reminder in the group is on; switching it sets all of them.
        Switch(checked = anyEnabled, onCheckedChange = onToggle, modifier = Modifier.padding(end = 4.dp))
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
            Box(
                Modifier.padding(end = 12.dp).size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (a.icon.isBlank()) Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                else Text(a.icon, fontSize = 22.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(Fmt.minuteOfDay(a.startMinute, use24), style = MaterialTheme.typography.headlineMedium, color = dim)
                Text(a.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = dim)
                Text(Fmt.summary(a, use24), style = MaterialTheme.typography.bodyMedium, color = dim)
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
