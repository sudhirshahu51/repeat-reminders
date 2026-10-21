package com.sudhirshahu.loopalarm.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sudhirshahu.loopalarm.R
import com.sudhirshahu.loopalarm.data.AppSettings
import com.sudhirshahu.loopalarm.util.Fmt
import kotlin.math.roundToInt

private val RampChoices = listOf(3, 5, 10, 20, 30)
private val RingChoices = listOf(5, 15, 30, 60)

/**
 * How new reminders ring: volume, whether it starts quiet and rises, how fast, and how long each ring lasts.
 * Used on the first-launch screen and in Settings. [onChange] gets a transform, like the Settings screen's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundDefaultsSection(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    // The slider moves a local value and saves when the finger lifts, so dragging doesn't write settings every frame.
    var volume by remember(settings.defaultVolumePercent) { mutableFloatStateOf(settings.defaultVolumePercent.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Volume ${volume.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = volume, onValueChange = { volume = it },
            onValueChangeFinished = { onChange { it.copy(defaultVolumePercent = volume.roundToInt()) } },
            valueRange = 5f..100f,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Gradually increase volume", style = MaterialTheme.typography.bodyLarge)
                Text("Starts quiet and rises to the volume above", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings.defaultGradual, onCheckedChange = { v -> onChange { it.copy(defaultGradual = v) } })
        }
        if (settings.defaultGradual) {
            Text("Reach full volume in", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RampChoices.forEach { n ->
                    FilterChip(
                        selected = settings.defaultGradualSec == n,
                        onClick = { onChange { it.copy(defaultGradualSec = n) } },
                        label = { Text(Fmt.duration(n)) },
                    )
                }
            }
        }
        Text("Each ring lasts", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RingChoices.forEach { n ->
                FilterChip(
                    selected = settings.defaultRingSec == n,
                    onClick = { onChange { it.copy(defaultRingSec = n) } },
                    label = { Text(Fmt.duration(n)) },
                )
            }
        }
    }
}

/**
 * Shown once, the first time the app opens: how should reminders ring? Nothing is installed-time on Android, so this
 * is the first thing a new user sees. The starting choices are 25% volume, rising over 5 seconds.
 */
@Composable
fun FirstRunSetup(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, onDone: () -> Unit) {
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(painterResource(R.drawable.ic_bell_badge), null, Modifier.size(72.dp))
            Text("Welcome to Repeat Reminders", style = MaterialTheme.typography.headlineMedium)
            Text(
                "How should your reminders ring? These are the starting values for every new reminder. " +
                    "You can change any reminder later, or these here in Settings.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) { SoundDefaultsSection(settings, onChange) }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Continue") }
        }
    }
}
