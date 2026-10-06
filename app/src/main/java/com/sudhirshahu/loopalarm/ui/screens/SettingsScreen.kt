package com.sudhirshahu.loopalarm.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.sudhirshahu.loopalarm.BuildConfig
import com.sudhirshahu.loopalarm.data.Accent
import com.sudhirshahu.loopalarm.data.AppIcon
import com.sudhirshahu.loopalarm.data.AppSettings
import com.sudhirshahu.loopalarm.data.CallBehavior
import com.sudhirshahu.loopalarm.data.ThemeMode
import com.sudhirshahu.loopalarm.data.TimeFormat
import com.sudhirshahu.loopalarm.data.notebook

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    missingPermissions: Int,
    contentPadding: PaddingValues,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Permissions and reliability") },
                supportingContent = {
                    Text(
                        if (missingPermissions == 0) "Everything needed is granted"
                        else "$missingPermissions required permission${if (missingPermissions > 1) "s" else ""} missing",
                    )
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            )
        }

        Group("Theme") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = settings.themeMode == m, onClick = { onChange { it.copy(themeMode = m) } }, label = { Text(m.label) })
                }
            }
            Text("Accent colour", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Accent.entries.forEach { acc ->
                    if (acc == Accent.DYNAMIC && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@forEach
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(40.dp).background(Color(acc.argb), CircleShape)
                                .border(
                                    when {
                                        settings.accent == acc -> 3.dp
                                        acc == Accent.WHITE -> 1.dp // keeps the white swatch visible on a light screen
                                        else -> 0.dp
                                    },
                                    MaterialTheme.colorScheme.onSurface, CircleShape,
                                )
                                .clickable { onChange { it.copy(accent = acc) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (settings.accent == acc) Icon(Icons.Filled.Check, null, tint = if (acc == Accent.WHITE) Color.Black else Color.White)
                        }
                        Text(acc.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Group("Time format") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeFormat.entries.forEach { f ->
                    FilterChip(selected = settings.timeFormat == f, onClick = { onChange { it.copy(timeFormat = f) } }, label = { Text(f.label) })
                }
            }
        }

        Group("Notifications") {
            Toggle("Show next alarm in the notification bar", "Ongoing notification with a countdown and a Skip button",
                settings.showNextAlarmNotification) { v -> onChange { it.copy(showNextAlarmNotification = v) } }
            Toggle("Keep a note after each alarm", "Silent notification saying when the alarm rang and how it ended",
                settings.keepNotificationAfterAlarm) { v -> onChange { it.copy(keepNotificationAfterAlarm = v) } }
            Toggle(
                "Small pop-up instead of the alarm screen",
                "Off: a ringing alarm always opens the app's alarm screen with the full message, picture and notes. " +
                    "On: while you're using the phone, show a small card with Snooze and Dismiss on top of the current app. " +
                    "Both need 'Display over other apps' (Permissions) to open while the phone is unlocked.",
                settings.popupOverApps,
            ) { v -> onChange { it.copy(popupOverApps = v) } }
        }

        Group("App icon") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon.entries.forEach { icon ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                                .background(
                                    // Auto: black in light mode, white in dark mode
                                    if (icon == AppIcon.DEFAULT) Brush.linearGradient(0.5f to Color.Black, 0.5f to Color.White)
                                    else SolidColor(Color(icon.argb)),
                                )
                                .border(if (settings.appIcon == icon) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(14.dp))
                                .clickable { onChange { it.copy(appIcon = icon) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            // The notebook page in the middle follows the accent colour.
                            val page = if (settings.accent.notebook.isEmpty()) Color.White else Color(settings.accent.argb)
                            Box(Modifier.size(22.dp, 26.dp).background(page, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)))
                            if (settings.appIcon == icon) Icon(Icons.Filled.Check, null, tint = if (page == Color.White) Color.Black else Color.White)
                        }
                        Text(icon.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                "Your home screen may take a few seconds to update. If the icon disappears from the home screen, " +
                    "add it again from the app drawer.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Group("During a phone call") {
            CallBehavior.entries.forEach { b ->
                FilterChip(selected = settings.callBehavior == b, onClick = { onChange { it.copy(callBehavior = b) } }, label = { Text(b.label) })
            }
        }

        Group("Do Not Disturb") {
            Toggle(
                "Ring through Do Not Disturb",
                "If DND is set to block alarms, switch it to alarms-only while ringing, then restore it. Needs DND access.",
                settings.overrideDnd,
            ) { v -> onChange { it.copy(overrideDnd = v) } }
        }

        val uri = LocalUriHandler.current
        Group("About") {
            Text("Repeat Reminders ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Text("No accounts, no ads, no tracking. Your alarms never leave your phone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { uri.openUri(PRIVACY_URL) }) { Text("Privacy policy") }
            TextButton(onClick = { uri.openUri("mailto:$SUPPORT_EMAIL") }) { Text("Contact: $SUPPORT_EMAIL") }
        }
    }
}

private const val PRIVACY_URL = "https://nzwqw1y2ia.execute-api.ap-south-1.amazonaws.com/privacy.html"
private const val SUPPORT_EMAIL = "sudhirkumarshahu80@gmail.com"

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
