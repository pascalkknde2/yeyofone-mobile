package com.yeyofone.app

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.core.content.FileProvider
import androidx.core.content.edit
import java.io.File

/**
 * Settings › Advanced › Debug log. When on, the next engine start logs SIP summaries and media
 * details (see YeyoFoneApplication.debugLogActive). Sharing copies this process's log lines to a
 * file; it never leaves the device unless the user picks a destination in the share sheet.
 */
internal object DebugLog {
    private const val PREFS = "diagnostics"
    private const val KEY_ENABLED = "debug_log"
    private const val DIRECTORY = "diagnostics"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    /** Blocking: writes this process's logcat to a file and returns a share intent, or null if it's empty. */
    fun shareIntent(context: Context): Intent? {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val file = File(directory, "yeyofone-log.txt")
        // An app may always read its own process's log lines.
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=${Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        process.inputStream.use { input -> file.outputStream().use(input::copyTo) }
        process.waitFor()
        if (file.length() == 0L) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "YeyoFone debug log")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }

    /** Relaunches the app in a new process so the engine starts with the new log setting. */
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }
}
