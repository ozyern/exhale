/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.IosSettingsGroup
import com.ozyern.exhale.ui.component.IosSettingsItem
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.utils.CrashHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CrashLogsSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val reportFile = File(context.filesDir, CrashHandler.CRASH_REPORT_FILE)
    val report = produceState<String?>(initialValue = null, reportFile.absolutePath) {
        value = withContext(Dispatchers.IO) {
            runCatching { reportFile.takeIf(File::exists)?.readText() }.getOrNull()
        }
    }.value

    SettingsPage(
        navController = navController,
        title = "Crash report",
        horizontalPadding = 16.dp,
    ) {
        Text(
            text = "If Exhale closes unexpectedly, its latest crash report appears here. You can copy or share it when asking for help.",
            style = IosTheme.typography.body,
            color = IosTheme.colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (report == null) {
            Text(
                text = "Checking for a report…",
                style = IosTheme.typography.body,
                color = IosTheme.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
            )
        } else if (report.isBlank()) {
            IosSettingsGroup(
                items = listOf(
                    IosSettingsItem(
                        icon = painterResource(R.drawable.info),
                        title = { Text("No crash report yet") },
                        description = { Text("A report will be saved here if the app closes unexpectedly.") },
                    ),
                ),
            )
        } else {
            IosSettingsGroup(
                title = "Latest report",
                items = listOf(
                    IosSettingsItem(
                        icon = painterResource(R.drawable.content_copy),
                        title = { Text("Copy report") },
                        onClick = { copyReport(context, report) },
                    ),
                    IosSettingsItem(
                        icon = painterResource(R.drawable.share),
                        title = { Text("Share report") },
                        onClick = { shareReport(context, report) },
                    ),
                ),
            )
            Spacer(Modifier.height(16.dp))
            SelectionContainer {
                Text(
                    text = report,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = IosTheme.colors.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(IosTheme.colors.secondarySystemGroupedBackground)
                        .padding(16.dp),
                )
            }
        }
    }
}

private fun copyReport(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Exhale crash report", report))
}

private fun shareReport(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Exhale crash report")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(send, "Share crash report"))
}
