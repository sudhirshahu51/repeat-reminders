package com.sudhirshahu.loopalarm.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.EndMode
import com.sudhirshahu.loopalarm.data.ImageStore
import com.sudhirshahu.loopalarm.data.ScheduleMode
import com.sudhirshahu.loopalarm.data.SoundType
import com.sudhirshahu.loopalarm.ring.BeepSynth
import com.sudhirshahu.loopalarm.ring.SoundPlayer
import com.sudhirshahu.loopalarm.schedule.ScheduleCalculator
import com.sudhirshahu.loopalarm.ui.components.CropDialog
import com.sudhirshahu.loopalarm.ui.components.IntervalPicker
import com.sudhirshahu.loopalarm.ui.components.TimePickerDialog
import com.sudhirshahu.loopalarm.ui.components.WeekDayChips
import com.sudhirshahu.loopalarm.util.Fmt
import com.sudhirshahu.loopalarm.util.ReminderIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    initial: Alarm,
    use24: Boolean,
    /** existing group names, offered as suggestions */
    groups: List<String>,
    onBack: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
) {
    var a by remember(initial) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    val error = validate(a)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial.id == 0L) "New alarm" else "Edit alarm") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (initial.id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    TextButton(onClick = { onSave(a) }, enabled = error == null) { Text("Save") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Name") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconPickerButton(a.icon) { a = a.copy(icon = it) }
                    OutlinedTextField(
                        value = a.name, onValueChange = { a = a.copy(name = it) }, singleLine = true,
                        placeholder = { Text("Alarm name") }, modifier = Modifier.weight(1f),
                    )
                }
                GroupField(a.groupName, groups) { a = a.copy(groupName = it) }
                OutlinedTextField(
                    value = a.notes, onValueChange = { a = a.copy(notes = it) },
                    label = { Text("Notes") }, placeholder = { Text("One per line, e.g. Take with water") },
                    minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth(),
                )
            }
            PictureSection(a.imageFile) { a = a.copy(imageFile = it) }
            Section("Repeat / Single alarm") {
                SwitchRow(
                    if (a.repeating) "Repeat" else "Single alarm",
                    if (a.repeating) "Rings again every interval until the window ends" else "Rings once at the start time",
                    a.repeating,
                ) { a = a.copy(repeating = it) }
                if (a.repeating) IntervalPicker(a.intervalValue, a.intervalUnit) { v, u -> a = a.copy(intervalValue = v, intervalUnit = u) }
            }
            TimeWindowSection(a, use24) { a = it }
            ScheduleSection(a) { a = it }
            SoundSection(a) { a = it }
            RingSection(a) { a = it }
            PreviewSection(a, use24)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = { onSave(a) }, enabled = error == null, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save alarm") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${a.displayName}?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(initial) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun validate(a: Alarm): String? = when {
    a.repeating && a.intervalSeconds < 10 -> "Interval must be at least 10 seconds."
    a.scheduleMode == ScheduleMode.WEEK_DAYS && a.daysOfWeek == 0 -> "Pick at least one day of the week."
    a.scheduleMode == ScheduleMode.MONTH_DAYS && a.daysOfMonth == 0 -> "Pick at least one day of the month."
    a.scheduleMode == ScheduleMode.DATES && a.dateSet.isEmpty() -> "Add at least one date."
    a.repeating && a.endMode == EndMode.AFTER_COUNT && a.repeatCount < 1 -> "Ring count must be at least 1."
    else -> null
}

