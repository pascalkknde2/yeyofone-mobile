package com.yeyofone.app

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.content.edit

/**
 * The incoming-call ringtone [IncomingCallService] actually plays (AUDIO-02). Stored as a URI
 * string; a dedicated sentinel marks an explicit "Silent" choice from the system ringtone picker,
 * distinct from "never chosen" (which keeps the service's existing default/fallback-tone logic).
 */
internal object RingtonePreference {
    sealed interface Selection {
        data object SystemDefault : Selection
        data object Silent : Selection
        data class Custom(val uri: Uri) : Selection
    }

    private const val PREFS_NAME = "yeyofone_ringtone"
    private const val KEY_URI = "uri"
    private const val SILENT = "silent"

    fun selection(context: Context): Selection {
        val stored = prefs(context).getString(KEY_URI, null) ?: return Selection.SystemDefault
        if (stored == SILENT) return Selection.Silent
        return Selection.Custom(Uri.parse(stored))
    }

    /** The URI to hand the system ringtone picker as its current selection. */
    fun get(context: Context): Uri? = when (val selection = selection(context)) {
        is Selection.Custom -> selection.uri
        Selection.Silent -> null
        Selection.SystemDefault -> RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
    }

    fun set(context: Context, uri: Uri?) {
        prefs(context).edit { putString(KEY_URI, uri?.toString() ?: SILENT) }
    }

    fun title(context: Context): String = when (val selection = selection(context)) {
        Selection.SystemDefault -> context.getString(R.string.ringtone_default)
        Selection.Silent -> context.getString(R.string.ringtone_silent)
        is Selection.Custom -> runCatching { RingtoneManager.getRingtone(context, selection.uri)?.getTitle(context) }
            .getOrNull() ?: context.getString(R.string.ringtone_default)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
