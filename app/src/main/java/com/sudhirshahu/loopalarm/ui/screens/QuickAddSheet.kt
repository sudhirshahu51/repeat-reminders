package com.sudhirshahu.loopalarm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.ScheduleMode
import com.sudhirshahu.loopalarm.ui.components.IntervalPicker
import com.sudhirshahu.loopalarm.ui.components.TimePickerDialog
import com.sudhirshahu.loopalarm.ui.components.WeekDayChips
import com.sudhirshahu.loopalarm.util.Fmt
import java.time.LocalTime

/** Quick steps in order: name, interval, start time, days, and an optional picture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    use24: Boolean,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onMoreOptions: (Alarm) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute + 1 } % 1440
    var alarm by remember { mutableStateOf(Alarm(startMinute = nowMinute, scheduleMode = ScheduleMode.WEEK_DAYS)) }
    var pickTime by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Quick add", style = MaterialTheme.typography.headlineSmall)

            Text("1. Name", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = alarm.name,
                onValueChange = { alarm = alarm.copy(name = it) },
                placeholder = { Text("e.g. Drink water, Stretch, Medicine") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("2. Repeat interval", style = MaterialTheme.typography.labelLarge)
            IntervalPicker(alarm.intervalValue, alarm.intervalUnit) { v, u -> alarm = alarm.copy(intervalValue = v, intervalUnit = u) }

            Text("3. Start time", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { pickTime = true }) {
                Text(Fmt.minuteOfDay(alarm.startMinute, use24), style = MaterialTheme.typography.titleLarge)
            }
            Text("Repeats until midnight. Set an end time or ring count under More options.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("4. Days", style = MaterialTheme.typography.labelLarge)
            WeekDayChips(alarm.daysOfWeek) { alarm = alarm.copy(daysOfWeek = it) }

            PictureSection(alarm.imageFile, title = "5. Picture (optional)", inCard = false) { alarm = alarm.copy(imageFile = it) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                OutlinedButton(onClick = { onMoreOptions(alarm) }, modifier = Modifier.weight(1f)) { Text("More options") }
                Button(onClick = { onSave(alarm) }, enabled = alarm.daysOfWeek != 0, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
    if (pickTime) {
        TimePickerDialog("Start time", alarm.startMinute, use24, { pickTime = false }) {
            alarm = alarm.copy(startMinute = it)
            pickTime = false
        }
    }
}
