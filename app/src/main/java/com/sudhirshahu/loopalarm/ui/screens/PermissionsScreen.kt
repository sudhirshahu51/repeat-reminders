package com.sudhirshahu.loopalarm.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sudhirshahu.loopalarm.util.Permissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    // Most permissions are granted on a system settings screen, so re-check whenever we come back.
    LifecycleResumeEffect(Unit) {
        refresh++
        onChanged()
        onPauseOrDispose { }
    }
    val items = remember(refresh) { Permissions.items(context) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; onChanged() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Android and many phone makers stop background apps to save battery. Grant these so alarms ring on time.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            items.forEach { p ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = {
                            when {
                                p.granted -> Icon(Icons.Filled.CheckCircle, "Granted", tint = MaterialTheme.colorScheme.primary)
                                p.required -> Icon(Icons.Filled.ErrorOutline, "Missing", tint = MaterialTheme.colorScheme.error)
                                else -> Icon(Icons.Filled.RadioButtonUnchecked, "Optional")
                            }
                        },
                        headlineContent = { Text(p.title + if (p.required) "" else " (optional)") },
                        supportingContent = { Text(p.why) },
                        trailingContent = {
                            if (!p.granted) {
                                Button(onClick = {
                                    if (p.intent == null && Build.VERSION.SDK_INT >= 33) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else if (p.intent != null) {
                                        try {
                                            context.startActivity(p.intent)
                                        } catch (e: ActivityNotFoundException) {
                                            context.startActivity(Permissions.appDetails(context))
                                        }
                                    }
                                }) { Text("Grant") }
                            }
                        },
                    )
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Phone maker settings", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "On Xiaomi, Oppo, Vivo, Realme, OnePlus, Huawei and Samsung phones, also turn on Autostart and set " +
                            "Battery to 'No restrictions' for this app in the app info screen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { context.startActivity(Permissions.appDetails(context)) }) { Text("Open app info") }
                }
            }
        }
    }
}
