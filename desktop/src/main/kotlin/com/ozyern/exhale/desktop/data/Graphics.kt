/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

/**
 * How much drawing this PC can take. Full is everything: live glass that bends what's behind it,
 * moving artwork, the blur under the sidebar. Lite keeps the look — the same panes, now plain frosted
 * plates — but nothing is re-recorded or re-blurred every frame, which is what an older integrated
 * GPU can't keep up with.
 *
 * Settings → Appearance → Graphics picks Full or Lite; Auto (the default) judges the PC once, on its
 * first launch, from its graphics chip, memory and cores, and remembers the verdict.
 */
object Graphics {
    /** What Auto decided for this PC; Full until the first check has run. */
    var detectedLite by mutableStateOf(DesktopPrefs.raw("graphics_detected_lite") == "true")
        private set

    /** The graphics chip the check found, for Settings to show. */
    var detectedGpu by mutableStateOf(DesktopPrefs.raw("graphics_detected_gpu"))
        private set

    /** True when drawing should be kept cheap. Read in composition, so a change restyles the app. */
    val lite: Boolean
        // EXHALE_GRAPHICS=LITE or FULL overrides the setting, for measuring one against the other.
        get() = when (System.getenv("EXHALE_GRAPHICS")?.uppercase() ?: AppSettings.current.graphics) {
            "FULL" -> false
            "LITE" -> true
            else -> detectedLite
        }

    /**
     * Physical memory in GB, or null where the JVM can't say. Throwables are caught, not just
     * exceptions: a runtime without the jdk.management module throws NoClassDefFoundError here, and
     * Main.kt reads this while building the image loader — one escape and no artwork loads at all.
     */
    val memoryGb: Long? by lazy {
        try {
            (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
                ?.totalMemorySize?.div(1024L * 1024 * 1024)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Judges the PC, once: Windows is asked for its graphics chips (about a second, off the UI thread),
     * the rest comes from the JVM. Later launches reuse the stored verdict.
     */
    fun detectOnce() {
        if (DesktopPrefs.raw("graphics_detected_lite") != null) return
        Thread({
            val gpus = runCatching { videoControllers() }.getOrDefault(emptyList())
            val cores = Runtime.getRuntime().availableProcessors()
            val lite = judge(gpus, cores, memoryGb)
            detectedGpu = gpus.joinToString(", ").ifBlank { null }
            detectedLite = lite
            DesktopPrefs.putAll(mapOf("graphics_detected_lite" to lite.toString(), "graphics_detected_gpu" to detectedGpu))
        }, "exhale-graphics-check").apply { isDaemon = true }.start()
    }

    /**
     * Lite for a PC with little memory or few cores, or whose only graphics are an older integrated
     * chip: Intel HD and UHD Graphics, AMD's Vega and R-series APUs, or no real driver at all. A
     * discrete card anywhere means Full; Exhale renders on it (see Main.kt).
     */
    internal fun judge(gpus: List<String>, cores: Int, memoryGb: Long?): Boolean {
        if (cores <= 4) return true
        if (memoryGb != null && memoryGb < 8) return true
        if (gpus.isEmpty()) return false
        val discrete = gpus.any { gpu ->
            val g = gpu.lowercase()
            "nvidia" in g || "geforce" in g || "quadro" in g || "radeon rx" in g || "radeon pro" in g || "arc(tm) a" in g || "arc a" in g
        }
        if (discrete) return false
        return gpus.all { gpu ->
            val g = gpu.lowercase()
            "basic display" in g || "basic render" in g ||
                "hd graphics" in g || "uhd graphics" in g ||
                "vega" in g || Regex("radeon\\(tm\\) r\\d|radeon r\\d").containsMatchIn(g)
        }
    }

    private fun videoControllers(): List<String> {
        val process = ProcessBuilder(
            "powershell", "-NoProfile", "-NonInteractive", "-Command",
            "(Get-CimInstance Win32_VideoController).Name",
        ).redirectErrorStream(true).start()
        // Waited on first: reading first blocked until PowerShell exited, so a hung one was never cut
        // off. A few lines of chip names can't fill the pipe meanwhile.
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return emptyList()
        }
        val out = process.inputStream.bufferedReader().readText()
        return out.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }
}
