package com.coffee.pet.android.playback

import android.content.Context

enum class P0PlaybackMode { PER_FRAME, HOST_ADVANCE }

object P0PlaybackModeStore {
    private const val PREFS = "p0-diagnostics"
    private const val KEY = "playback-mode"
    private const val SPEED_KEY = "interval-ms"
    private const val ACTION_ROW_KEY = "debug-action-row"
    fun get(context: Context): P0PlaybackMode = runCatching {
        P0PlaybackMode.valueOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: "PER_FRAME")
    }.getOrDefault(P0PlaybackMode.PER_FRAME)
    fun getInterval(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(SPEED_KEY, PlaybackPolicy.DEFAULT_INTERVAL_MS).coerceIn(PlaybackPolicy.DEFAULT_INTERVAL_MS, PlaybackPolicy.MAX_INTERVAL_MS)
    fun getDebugActionRow(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(ACTION_ROW_KEY, 0).takeIf { it in 0..8 } ?: 0
    fun set(context: Context, mode: P0PlaybackMode, intervalMs: Int) {
        require(intervalMs in PlaybackPolicy.DEFAULT_INTERVAL_MS..PlaybackPolicy.MAX_INTERVAL_MS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).putInt(SPEED_KEY, intervalMs).commit()
    }
    fun setDebugActionRow(context: Context, row: Int) {
        require(row in 0..8)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(ACTION_ROW_KEY, row).commit()
    }
}
