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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sudhirshahu.loopalarm.app
import com.sudhirshahu.loopalarm.data.AppSettings
import com.sudhirshahu.loopalarm.data.ImageStore
import com.sudhirshahu.loopalarm.ring.AlarmService
import com.sudhirshahu.loopalarm.ring.Ringing
import com.sudhirshahu.loopalarm.ring.RingingState
import com.sudhirshahu.loopalarm.ui.theme.LoopAlarmTheme
import com.sudhirshahu.loopalarm.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The optional post-alarm screen, shown over the lock screen while an alarm rings. */
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
        enableEdgeToEdge()
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val ringing by RingingState.current.collectAsStateWithLifecycle()
            val r = ringing
            LaunchedEffect(r) { if (r == null) finish() }
            LoopAlarmTheme(settings.themeMode, settings.accent) {
                if (r != null) {
                    RingScreen(
                        r, Fmt.is24h(this, settings.timeFormat),
                        onSnooze = { startService(AlarmService.actionIntent(this, AlarmService.ACTION_SNOOZE)); finish() },
                        onDismiss = { startService(AlarmService.actionIntent(this, AlarmService.ACTION_DISMISS)); finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun RingScreen(r: Ringing, use24: Boolean, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val context = LocalContext.current
    val picture by produceState<ImageBitmap?>(null, r.imageFile) {
        value = withContext(Dispatchers.IO) { ImageStore.load(context, r.imageFile)?.asImageBitmap() }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.9f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale",
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 48.dp)) {
                Text(Fmt.time(now, use24), fontSize = 72.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Text(r.names, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(r.subtitle, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            val picture = picture
            if (picture != null) {
                Image(
                    picture, "Reminder picture", contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f, fill = false).padding(vertical = 24.dp).clip(RoundedCornerShape(16.dp)),
                )
            } else if (r.icon.isNotBlank()) {
                Text(r.icon, fontSize = 110.sp, modifier = Modifier.scale(pulse))
            } else {
                Icon(Icons.Filled.Alarm, null, Modifier.size(120.dp).scale(pulse), tint = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
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
