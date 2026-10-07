package com.sudhirshahu.loopalarm.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.AppSettings
import com.sudhirshahu.loopalarm.ui.screens.AlarmEditScreen
import com.sudhirshahu.loopalarm.ui.screens.AlarmListScreen
import com.sudhirshahu.loopalarm.ui.screens.BirthdaysScreen
import com.sudhirshahu.loopalarm.ui.screens.HistoryScreen
import com.sudhirshahu.loopalarm.ui.screens.PermissionsScreen
import com.sudhirshahu.loopalarm.ui.screens.QuickAddSheet
import com.sudhirshahu.loopalarm.ui.screens.ReminderDetailsScreen
import com.sudhirshahu.loopalarm.ui.screens.SettingsScreen
import com.sudhirshahu.loopalarm.ui.theme.LoopAlarmTheme
import com.sudhirshahu.loopalarm.util.Fmt
import com.sudhirshahu.loopalarm.util.Permissions

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** A .ics or .vcf file shared to the app or opened with it, waiting for the birthday import screen. */
    private val sharedFile = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) sharedFile.value = importUri(intent)
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings
            if (s == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LoopAlarmTheme(s.themeMode, s.accent) { AppRoot(vm, s, sharedFile.value) { sharedFile.value = null } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importUri(intent)?.let { sharedFile.value = it }
    }

    private fun importUri(i: Intent?): Uri? = when (i?.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_VIEW -> i.data
        else -> null
    }
}

private val tabs = listOf(
    Triple("alarms", "Alarms", Icons.Filled.Alarm),
    Triple("history", "History", Icons.Filled.History),
    Triple("settings", "Settings", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: AppViewModel, settings: AppSettings, sharedFile: Uri?, onSharedFileHandled: () -> Unit) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val use24 = Fmt.is24h(context, settings.timeFormat)

    var permissionCheck by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        permissionCheck++
        onPauseOrDispose { }
    }
    val missing = remember(permissionCheck) { Permissions.missingRequired(context) }

    var quickAdd by remember { mutableStateOf(false) }
    // Unsaved alarm handed from Quick add to the full editor.
    var draft by remember { mutableStateOf<Alarm?>(null) }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val topLevel = tabs.firstOrNull { it.first == route }

    LaunchedEffect(sharedFile) { if (sharedFile != null && route != "birthdays") nav.navigate("birthdays") }

    fun openTab(r: String) = nav.navigate(r) {
        popUpTo("alarms") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        topBar = {
            if (topLevel != null) {
                TopAppBar(
                    title = { Text(if (route == "alarms") "Repeat Reminders" else topLevel.second) },
                    actions = {
                        if (route == "alarms") IconButton(onClick = { nav.navigate("birthdays") }) { Icon(Icons.Filled.Cake, "Import birthdays") }
                    },
                )
            }
        },
        bottomBar = {
            if (topLevel != null) {
                NavigationBar {
                    tabs.forEach { (r, label, icon) ->
                        NavigationBarItem(selected = route == r, onClick = { openTab(r) }, icon = { Icon(icon, null) }, label = { Text(label) })
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "alarms", modifier = Modifier.padding(if (topLevel == null) PaddingValues() else pad)) {
            composable("alarms") {
                AlarmListScreen(
                    alarms = alarms,
                    use24 = use24,
                    missingPermissions = missing,
                    contentPadding = PaddingValues(),
                    onToggle = { a, on -> vm.setEnabled(a, on) },
                    onToggleGroup = { g, on -> vm.setGroupEnabled(g, on) },
                    onEdit = { nav.navigate("edit/${it.id}") },
                    onDuplicate = { vm.duplicate(it) },
                    onDelete = { vm.delete(it) },
                    onRingNow = { vm.ringNow(it) },
                    onOpen = { nav.navigate("details/${it.id}") },
                    onQuickAdd = { quickAdd = true },
                    onOpenPermissions = { nav.navigate("permissions") },
                )
            }
            composable("history") {
                HistoryScreen(history, use24, PaddingValues(), onClear = { vm.clearHistory() })
            }
            composable("settings") {
                SettingsScreen(
                    settings, missing, PaddingValues(),
                    onChange = { vm.updateSettings(it) },
                    onOpenPermissions = { nav.navigate("permissions") },
                )
            }
            composable("birthdays") {
                BirthdaysScreen(
                    existing = alarms,
                    use24 = use24,
                    sharedFile = sharedFile,
                    onSharedFileHandled = onSharedFileHandled,
                    onBack = { nav.popBackStack() },
                    onAdd = { list -> vm.addAll(list); nav.popBackStack() },
                )
            }
            composable("permissions") {
                PermissionsScreen(onBack = { nav.popBackStack() }, onChanged = { permissionCheck++ })
            }
            composable("details/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                // From the live list, so edits and the on/off switch show up straight away.
                val a = alarms.firstOrNull { it.id == id }
                a?.let {
                    ReminderDetailsScreen(
                        it, use24,
                        onBack = { nav.popBackStack() },
                        onEdit = { r -> nav.navigate("edit/${r.id}") },
                        onToggle = { r, on -> vm.setEnabled(r, on) },
                        onRingNow = { r -> vm.ringNow(r) },
                        onDuplicate = { r -> vm.duplicate(r); nav.popBackStack() },
                        onDelete = { r -> vm.delete(r); nav.popBackStack() },
                    )
                }
            }
            composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val initial by produceState<Alarm?>(null, id) {
                    value = when (id) {
                        DRAFT_ID -> draft ?: Alarm()
                        0L -> Alarm()
                        else -> vm.load(id) ?: Alarm()
                    }
                }
                initial?.let { a ->
                    AlarmEditScreen(
                        initial = a,
                        use24 = use24,
                        groups = groups,
                        onBack = { nav.popBackStack() },
                        onSave = { vm.save(it); draft = null; nav.popBackStack() },
                        onDelete = { vm.delete(it); nav.popBackStack() },
                    )
                }
            }
        }
    }

    if (quickAdd) {
        QuickAddSheet(
            use24 = use24,
            onDismiss = { quickAdd = false },
            onSave = { vm.save(it); quickAdd = false },
            onMoreOptions = { draft = it; quickAdd = false; nav.navigate("edit/$DRAFT_ID") },
        )
    }
}

private const val DRAFT_ID = -1L
