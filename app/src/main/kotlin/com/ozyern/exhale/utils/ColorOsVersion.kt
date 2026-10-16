/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

/**
 * Which ColorOS the phone runs, read from the ROM's own version property.
 *
 * OPlus ROMs (ColorOS, and OxygenOS, which is the same build) publish `ro.build.version.oplusrom`
 * as "V17.0.0" and friends. Anything else - including an OPlus phone on a custom ROM - has no such
 * property and reads as 0.
 */
object ColorOsVersion {
    val major: Int by lazy {
        val get = runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        }.getOrNull() ?: return@lazy 0
        listOf("ro.build.version.oplusrom.display", "ro.build.version.oplusrom")
            .firstNotNullOfOrNull { key ->
                runCatching { get.invoke(null, key) as? String }.getOrNull()
                    ?.let { Regex("""(\d+)""").find(it)?.value?.toIntOrNull() }
                    ?.takeIf { it > 0 }
            } ?: 0
    }

    /** ColorOS 17 (or its OxygenOS twin). */
    val is17: Boolean get() = DeviceAudio.isOplusDevice && major == 17
}
