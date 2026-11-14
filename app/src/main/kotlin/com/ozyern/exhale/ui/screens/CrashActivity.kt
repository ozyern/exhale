/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import androidx.compose.ui.res.painterResource
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.IconButton
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ozyern.exhale.R
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFSquareOnSquare
import com.composables.sfsymbols.monochrome.SFXmark
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.IosControlSize
import com.ozyern.exhale.ui.ios.component.IosButton
import com.ozyern.exhale.ui.ios.component.IosButtonStyle
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.theme.SfProFontFamily
import com.ozyern.exhale.utils.CrashHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val crashLog = intent.getStringExtra(CrashHandler.EXTRA_CRASH_LOG) ?: withContext(Dispatchers.IO) {
                runCatching { File(filesDir, CrashHandler.CRASH_REPORT_FILE).readText() }.getOrNull()
            } ?: getString(R.string.crash_no_log)
            setContent {
                val darkTheme = isSystemInDarkTheme()
                // Recovery must not read the preference store that may have caused the crash.
                IosTheme(darkTheme = darkTheme, accent = Color(0xFFFA233B)) {
                    CrashScreen(
                        crashLog = crashLog,
                        onClose = { finishAffinity() },
                        onShare = { shareCrashLog(crashLog) },
                        onCopy = { copyToClipboard(crashLog) }
                    )
                }
            }
        }
    }

    private fun copyToClipboard(crashLog: String) {
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("CrashLog", crashLog)
        cm.setPrimaryClip(clip)
        android.widget.Toast.makeText(this, R.string.copied_to_clipboard, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun shareCrashLog(crashLog: String) {
        lifecycleScope.launch {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "exhale_crash_$timestamp.txt"
                val crashFile = withContext(Dispatchers.IO) {
                    File(cacheDir, fileName).also { it.writeText(crashLog) }
                }

                val uri = FileProvider.getUriForFile(
                    this@CrashActivity,
                    "${packageName}.FileProvider",
                    crashFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_subject))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(shareIntent, getString(R.string.crash_share_title)))
            } catch (e: Exception) {
                // Fallback to simple text share if file sharing fails
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, crashLog)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_subject))
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.crash_share_title)))
            }
        }
    }
}

@Composable
fun CrashScreen(
    crashLog: String,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    val colors = IosTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.systemBackground)
            .safeDrawingPadding(),
    ) {
        // Center the title independently of the trailing controls, as on iPad.
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            Text(
                text = stringResource(R.string.crash_title),
                fontFamily = SfProFontFamily,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 100.dp),
            )
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                IconButton(onClick = onCopy) {
                    SymbolIcon(symbol = sf("SFSquareOnSquare"), contentDescription = stringResource(R.string.copy_logs), size = 22.dp, tint = colors.label)
                }
                IconButton(onClick = onClose) {
                    SymbolIcon(symbol = sf("SFXmark"), contentDescription = stringResource(R.string.crash_close), size = 22.dp, tint = colors.label)
                }
            }
        }
        // This is the only vertical scroller. weight gives it a finite viewport
        // in both orientations, even for a long exception report.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.crash_description),
                fontFamily = SfProFontFamily,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = colors.secondaryLabel,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(ContinuousRoundedRectangle(16.dp))
                    .background(if (colors.isDark) colors.systemGray5 else colors.secondarySystemBackground)
                    .padding(16.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = crashLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = colors.label,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
        // The action owns its space; neither the report nor system gestures
        // can obscure it on a short landscape viewport.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IosButton(
                onClick = onShare,
                style = IosButtonStyle.Bordered,
                controlSize = IosControlSize.Large,
            ) {
                SymbolIcon(symbol = sf("square.and.arrow.up"), contentDescription = null, size = 18.dp)
                Text(stringResource(R.string.crash_share_logs))
            }
        }
    }
}
