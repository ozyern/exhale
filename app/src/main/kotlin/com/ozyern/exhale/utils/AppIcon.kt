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
 * [previewRes] is the foreground layer shown over [R.color.ic_launcher_tile] in the picker.
 */
enum class AppIcon(
    val id: String,
    val alias: String,
    @DrawableRes val previewRes: Int,
    @StringRes val labelRes: Int,
) {
    WHITE("white", ".LauncherWhite", R.drawable.ic_launcher_exhale_preview, R.string.app_icon_white),
    AZURE("azure", ".LauncherAzure", R.drawable.ic_launcher_azure_fg, R.string.app_icon_azure),
    SKY("sky", ".LauncherSky", R.drawable.ic_launcher_sky_fg, R.string.app_icon_sky),
    TEAL("teal", ".LauncherTeal", R.drawable.ic_launcher_teal_fg, R.string.app_icon_teal),
    SLATE("slate", ".LauncherSlate", R.drawable.ic_launcher_slate_fg, R.string.app_icon_slate),
    SILVER("silver", ".LauncherSilver", R.drawable.ic_launcher_silver_fg, R.string.app_icon_silver),
    PERIWINKLE(
        "periwinkle",
        ".LauncherPeriwinkle",
        R.drawable.ic_launcher_periwinkle_fg,
        R.string.app_icon_periwinkle,
    ),
    VIOLET("violet", ".LauncherViolet", R.drawable.ic_launcher_violet_fg, R.string.app_icon_violet),
    MIDNIGHT(
        "midnight",
        ".LauncherMidnight",
        R.drawable.ic_launcher_midnight_fg,
        R.string.app_icon_midnight,
    ),
    MAUVE("mauve", ".LauncherMauve", R.drawable.ic_launcher_mauve_fg, R.string.app_icon_mauve),
    ROSE("rose", ".LauncherRose", R.drawable.ic_launcher_rose_fg, R.string.app_icon_rose),
    COPPER("copper", ".LauncherCopper", R.drawable.ic_launcher_copper_fg, R.string.app_icon_copper),
    RUST("rust", ".LauncherRust", R.drawable.ic_launcher_rust_fg, R.string.app_icon_rust),
    ;

    companion object {
        val DEFAULT = WHITE

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

        /** Aliases resolve against the namespace, which can differ from applicationId in debug builds. */
        private fun componentFor(context: Context, icon: AppIcon): ComponentName {
            val namespace = WelcomeActivity::class.java.name.substringBeforeLast('.')
            return ComponentName(context.packageName, namespace + icon.alias)
        }
    }
}
