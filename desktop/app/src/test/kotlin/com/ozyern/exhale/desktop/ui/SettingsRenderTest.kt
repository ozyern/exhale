/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.LocalSettings
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.settings.SettingsScreen
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Draws the settings screen to a PNG so a layout can be looked at without driving the real window. */
class SettingsRenderTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun renderSettings() {
        val out = System.getenv("EXHALE_RENDER_DIR") ?: return
        assumeTrue(out.isNotBlank())
        for (page in listOf("default", "about")) {
            val scene = ImageComposeScene(1320, 860, Density(1f)) {
                val settings = AppSettings.current
                CompositionLocalProvider(LocalSettings provides settings) {
                    ExhaleTheme(settings, null) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.fillMaxSize()) {
                                SettingsScreen(
                                    insets = ChromeInsets(0.dp, 0.dp),
                                    onBack = {},
                                    onSignIn = {},
                                    onEditTaste = {},
                                    onHomeInputsChanged = {},
                                    onDiscordLogin = {},
                                    startPage = if (page == "about") "about" else null,
                                )
                            }
                        }
                    }
                }
            }
            repeat(30) { scene.render(it * 16_000_000L) }
            val image = scene.render(31 * 16_000_000L)
            File(out, "settings-$page.png").writeBytes(image.encodeToData()!!.bytes)
            scene.close()
        }
    }
}
