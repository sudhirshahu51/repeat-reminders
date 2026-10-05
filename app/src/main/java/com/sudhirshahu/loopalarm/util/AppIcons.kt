package com.sudhirshahu.loopalarm.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.sudhirshahu.loopalarm.data.Accent
import com.sudhirshahu.loopalarm.data.AppIcon
import com.sudhirshahu.loopalarm.data.notebook

/**
 * Switches the home-screen icon. Every background ([AppIcon]) and notebook page colour (from the [Accent]) pair is an
 * activity-alias of MainActivity with its own icon, e.g. LauncherDefault (white page) or LauncherDefaultTeal;
 * exactly one is enabled. Some launchers take a few seconds to refresh, and a shortcut already on the home screen
 * may need to be added again.
 */
object AppIcons {
    private fun alias(icon: AppIcon, notebook: String) = icon.alias + notebook

    private val all: List<String> by lazy {
        val notebooks = Accent.entries.map { it.notebook }.distinct()
        AppIcon.entries.flatMap { icon -> notebooks.map { alias(icon, it) } }
    }

    @Volatile private var cached: Pair<String, Bitmap>? = null

    /**
     * The home-screen icon as it currently looks (background and notebook colour), for notification large icons.
     * LauncherDefault is enabled by the manifest, so it is the answer when no alias was switched on explicitly.
     */
    fun bitmap(context: Context): Bitmap? = runCatching {
        val pm = context.packageManager
        fun cn(alias: String) = ComponentName(context.packageName, "${context.packageName}.$alias")
        val alias = all.firstOrNull { pm.getComponentEnabledSetting(cn(it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
            ?: AppIcon.DEFAULT.alias
        // Also keyed by night mode: the Auto background is black in light mode and white in dark mode.
        val key = "$alias/${context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK}"
        cached?.takeIf { it.first == key }?.second
            ?: pm.getActivityIcon(cn(alias)).toBitmap(192, 192).also { cached = key to it }
    }.getOrNull()

    fun apply(context: Context, icon: AppIcon, accent: Accent) {
        val pm = context.packageManager
        fun set(alias: String, on: Boolean) {
            val cn = ComponentName(context.packageName, "${context.packageName}.$alias")
            val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(cn) != state) pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
        }
        val chosen = alias(icon, accent.notebook)
        // Enable the new one first so there is never a moment with no launcher entry.
        set(chosen, true)
        all.filter { it != chosen }.forEach { set(it, false) }
    }
}
