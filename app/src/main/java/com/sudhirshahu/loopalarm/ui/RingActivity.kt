package com.sudhirshahu.loopalarm.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sudhirshahu.loopalarm.R
import com.sudhirshahu.loopalarm.app
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.AppSettings
import com.sudhirshahu.loopalarm.ring.AlarmService
import com.sudhirshahu.loopalarm.ring.Ringing
import com.sudhirshahu.loopalarm.ring.RingingState
import com.sudhirshahu.loopalarm.ui.screens.ReminderDetailsContent
import com.sudhirshahu.loopalarm.ui.theme.LoopAlarmTheme
import com.sudhirshahu.loopalarm.util.Fmt
import kotlinx.coroutines.delay

/** The alarm screen with the full message, opened when an alarm rings (also over the lock screen). */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Opened from the notification while the small pop-up shows: the pop-up would cover this screen.
        runCatching { startService(AlarmService.actionIntent(this, AlarmService.ACTION_HIDE_POPUP)) }
        enableEdgeToEdge()
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val ringing by RingingState.current.collectAsStateWithLifecycle()
            val r = ringing
            LaunchedEffect(r) { if (r == null) finish() }
            // The ringing reminders themselves, for the full details page.
            val alarms by produceState(emptyList<Alarm>(), r?.alarmIds) {
                value = r?.alarmIds?.takeIf { it.isNotEmpty() }?.let { ids -> app.db.alarmDao().getMany(ids) }.orEmpty()
            }
            LoopAlarmTheme(settings.themeMode, settings.accent) {
                if (r != null) {
                    RingScreen(
                        r, alarms, Fmt.is24h(this, settings.timeFormat),
                        onSnooze = { startService(AlarmService.actionIntent(this, AlarmService.ACTION_SNOOZE)); finish() },
                        onDismiss = { startService(AlarmService.actionIntent(this, AlarmService.ACTION_DISMISS)); finish() },
                    )
                }
            }
        }
    }
}

/**
 * The alarm screen: the same page as the reminder's details (see [ReminderDetailsContent]) for every reminder that is
 * ringing, under the current time, with Snooze and Dismiss always visible at the bottom. [alarms] is empty while they
 * load (or if they were deleted); the short message from [r] is shown then.
 */
@Composable
internal fun RingScreen(r: Ringing, alarms: List<Alarm>, use24: Boolean, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale",
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Ringing banner with the live time
                Column(
                    Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer).padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_bell_badge), null, Modifier.size(28.dp).scale(pulse))
                        Text("  Ringing now", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(Fmt.time(now, use24), fontSize = 60.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (alarms.isEmpty()) {
                    Text(r.names, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(r.subtitle, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (r.notes.isNotEmpty()) {
                        Text(r.notes, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    alarms.forEach { a -> ReminderDetailsContent(a, use24, ringing = true) }
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                FilledTonalButton(onClick = onSnooze, modifier = Modifier.weight(1f).height(64.dp)) {
                    Text("Snooze ${r.snoozeMinutes} min", style = MaterialTheme.typography.titleMedium)
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(64.dp)) {
                    Text("Dismiss", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
