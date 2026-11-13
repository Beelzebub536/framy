package app.framy

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Plain (non-secret) settings. Theme fields are Compose state so the UI recomposes on change. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        themeSeedState = sp.getString("themeSeed", null) ?: if (android.os.Build.VERSION.SDK_INT >= 31) "wallpaper" else "slate"
        modeState = sp.getString("mode", "system")!!
        if (!sp.contains("tag")) sp.edit().putString("tag", "%04x".format(Random.nextInt(0x10000))).apply()
    }

    private var themeSeedState by mutableStateOf("slate")
    private var modeState by mutableStateOf("system")

    /** "wallpaper" | "slate" | "sage" | "teal" | "sand" | "rose" */
    var themeSeed: String
        get() = themeSeedState
        set(v) { themeSeedState = v; sp.edit().putString("themeSeed", v).apply() }

    /** "system" | "light" | "dark" */
    var mode: String
        get() = modeState
        set(v) { modeState = v; sp.edit().putString("mode", v).apply() }

    var canvasBaseUrl: String
        get() = sp.getString("canvasBaseUrl", null) ?: "https://northsouth.instructure.com"
        set(v) = sp.edit().putString("canvasBaseUrl", v.trim().trimEnd('/')).apply()

    var nick: String
        get() = sp.getString("nick", "") ?: ""
        set(v) = sp.edit().putString("nick", v.trim().take(30)).apply()

    /** 4 hex chars, created once per install. */
    val tag: String get() = sp.getString("tag", "0000")!!
}