/** Round button showing the reminder's emoji; opens a picker of common icons plus any emoji you type. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPickerButton(icon: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FilledTonalIconButton(onClick = { open = true }, modifier = Modifier.size(56.dp)) {
        if (icon.isBlank()) Icon(Icons.Filled.Alarm, "Choose icon") else Text(icon, fontSize = 26.sp)
    }
    if (!open) return
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Reminder icon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ReminderIcons.presets.forEach { e ->
                        val selected = e == icon
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { onChange(e); open = false },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 24.sp) }
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = ReminderIcons.firstSymbol(it) },
                    label = { Text("Or type any emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onChange(custom); open = false }, enabled = custom.isNotBlank()) { Text("Use") }
        },
        dismissButton = {
            TextButton(onClick = { onChange(""); open = false }) { Text("Default") }
        },
    )
}

/** Free-text group name with the existing groups as one-tap chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupField(group: String, groups: List<String>, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = group,
        onValueChange = { onChange(it.take(40)) },
        label = { Text("Group (optional)") },
        placeholder = { Text("e.g. Health, Work, Birthdays") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val others = groups.filter { it != group }
    if (others.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            others.forEach { g -> AssistChip(onClick = { onChange(g) }, label = { Text(g) }) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp).weight(1f))
        trailing()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeWindowSection(a: Alarm, use24: Boolean, onChange: (Alarm) -> Unit) {
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    Section("Start time") {
        OutlinedButton(onClick = { pickStart = true }) {
            Text(Fmt.minuteOfDay(a.startMinute, use24), style = MaterialTheme.typography.headlineSmall)
        }
        if (a.repeating) StopRepeatingOptions(a, use24, onChange) { pickEnd = true }
    }
    if (pickStart) TimePickerDialog("Start time", a.startMinute, use24, { pickStart = false }) { onChange(a.copy(startMinute = it)); pickStart = false }
    if (pickEnd) TimePickerDialog("End time", a.endMinute, use24, { pickEnd = false }) { onChange(a.copy(endMinute = it)); pickEnd = false }
}

@Composable
private fun StopRepeatingOptions(a: Alarm, use24: Boolean, onChange: (Alarm) -> Unit, onPickEnd: () -> Unit) {
    Text("Stop repeating", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    RadioRow("At midnight", a.endMode == EndMode.END_OF_DAY, { onChange(a.copy(endMode = EndMode.END_OF_DAY)) })
    RadioRow("At a set time", a.endMode == EndMode.AT_TIME, { onChange(a.copy(endMode = EndMode.AT_TIME)) }) {
        if (a.endMode == EndMode.AT_TIME) TextButton(onClick = onPickEnd) { Text(Fmt.minuteOfDay(a.endMinute, use24)) }
    }
    if (a.endMode == EndMode.AT_TIME && a.endMinute < a.startMinute) {
        Text("Ends after midnight, on the next day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    RadioRow("After a number of rings", a.endMode == EndMode.AFTER_COUNT, { onChange(a.copy(endMode = EndMode.AFTER_COUNT)) }) {
        if (a.endMode == EndMode.AFTER_COUNT) {
            var text by remember(a.repeatCount) { mutableStateOf(a.repeatCount.toString()) }
            OutlinedTextField(
                value = text,
                onValueChange = { t ->
                    text = t.filter(Char::isDigit).take(4)
                    onChange(a.copy(repeatCount = text.toIntOrNull() ?: 0))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(88.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSection(a: Alarm, onChange: (Alarm) -> Unit) {
    var addDate by remember { mutableStateOf(false) }
    var addRange by remember { mutableStateOf(false) }
    Section("Days") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ScheduleMode.DAILY to "Every day",
                ScheduleMode.WEEK_DAYS to "Days of week",
                ScheduleMode.MONTH_DAYS to "Days of month",
                ScheduleMode.DATES to "Calendar dates",
            ).forEach { (mode, label) ->
                FilterChip(selected = a.scheduleMode == mode, onClick = { onChange(a.copy(scheduleMode = mode)) }, label = { Text(label) })
            }
        }
        when (a.scheduleMode) {
            ScheduleMode.DAILY -> Unit
            ScheduleMode.WEEK_DAYS -> WeekDayChips(a.daysOfWeek) { onChange(a.copy(daysOfWeek = it)) }
            ScheduleMode.MONTH_DAYS -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..31).forEach { d ->
                        FilterChip(
                            selected = (a.daysOfMonth shr (d - 1)) and 1 == 1,
                            onClick = { onChange(a.copy(daysOfMonth = a.daysOfMonth xor (1 shl (d - 1)))) },
                            label = { Text(d.toString()) },
                        )
                    }
                }
                Text("Months without the chosen day (e.g. the 31st) are skipped.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ScheduleMode.DATES -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { addDate = true }) { Text("Add date") }
                    OutlinedButton(onClick = { addRange = true }) { Text("Add date range") }
                    if (a.dateSet.isNotEmpty()) TextButton(onClick = { onChange(a.copy(dates = "")) }) { Text("Clear") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    a.dateSet.sorted().forEach { d ->
                        InputChip(
                            selected = false,
                            onClick = { onChange(a.copy(dates = (a.dateSet - d).joinToString(","))) },
                            label = { Text(LocalDate.parse(d).let { "${it.dayOfMonth} ${Fmt.MONTH_SHORT[it.monthValue - 1]} ${it.year}" }) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }
        if (a.scheduleMode != ScheduleMode.DATES) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Only in these months (none selected = every month)", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Fmt.MONTH_SHORT.forEachIndexed { i, m ->
                    FilterChip(
                        selected = (a.months shr i) and 1 == 1,
                        onClick = { onChange(a.copy(months = a.months xor (1 shl i))) },
                        label = { Text(m) },
                    )
                }
            }
            Text("Only in these years (none selected = every year)", style = MaterialTheme.typography.labelLarge)
            val thisYear = LocalDate.now().year
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (thisYear..thisYear + 4).forEach { y ->
                    val on = y in a.yearSet
                    FilterChip(
                        selected = on,
                        onClick = { onChange(a.copy(years = (if (on) a.yearSet - y else a.yearSet + y).sorted().joinToString(","))) },
                        label = { Text(y.toString()) },
                    )
                }
            }
        }
    }

    if (addDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { addDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(a.copy(dates = (a.dateSet + utcDate(it).toString()).joinToString(","))) }
                    addDate = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    if (addRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { addRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis ?: s
                    if (s != null && e != null) {
                        val days = generateSequence(utcDate(s)) { it.plusDays(1) }.takeWhile { !it.isAfter(utcDate(e)) }.take(1000)
                        onChange(a.copy(dates = (a.dateSet + days.map { it.toString() }).joinToString(",")))
                    }
                    addRange = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addRange = false }) { Text("Cancel") } },
        ) { DateRangePicker(state = state, modifier = Modifier.height(500.dp)) }
    }
}

/** Material date pickers report UTC midnight. */
private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PictureSection(imageFile: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Picture waiting in the crop screen: a newly picked photo, or the current one when re-cropping.
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    fun openCrop(load: () -> Bitmap?) {
        busy = true
        failed = false
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { load() }
            busy = false
            if (bmp != null) cropSource = bmp else failed = true
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) openCrop { ImageStore.decodeUpright(context, uri) }
    }
    val bitmap by produceState<ImageBitmap?>(null, imageFile) {
        value = withContext(Dispatchers.IO) { ImageStore.load(context, imageFile, 720)?.asImageBitmap() }
    }

    Section("Picture") {
        bitmap?.let {
            Image(
                it, "Reminder picture", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !busy,
            ) { Text(if (busy) "Opening…" else if (imageFile.isBlank()) "Add picture" else "Change picture") }
            if (imageFile.isNotBlank()) {
                OutlinedButton(onClick = { openCrop { ImageStore.load(context, imageFile) } }, enabled = !busy) { Text("Crop") }
                TextButton(onClick = { onChange("") }) { Text("Remove") }
            }
        }
        if (failed) Text("Couldn't open that picture. Try another one.", color = MaterialTheme.colorScheme.error)
        Text(
            "Shown in the notification and on the alarm screen when this reminder rings. Wide pictures fit notifications best.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    cropSource?.let { src ->
        CropDialog(
            src,
            onCancel = { cropSource = null },
            onDone = { rect ->
                cropSource = null
                busy = true
                scope.launch {
                    val name = withContext(Dispatchers.IO) { ImageStore.saveCropped(context, src, rect) }
                    busy = false
                    if (name != null) onChange(name) else failed = true
                }
            },
        )
    }
}

