/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * What this phone is called, as opposed to what it is filed under.
 *
 * `Build.MODEL` is a factory code - `CPH2649`, `SM-S928B`, `2201116SG` - and the output picker was
 * showing it as the name of the device you are listening on, which is the one place it is
 * guaranteed to look wrong: every other entry in that list is a Bluetooth name somebody chose.
 *
 * Three sources, in the order they are worth trusting:
 *
 *  1. The vendor's marketing-name property - the string on the box. Every large OEM ships one
 *     under a different key because none of this is in the SDK, so all the known keys are tried;
 *     they are read through `SystemProperties.get` by reflection, which is the only way to reach
 *     them from an app.
 *  2. `Settings.Global.DEVICE_NAME` - what the phone calls itself on the network. Second, not
 *     first, because on many builds it is seeded from `Build.MODEL` and never changed; where the
 *     owner really has renamed the phone and the vendor key is absent, this is what answers.
 *  3. Manufacturer plus model, with the manufacturer capitalised - still a code, but at least a
 *     code with a maker's name in front of it.
 *
 * Resolved once per process: none of these can change while the app is running.
 */
object DeviceNames {

    private val marketingNameKeys = listOf(
        "ro.product.marketname",
        "ro.vendor.oplus.market.name",
        "ro.config.marketing_name",
        "ro.product.odm.marketname",
        "ro.vendor.product.marketname",
        "ro.product.vendor.marketname",
    )

    @Volatile
    private var cached: String? = null

    fun thisDevice(context: Context): String {
        cached?.let { return it }
        // Vendor first, then the network name.
        //
        // The order was the other way round on the theory that a rename is the owner's own answer.
        // In practice `DEVICE_NAME` is very often still the factory code - it is seeded from
        // `Build.MODEL` on a lot of builds and nobody ever changes it - while the vendor's
        // marketing key is exactly the string on the box. A phone that really has been renamed
        // still comes through here, because the vendor key is absent on the ROMs that do not set
        // it and this falls straight through to the rename.
        val resolved = vendorName() ?: settingsName(context) ?: fallbackName()
        cached = resolved
        return resolved
    }

    private fun settingsName(context: Context): String? =
        runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }

    private fun vendorName(): String? {
        val get = runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
        }.getOrNull() ?: return null
        return marketingNameKeys.firstNotNullOfOrNull { key ->
            runCatching { get.invoke(null, key) as? String }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }
        }
    }

    private fun fallbackName(): String {
        val maker = Build.MANUFACTURER.trim().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.trim()
        return when {
            model.isBlank() -> maker
            model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model"
        }
    }
}
