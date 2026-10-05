package com.sudhirshahu.loopalarm.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.Birthday
import com.sudhirshahu.loopalarm.data.BirthdaySources
import com.sudhirshahu.loopalarm.ui.components.TimePickerDialog
import com.sudhirshahu.loopalarm.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** File types accepted from the picker. Some apps label .ics/.vcf files as plain text or binary. */
private val IMPORT_TYPES = arrayOf(
    "text/calendar", "text/x-vcalendar", "text/vcard", "text/x-vcard", "text/directory", "text/plain", "application/octet-stream",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaysScreen(
    existing: List<Alarm>,
    use24: Boolean,
    /** a file shared to the app or opened with it, imported straight away */
    sharedFile: Uri?,
    onSharedFileHandled: () -> Unit,
    onBack: () -> Unit,
    onAdd: (List<Alarm>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Found birthdays with whether each is ticked.
    var found by remember { mutableStateOf<List<Pair<Birthday, Boolean>>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var minute by remember { mutableIntStateOf(9 * 60) }
    var pickTime by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf("Birthdays") }
    val existingNames = remember(existing) { existing.map { it.name.lowercase() }.toSet() }
    fun alreadyAdded(b: Birthday) = "${b.name}'s birthday".lowercase() in existingNames

    fun show(list: List<Pair<Birthday, Boolean>>, source: String) {
        val merged = (found + list).distinctBy { it.first.key }
        found = merged.map { (b, on) -> b to (on && !alreadyAdded(b)) }
        status = if (list.isEmpty()) "No birthdays found in $source." else "Found ${list.size} in $source."
    }

    fun readFile(uri: Uri) {
        status = "Reading file…"
        scope.launch {
            val list = withContext(Dispatchers.IO) { BirthdaySources.fromFile(context, uri) }
            show(list.map { it to true }, "the file")
        }
    }

    fun readCalendars() {
        status = "Reading calendars…"
        scope.launch {
            val list = withContext(Dispatchers.IO) { BirthdaySources.fromCalendars(context) }
            show(list, "your calendars")
        }
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) readCalendars() else status = "Calendar permission is needed to read birthdays from your calendars."
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) readFile(uri) }

    LaunchedEffect(sharedFile) {
        if (sharedFile != null) {
            readFile(sharedFile)
            onSharedFileHandled()
        }
    }

    val selected = found.filter { it.second }.map { it.first }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import birthdays") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (found.isNotEmpty()) {
                Button(
                    onClick = { onAdd(selected.map { it.toAlarm(minute, group.ifBlank { "Birthdays" }) }) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                ) { Text("Add ${selected.size} birthday reminder${if (selected.size == 1) "" else "s"}") }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("From phone calendars", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Reads birthday events and yearly events from calendars synced to this phone, such as your Google Calendar. " +
                                "Nothing leaves the phone.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                            if (granted) readCalendars() else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                        }) { Text("Read calendars") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("From a file (Google Contacts, Facebook, other apps)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Facebook and most social media apps don't let other apps read birthdays directly. If an app or website " +
                                "can export them as a calendar (.ics) or contacts (.vcf) file, choose that file here, or share it to " +
                                "Repeat Reminders from the other app.\n\n" +
                                "Google Contacts: open contacts.google.com, select contacts, then Export as vCard.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { filePicker.launch(IMPORT_TYPES) }) { Text("Choose .ics or .vcf file") }
                    }
                }
            }
            status?.let { s -> item { Text(s, style = MaterialTheme.typography.bodyMedium) } }

            if (found.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Remind me at", modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = { pickTime = true }) { Text(Fmt.minuteOfDay(minute, use24)) }
                            }
                            OutlinedTextField(
                                value = group, onValueChange = { group = it.take(40) }, singleLine = true,
                                label = { Text("Group") }, modifier = Modifier.fillMaxWidth(),
                            )
                            Row {
                                TextButton(onClick = { found = found.map { (b, _) -> b to !alreadyAdded(b) } }) { Text("Select all") }
                                TextButton(onClick = { found = found.map { (b, _) -> b to false } }) { Text("Select none") }
                            }
                        }
                    }
                }
                items(found, key = { it.first.key }) { (b, on) ->
                    val added = alreadyAdded(b)
                    ListItem(
                        headlineContent = { Text(b.name) },
                        supportingContent = {
                            Text(
                                "${b.day} ${Fmt.MONTH_SHORT[b.month - 1]}" + (b.year?.let { " $it" } ?: "") +
                                    (if (added) " · already added" else "") +
                                    (if (b.month == 2 && b.day == 29) " · rings on 28 Feb" else ""),
                            )
                        },
                        leadingContent = {
                            Checkbox(
                                checked = on, enabled = !added,
                                onCheckedChange = { v -> found = found.map { if (it.first.key == b.key) b to v else it } },
                            )
                        },
                    )
                }
            }
        }
    }

    if (pickTime) TimePickerDialog("Reminder time", minute, use24, { pickTime = false }) { minute = it; pickTime = false }
}