@Composable
private fun SoundSection(a: Alarm, onChange: (Alarm) -> Unit) {
    val context = LocalContext.current
    val preview = remember { SoundPlayer(context) }
    var playing by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
    LaunchedEffect(playing) {
        if (playing != null) {
            delay(4000)
            preview.stop()
            playing = null
        }
    }
    fun togglePreview(key: String, type: SoundType, value: String) {
        if (playing == key) {
            preview.stop(); playing = null
        } else {
            preview.start(type, value, a.volumePercent / 100f); playing = key
        }
    }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            ?: return@rememberLauncherForActivityResult
        val title = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: "Ringtone"
        onChange(a.copy(soundType = SoundType.RINGTONE, soundValue = uri.toString(), soundTitle = title))
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "Audio file"
        onChange(a.copy(soundType = SoundType.FILE, soundValue = uri.toString(), soundTitle = name))
    }

    Section("Sound") {
        Text("Built-in beeps", style = MaterialTheme.typography.labelLarge)
        BeepSynth.all.forEach { b ->
            val selected = a.soundType == SoundType.BEEP && a.soundValue == b.id
            RadioRow(b.title, selected, { onChange(a.copy(soundType = SoundType.BEEP, soundValue = b.id, soundTitle = b.title)) }) {
                IconButton(onClick = { togglePreview(b.id, SoundType.BEEP, b.id) }) {
                    Icon(if (playing == b.id) Icons.Filled.Stop else Icons.Filled.PlayArrow, "Preview")
                }
            }
        }
        HorizontalDivider()
        val custom = a.soundType == SoundType.RINGTONE || a.soundType == SoundType.FILE
        if (custom) {
            RadioRow(a.soundTitle, true, {}) {
                IconButton(onClick = { togglePreview("custom", a.soundType, a.soundValue) }) {
                    Icon(if (playing == "custom") Icons.Filled.Stop else Icons.Filled.PlayArrow, "Preview")
                }
            }
        }
        RadioRow("Silent (vibrate only)", a.soundType == SoundType.SILENT, {
            onChange(a.copy(soundType = SoundType.SILENT, soundValue = "", soundTitle = "Silent", vibrate = true))
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val existing = if (a.soundType == SoundType.RINGTONE) Uri.parse(a.soundValue) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ringtonePicker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing),
                )
            }) { Text("Phone ringtones") }
            OutlinedButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) { Text("Music / audio file") }
        }
        Text(
            "Music / audio file opens the system file picker, which lists songs on the phone and in apps that share " +
                "their files (Files, Downloads, SD card). Streaming apps such as YouTube Music and Spotify do not share " +
                "their songs, so save the song as a file first.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RingSection(a: Alarm, onChange: (Alarm) -> Unit) {
    Section("Ringing") {
        Text("Ring for ${Fmt.duration(a.ringDurationSec)}", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 10, 15, 30, 60, 120, 300, 600).forEach { s ->
                FilterChip(selected = a.ringDurationSec == s, onClick = { onChange(a.copy(ringDurationSec = s)) }, label = { Text(Fmt.duration(s)) })
            }
        }
        var custom by remember(a.ringDurationSec) { mutableStateOf(a.ringDurationSec.toString()) }
        OutlinedTextField(
            value = custom,
            onValueChange = { t ->
                custom = t.filter(Char::isDigit).take(4)
                custom.toIntOrNull()?.let { onChange(a.copy(ringDurationSec = it.coerceIn(5, 3600))) }
            },
            label = { Text("Custom (seconds, 5 to 3600)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Text("Volume ${a.volumePercent}%", style = MaterialTheme.typography.labelLarge)
        Slider(value = a.volumePercent.toFloat(), onValueChange = { onChange(a.copy(volumePercent = it.toInt())) }, valueRange = 5f..100f)

        SwitchRow("Gradually increase volume", "Starts quiet and rises to the set volume", a.gradualVolume) { onChange(a.copy(gradualVolume = it)) }
        if (a.gradualVolume) {
            Text("Reach full volume in ${a.gradualSec} sec", style = MaterialTheme.typography.bodyMedium)
            Slider(value = a.gradualSec.toFloat(), onValueChange = { onChange(a.copy(gradualSec = it.toInt())) }, valueRange = 3f..120f)
        }
        SwitchRow("Vibrate", checked = a.vibrate) { onChange(a.copy(vibrate = it)) }
        SwitchRow("Show post-alarm screen", "Full-screen view with Snooze and Dismiss. Off = notification only.", a.showPostScreen) {
            onChange(a.copy(showPostScreen = it))
        }
        Text("Snooze for", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 3, 5, 10, 15, 30).forEach { m ->
                FilterChip(selected = a.snoozeMinutes == m, onClick = { onChange(a.copy(snoozeMinutes = m)) }, label = { Text("$m min") })
            }
        }
    }
}

@Composable
private fun PreviewSection(a: Alarm, use24: Boolean) {
    val upcoming = remember(a) { if (validate(a) == null) ScheduleCalculator.upcoming(a, System.currentTimeMillis(), 6) else emptyList() }
    Section("Next rings") {
        if (upcoming.isEmpty()) {
            Text("No rings in the next five years with these settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                upcoming.forEach { Text(Fmt.dateTime(it, use24), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
