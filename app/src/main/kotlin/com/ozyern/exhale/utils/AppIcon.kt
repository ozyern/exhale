/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ozyern.exhale.R
import com.ozyern.exhale.WelcomeActivity

/**
 * Launcher icon aliases; one is enabled at a time and the selection is mirrored in DataStore.
 * [previewRes] is the full 108dp adaptive canvas; the picker zooms it to the 72dp a launcher shows.
 */
enum class AppIcon(
    val id: String,
    val alias: String,
    @DrawableRes val previewRes: Int,
    @StringRes val labelRes: Int,
) {
    GTAVI("gtavi", ".LauncherGtavi", R.drawable.ic_launcher_gtavi_preview, R.string.app_icon_gtavi),
    // The gold icon keeps the "white" id and .LauncherWhite alias it shipped under, so people who
    // picked it before GTA VI stay on it and pinned shortcuts keep resolving.
    GOLD("white", ".LauncherWhite", R.drawable.ic_launcher_gold_preview, R.string.app_icon_white),
    YELLOW("yellow", ".LauncherYellow", R.drawable.ic_launcher_yellow_preview, R.string.app_icon_yellow),
    ;

    companion object {
        val DEFAULT = GTAVI

        /** Unknown ids, including the retired colour variants, fall back to [DEFAULT]. */
        fun fromId(id: String?): AppIcon = entries.firstOrNull { it.id == id } ?: DEFAULT

        /** Enables the new alias before disabling the old one, keeping a launcher entry active. */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                componentFor(context, icon),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            for (otherIcon in entries) {
                if (otherIcon == icon) continue
                pm.setComponentEnabledSetting(
                    componentFor(context, otherIcon),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }

        /**
         * Brings the launcher aliases in line with the saved choice. Needed after an update that
         * changes which alias the manifest enables by default: someone who explicitly chose the
         * gold icon would otherwise see it next to the new default, and someone on a retired
         * colour would be left on whatever the manifest enables. Touches nothing when consistent.
         */
        fun reconcile(context: Context, savedId: String?) {
            val wanted = fromId(savedId)
            val pm = context.packageManager
            val consistent = entries.all { icon ->
                val component = componentFor(context, icon)
                val enabled = when (pm.getComponentEnabledSetting(component)) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                        runCatching { pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).enabled }
                            .getOrDefault(false)
                    else -> false
                }
                enabled == (icon == wanted)
            }
            if (!consistent) apply(context, wanted)
        }

        /** Aliases resolve against the namespace, which can differ from applicationId in debug builds. */
        private fun componentFor(context: Context, icon: AppIcon): ComponentName {
            val namespace = WelcomeActivity::class.java.name.substringBeforeLast('.')
            return ComponentName(context.packageName, namespace + icon.alias)
        }
    }
}
