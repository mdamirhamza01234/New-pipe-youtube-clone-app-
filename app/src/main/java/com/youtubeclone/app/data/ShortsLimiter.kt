package com.youtubeclone.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

enum class ShortsBlock { DAILY, SESSION }

/** Shorts ki limits: din mein 2 ghante, aur ek baar mein lagataar 3 minute. */
object ShortsLimiter {
    const val DAILY_LIMIT_MS = 2L * 60 * 60 * 1000
    const val SESSION_LIMIT_MS = 3L * 60 * 1000

    /** Itni der Shorts se door rahe to naya session shuru maana jaata hai. */
    private const val AWAY_RESET_MS = 5L * 60 * 1000

    private lateinit var sp: SharedPreferences
    private var day = ""
    private var lastActive = 0L
    private var ticks = 0

    var todayMs by mutableLongStateOf(0L)
        private set
    var sessionMs by mutableLongStateOf(0L)
        private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("shorts_limit", Context.MODE_PRIVATE)
        day = LocalDate.now().toString()
        todayMs = if (sp.getString("day", "") == day) sp.getLong("ms", 0L) else 0L
    }

    val blocked: ShortsBlock?
        get() = when {
            todayMs >= DAILY_LIMIT_MS -> ShortsBlock.DAILY
            sessionMs >= SESSION_LIMIT_MS -> ShortsBlock.SESSION
            else -> null
        }

    private fun rollDay() {
        val t = LocalDate.now().toString()
        if (t != day) {
            day = t
            todayMs = 0L
            save()
        }
    }

    private fun save() {
        sp.edit().putString("day", day).putLong("ms", todayMs).apply()
    }

    /** Shorts tab khulne par. */
    fun enter() {
        rollDay()
        if (System.currentTimeMillis() - lastActive > AWAY_RESET_MS) sessionMs = 0L
    }

    /** Shorts tab chhodne par. */
    fun leave() {
        lastActive = System.currentTimeMillis()
        save()
    }

    /** Short chalte waqt har second bulao. */
    fun tick(ms: Long) {
        rollDay()
        todayMs += ms
        sessionMs += ms
        lastActive = System.currentTimeMillis()
        if (++ticks % 5 == 0) save()
    }

    /** 3 minute ke break ke baad "Phir se dekho" dabane par. */
    fun newSession() {
        sessionMs = 0L
    }
}
