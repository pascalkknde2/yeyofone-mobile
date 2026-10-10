package com.yeyofone.app.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.yeyofone.app.R

/** The user's light/dark choice from Settings › Appearance. */
enum class Appearance(@StringRes val label: Int) {
    SYSTEM(R.string.appearance_system),
    LIGHT(R.string.appearance_light),
    DARK(R.string.appearance_dark),
}

object AppearancePreferences {
    private const val PREFS = "appearance_settings"
    private const val KEY_APPEARANCE = "appearance"

    /** Current choice; YeyoFoneTheme recomposes when it changes. Loaded by [load]. */
    var current by mutableStateOf(Appearance.SYSTEM)
        private set

    fun load(context: Context) {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_APPEARANCE, null)
        current = Appearance.entries.firstOrNull { it.name == stored } ?: Appearance.SYSTEM
    }

    fun set(context: Context, appearance: Appearance) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_APPEARANCE, appearance.name) }
        current = appearance
    }
}
