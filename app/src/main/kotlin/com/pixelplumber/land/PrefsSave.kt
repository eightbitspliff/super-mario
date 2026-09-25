package com.pixelplumber.land

import android.content.Context
import com.pixelplumber.core.SaveStore

class PrefsSave(context: Context) : SaveStore {
    private val prefs = context.getSharedPreferences("pixelplumber", Context.MODE_PRIVATE)

    override var unlocked: Int
        get() = prefs.getInt("unlocked", 0)
        set(v) { prefs.edit().putInt("unlocked", v).apply() }

    override var highScore: Int
        get() = prefs.getInt("highscore", 0)
        set(v) { prefs.edit().putInt("highscore", v).apply() }

    var soundOn: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) { prefs.edit().putBoolean("sound", v).apply() }
}
