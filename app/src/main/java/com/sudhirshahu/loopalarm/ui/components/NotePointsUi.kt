package com.sudhirshahu.loopalarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sudhirshahu.loopalarm.data.NotePoints

/**
 * Shows notes as their list: bullets, numbers or checkboxes in front of each point.
 * With [onChange], checklist boxes can be ticked and the new notes text is handed back.
 */
@Composable
fun NotePointsView(
    notes: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    colour: Color = MaterialTheme.colorScheme.onSurface,
    markerColour: Color = MaterialTheme.colorScheme.primary,
    spacing: Int = 8,
    onChange: ((String) -> Unit)? = null,
) {
    val parsed = remember(notes) { NotePoints.parse(notes) }
    val points = parsed.points.filter { it.text.isNotBlank() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
        points.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.Top) {
                if (parsed.style == NotePoints.Style.CHECKLIST) {
                    val toggle = onChange?.let { cb ->
                        {
                            val updated = points.mapIndexed { j, q -> if (j == i) q.copy(checked = !q.checked) else q }
                            cb(NotePoints.format(NotePoints.Notes(parsed.style, updated)))
                        }
                    }
                    Icon(
                        if (p.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        if (p.checked) "Done" else "Not done",
                        Modifier.size(22.dp).then(if (toggle != null) Modifier.clickable(onClick = toggle) else Modifier),
                        tint = markerColour,
                    )
                } else if (parsed.style == NotePoints.Style.BULLETS) {
                    BulletDot(style, markerColour)
                } else {
                    Text(
                        NotePoints.marker(parsed.style, i, p.checked), style = style, color = markerColour,
                        fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp),
                    )
                }
                Text(
                    p.text, style = style,
                    color = if (p.checked) colour.copy(alpha = 0.55f) else colour,
                    textDecoration = if (p.checked) TextDecoration.LineThrough else null,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * A round bullet sized to the text (the "•" character is tiny at these sizes), centred on the first line so it
 * lines up with the text when a point wraps.
 */
@Composable
private fun BulletDot(style: TextStyle, colour: Color) {
    val density = LocalDensity.current
    val line = with(density) { (if (style.lineHeight.isSp) style.lineHeight else style.fontSize * 1.4f).toDp() }
    val dot = with(density) { (style.fontSize * 0.42f).toDp() }
    Box(Modifier.width(18.dp).height(line), contentAlignment = Alignment.Center) {
        Box(Modifier.size(dot).background(colour, CircleShape))
    }
}

/** A point being edited; [id] keeps each row's text field and focus while points are added or removed. */
private class EditPoint(val id: Int, text: String, checked: Boolean) {
    var text by mutableStateOf(text)
    var checked by mutableStateOf(checked)
}

/**
 * Notes as a list of points, like OneNote: choose bullets, numbers or a checklist; Enter starts the next point,
 * ✕ removes one, and pasted lines become separate points. [onChange] gets the stored notes text.
 */
@Composable
fun NotesEditor(notes: String, onChange: (String) -> Unit) {
    val initial = remember { NotePoints.parse(notes) }
    var style by remember { mutableStateOf(initial.style) }
    var nextId by remember { mutableStateOf(0) }
    fun newPoint(text: String = "", checked: Boolean = false) = EditPoint(nextId++, text, checked)
    val points = remember {
        mutableStateListOf<EditPoint>().apply {
            initial.points.forEach { add(newPoint(it.text, it.checked)) }
            if (isEmpty()) add(newPoint())
        }
    }
    var focusId by remember { mutableStateOf<Int?>(null) }
    fun publish() = onChange(NotePoints.format(NotePoints.Notes(style, points.map { NotePoints.Point(it.text, it.checked) })))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val styles = NotePoints.Style.entries
            styles.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = style == s,
                    onClick = { style = s; publish() },
                    shape = SegmentedButtonDefaults.itemShape(i, styles.size),
                    icon = {
                        Icon(
                            when (s) {
                                NotePoints.Style.BULLETS -> Icons.AutoMirrored.Filled.FormatListBulleted
                                NotePoints.Style.NUMBERS -> Icons.Filled.FormatListNumbered
                                NotePoints.Style.CHECKLIST -> Icons.Outlined.Checklist
                            },
                            null, Modifier.size(18.dp),
                        )
                    },
                ) { Text(s.label, maxLines = 1) }
            }
        }
        points.forEachIndexed { index, point ->
            val focus = remember(point.id) { FocusRequester() }
            LaunchedEffect(focusId) { if (focusId == point.id) { focus.requestFocus(); focusId = null } }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (style == NotePoints.Style.CHECKLIST) {
                    Icon(
                        if (point.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        if (point.checked) "Done" else "Not done",
                        Modifier.size(24.dp).clickable { point.checked = !point.checked; publish() },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else if (style == NotePoints.Style.BULLETS) {
                    BulletDot(MaterialTheme.typography.bodyLarge, MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        NotePoints.marker(style, index, false), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp),
                    )
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    BasicTextField(
                        value = point.text,
                        onValueChange = { v ->
                            if ('\n' in v) {
                                // Pasted several lines: one point each.
                                val parts = v.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                                point.text = parts.firstOrNull().orEmpty()
                                val more = parts.drop(1).map { newPoint(it) }
                                points.addAll(index + 1, more)
                                more.lastOrNull()?.let { focusId = it.id }
                            } else {
                                point.text = v
                            }
                            publish()
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = {
                            val p = newPoint()
                            points.add(index + 1, p)
                            focusId = p.id
                        }),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).focusRequester(focus),
                        decorationBox = { inner ->
                            if (point.text.isEmpty()) {
                                Text(
                                    if (index == 0) "Write a point, e.g. Take with water" else "Next point",
                                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                    HorizontalDivider()
                }
                IconButton(
                    onClick = {
                        if (points.size > 1) points.removeAt(index) else point.text = ""
                        publish()
                    },
                ) { Icon(Icons.Filled.Close, "Remove point", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        TextButton(onClick = {
            val p = newPoint()
            points.add(p)
            focusId = p.id
        }) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Text("  Add point")
        }
    }
}
