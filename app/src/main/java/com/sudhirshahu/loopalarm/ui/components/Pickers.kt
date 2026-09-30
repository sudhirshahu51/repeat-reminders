package com.sudhirshahu.loopalarm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sudhirshahu.loopalarm.data.IntervalUnit
import com.sudhirshahu.loopalarm.util.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    minuteOfDay: Int,
    is24h: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24h)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Number field plus unit dropdown, with one-tap presets. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IntervalPicker(value: Int, unit: IntervalUnit, onChange: (Int, IntervalUnit) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Every", modifier = Modifier.padding(end = 12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                text = t.filter(Char::isDigit).take(4)
                text.toIntOrNull()?.takeIf { it > 0 }?.let { onChange(it, unit) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp),
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(start = 12.dp)) {
            OutlinedTextField(
                value = when (unit) {
                    IntervalUnit.SECONDS -> "seconds"
                    IntervalUnit.MINUTES -> "minutes"
                    IntervalUnit.HOURS -> "hours"
                },
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).width(150.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                IntervalUnit.entries.forEach { u ->
                    DropdownMenuItem(
                        text = { Text(u.name.lowercase()) },
                        onClick = { expanded = false; onChange(value, u) },
                    )
                }
            }
        }
    }
    if (unit == IntervalUnit.SECONDS && value < 10) {
        Text("Shortest interval is 10 seconds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    val presets = listOf(
        1 to IntervalUnit.MINUTES, 5 to IntervalUnit.MINUTES, 10 to IntervalUnit.MINUTES, 15 to IntervalUnit.MINUTES,
        30 to IntervalUnit.MINUTES, 45 to IntervalUnit.MINUTES, 1 to IntervalUnit.HOURS, 2 to IntervalUnit.HOURS, 3 to IntervalUnit.HOURS,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        presets.forEach { (v, u) ->
            FilterChip(
                selected = v == value && u == unit,
                onClick = { onChange(v, u) },
                label = { Text(if (u == IntervalUnit.HOURS) "${v}h" else "${v}m") },
            )
        }
    }
}

/** Seven toggle chips, Monday first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekDayChips(mask: Int, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Fmt.DAY_SHORT.forEachIndexed { i, label ->
            FilterChip(
                selected = (mask shr i) and 1 == 1,
                onClick = { onChange(mask xor (1 shl i)) },
                label = { Text(label) },
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { onChange(0b1111111) }) { Text("Every day") }
        TextButton(onClick = { onChange(0b0011111) }) { Text("Weekdays") }
        TextButton(onClick = { onChange(0b1100000) }) { Text("Weekends") }
    }
}
