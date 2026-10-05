package com.sudhirshahu.loopalarm.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.sudhirshahu.loopalarm.data.AppIcon

/**
 * Switches the home-screen icon. Every [AppIcon] is an activity-alias of MainActivity with its own icon;
 * exactly one is enabled. Some launchers take a few seconds to refresh, and a shortcut already on the home screen
 * may need to be added again.
 */
object AppIcons {
    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        fun set(i: AppIcon, on: Boolean) {
            val cn = ComponentName(context.packageName, "${context.packageName}.${i.alias}")
            val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(cn) != state) pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
        }
        // Enable the new one first so there is never a moment with no launcher entry.
        set(icon, true)
        AppIcon.entries.filter { it != icon }.forEach { set(it, false) }
    }
}
