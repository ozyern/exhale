/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.ui.screens.CrashActivity
import timber.log.Timber
import java.io.PrintWriter
import java.io.File
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val applicationContext: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Media3 promotes the service asynchronously, outside app-controlled code.
        // Ignore Android 12+ refusals; playback can retry when it starts in foreground.
        if (Build.VERSION.SDK_INT >= 31 &&
            throwable is android.app.ForegroundServiceStartNotAllowedException
        ) {
            Timber.tag("CrashHandler").w(throwable, "Foreground service start refused by platform — ignoring, not crashing")
            return
        }
        // Restore closes Room and exits after replacing the database file.
        // Ignore queries still in flight against the closed database.
        if (databaseCloseExpected && throwable is android.database.SQLException) {
            Timber.tag("CrashHandler").w(throwable, "SQLException during expected restore DB close — ignoring, process is already exiting")
            return
        }
        try {
            val crashLog = buildCrashLog(throwable)
            Timber.e(throwable, "App crashed")
            // Reports can exceed Binder's transaction limit. The isolated recovery
            // process reads this private file after the failing process exits.
            File(applicationContext.filesDir, CRASH_REPORT_FILE).writeText(crashLog)
            
            val intent = Intent(applicationContext, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            applicationContext.startActivity(intent)
            
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        } catch (e: Exception) {
            Timber.e(e, "Error handling crash")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashLog(throwable: Throwable): String {
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()

        return buildString {
            appendLine("Exhale Crash Report")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Device: ${Build.MODEL}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("=".repeat(50))
            appendLine("Stacktrace:")
            appendLine("=".repeat(50))
            appendLine()
            append(stackTrace)
        }
    }

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"
        const val CRASH_REPORT_FILE = "last_crash_report.txt"

        /** Set by [com.ozyern.exhale.viewmodels.BackupRestoreViewModel] right before it closes
         *  the shared database ahead of an intentional process exit. See the guard above. */
        @Volatile
        var databaseCloseExpected: Boolean = false

        fun install(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Timber.d("CrashHandler installed")
        }
    }
}
