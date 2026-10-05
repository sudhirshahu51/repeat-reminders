package com.sudhirshahu.loopalarm.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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
