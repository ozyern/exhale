/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop

import java.io.File
import java.util.jar.JarFile
import kotlin.concurrent.thread

/**
 * Loads the app's classes, and Compose's, before anything needs them.
 *
 * Compose makes a class of nearly every lambda, and the JVM reads each one from its jar the first time
 * it runs — on the thread that draws the window. A flight recording of the first open of an artist
 * page caught one such read taking 155 ms: the first open of every page and of the player stuttered
 * while the second was smooth. This reads them all once, early, at the lowest priority, so first opens
 * are as smooth as second ones. Classes are only loaded, never initialised: nothing runs.
 */
object ClassWarmup {
    /** One class from each jar worth warming; the whole jar it came from is loaded. */
    private val anchors = listOf(
        "com.ozyern.exhale.desktop.MainKt",
        "com.ozyern.exhale.innertube.YouTube",
        "androidx.compose.foundation.lazy.LazyListState",
        "androidx.compose.foundation.layout.RowKt",
        "androidx.compose.animation.AnimatedContentKt",
        "androidx.compose.animation.core.Animatable",
        "androidx.compose.material3.MaterialTheme",
        "androidx.compose.ui.Modifier",
        "androidx.compose.ui.text.TextStyle",
        "coil3.ImageLoader",
        "com.kyant.backdrop.BackdropKt",
    )

    fun start() {
        thread(name = "exhale-class-warmup", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            // The window, Home and the player come first; this is for what's opened after. Not at 3 s:
            // timed, that was exactly when Home was building its first page and decoding its artwork,
            // and loading every class of Compose's jars beside it held Home up by seconds.
            Thread.sleep(15_000)
            val loader = ClassWarmup::class.java.classLoader
            val jars = anchors.mapNotNull { name ->
                runCatching { Class.forName(name, false, loader).protectionDomain?.codeSource?.location?.toURI()?.let(::File) }.getOrNull()
            }.filter { it.isFile && it.extension == "jar" }.distinct()
            for (jar in jars) {
                runCatching {
                    JarFile(jar).use { file ->
                        for (entry in file.entries()) {
                            val path = entry.name
                            if (!path.endsWith(".class") || path.startsWith("META-INF") || path.endsWith("module-info.class")) continue
                            // A class that won't load here wouldn't have loaded later either; skip it.
                            runCatching { Class.forName(path.removeSuffix(".class").replace('/', '.'), false, loader) }
                        }
                    }
                }
            }
        }
    }
}
