package com.sudhirshahu.loopalarm.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudhirshahu.loopalarm.R
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.ImageStore
import com.sudhirshahu.loopalarm.data.SoundType
import com.sudhirshahu.loopalarm.schedule.ScheduleCalculator
import com.sudhirshahu.loopalarm.ui.components.NotePointsView
import com.sudhirshahu.loopalarm.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Everything about one reminder: bell or emoji and start time, then the title and notes across the full width,
 * schedule, group, next ring, every picture and how it rings. Shared by the details page (tap a card) and the alarm screen, so a ringing reminder looks the same.
 * Not scrollable itself; the caller scrolls it.
 *
 * [ringing] drops the parts that make no sense while it rings (next ring, on / off state).
 * With [onNotesChange], checklist points can be ticked off here.
 */
@Composable
internal fun ReminderDetailsContent(
    a: Alarm,
    use24: Boolean,
    ringing: Boolean = false,
    modifier: Modifier = Modifier,
    onNotesChange: ((String) -> Unit)? = null,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (!ringing) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(15_000)
                now = System.currentTimeMillis()
            }
        }
    }
    val colour = reminderColour(a)
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Top: bell (or emoji) and the start time, as on the reminder card
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(colour.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                if (a.icon.isBlank()) Image(painterResource(R.drawable.ic_bell_badge), null, Modifier.size(36.dp))
                else Text(a.icon, fontSize = 30.sp)
            }
            Text(
                Fmt.minuteOfDay(a.startMinute, use24),
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 14.dp),
            )
        }

        // Below, the full width for the title and the description (the notes, as their bullet / number / checklist points)
        Column(Modifier.fillMaxWidth()) {
            Text(a.displayName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            if (a.notes.isNotBlank()) NotePointsView(a.notes, Modifier.padding(top = 10.dp), onChange = onNotesChange)
        }

        DetailsCard("Schedule") {
            DetailRow(Icons.Outlined.Repeat, Fmt.summary(a, use24), MaterialTheme.colorScheme.onSurface)
            DetailRow(Icons.Outlined.CalendarMonth, Fmt.schedule(a), MaterialTheme.colorScheme.onSurface)
            if (a.groupName.isNotBlank()) DetailRow(Icons.Outlined.Folder, "Group: ${a.groupName}", MaterialTheme.colorScheme.onSurface)
            if (!ringing) {
                if (a.enabled) {
                    val next = remember(a, now) { ScheduleCalculator.nextIncludingSnooze(a, now) }
                    val c = nextRingColour()
                    Row(
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(c, CircleShape))
                        Text(nextRingLabel(a, next, now, use24), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium, color = c, modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    Text("Turned off", style = MaterialTheme.typography.bodyMedium, color = soft, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        a.images.forEach { name -> DetailsPicture(name) }

        DetailsCard("When it rings") {
            InfoRow("Sound", if (a.soundType == SoundType.SILENT) "Silent" else a.soundTitle.ifBlank { "Default" })
            if (a.soundType != SoundType.SILENT) {
                InfoRow("Volume", "${a.volumePercent}%" + if (a.gradualVolume) ", rising over ${Fmt.duration(a.gradualSec)}" else "")
            }
            InfoRow("Vibrate", if (a.vibrate) "Yes" else "No")
            InfoRow("Rings for", Fmt.duration(a.ringDurationSec))
            InfoRow("Snooze", "${a.snoozeMinutes} min")
            InfoRow("Alarm screen", if (a.showPostScreen) "Opens when it rings" else "Notification only")
        }
    }
}

@Composable
private fun DetailsCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun DetailsPicture(name: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { ImageStore.load(context, name)?.asImageBitmap() }
    }
    bitmap?.let {
        Image(it, "Reminder picture", contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)))
    }
}

/** The page a reminder card opens: its full details, with Edit at the top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailsScreen(
    a: Alarm,
    use24: Boolean,
    onBack: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onRingNow: (Alarm) -> Unit,
    onDuplicate: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onNotesChange: (Alarm, String) -> Unit = { _, _ -> },
) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Switch(checked = a.enabled, onCheckedChange = { onToggle(a, it) }, modifier = Modifier.padding(end = 8.dp))
                    FilledTonalButton(onClick = { onEdit(a) }) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                        Text("Edit", modifier = Modifier.padding(start = 6.dp))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Test ring now") }, onClick = { menu = false; onRingNow(a) })
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate(a) })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete(a) })
                        }
                    }
                },
            )
        },
    ) { pad ->
        ReminderDetailsContent(
            a, use24,
            onNotesChange = { onNotesChange(a, it) },
            modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
        )
    }
}
