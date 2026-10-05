package com.sudhirshahu.loopalarm.ring

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sudhirshahu.loopalarm.data.AppSettings
import com.sudhirshahu.loopalarm.ui.theme.LoopAlarmTheme

/**
 * Compact card at the top of the screen, drawn over whatever app is open (like Google Tasks' reminders).
 * Needs "Display over other apps". Shown only while the phone is unlocked and in use.
 */
class PopupOverlay(private val context: Context) {
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    fun canShow() = Settings.canDrawOverlays(context)

    fun show(
        r: Ringing,
        picture: ImageBitmap?,
        settings: AppSettings,
        timeText: String,
        onOpen: () -> Unit,
        onSnooze: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        hide()
        if (!canShow()) return
        val wm = context.getSystemService(WindowManager::class.java)
        val o = OverlayOwner().also { it.start() }
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                LoopAlarmTheme(settings.themeMode, settings.accent) {
                    PopupCard(r, picture, timeText, onOpen, onSnooze, onDismiss)
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Doesn't take keyboard focus, so the app underneath keeps working around the card.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = (context.resources.displayMetrics.density * 32).toInt()
        }
        if (runCatching { wm.addView(v, params) }.isSuccess) {
            view = v
            owner = o
        } else {
            o.stop()
        }
    }

    fun hide() {
        view?.let { v -> runCatching { context.getSystemService(WindowManager::class.java).removeView(v) } }
        owner?.stop()
        view = null
        owner = null
    }
}

@androidx.compose.runtime.Composable
private fun PopupCard(
    r: Ringing,
    picture: ImageBitmap?,
    timeText: String,
    onOpen: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    when {
                        picture != null -> Image(picture, null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
                        r.icon.isNotBlank() -> Text(r.icon, fontSize = 32.sp)
                        else -> Icon(Icons.Filled.Alarm, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(r.names, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("$timeText · ${r.subtitle}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onSnooze, modifier = Modifier.weight(1f)) { Text("Snooze ${r.snoozeMinutes} min") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Dismiss") }
            }
        }
    }
}

/** Minimal lifecycle so Compose can run in a window that isn't part of an activity. */
private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
