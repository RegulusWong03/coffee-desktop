package com.coffee.pet.android.playback

import android.content.Context

class SharedPreferencesPlaybackRepository(context: Context) : PlaybackRepository {
    private val prefs = context.getSharedPreferences("p0-widget-state", Context.MODE_PRIVATE)
    private fun key(id: Int) = id.toString()

    @Synchronized override fun read(widgetId: Int): PlaybackRecord? {
        val values = prefs.getString(key(widgetId), null)?.split(',')?.mapNotNull(String::toLongOrNull) ?: return null
        if (values.size != 8) return null
        return PlaybackRecord(values[0] != 0L, values[1] != 0L, values[2], values[3], values[4], values[5].toInt(), values[6].toInt(), values[7])
    }

    @Synchronized override fun write(widgetId: Int, record: PlaybackRecord) {
        prefs.edit().putString(key(widgetId), listOf(if (record.exists) 1 else 0, if (record.busy) 1 else 0, record.transactionId, record.renderGeneration, record.nextConfigVersion, record.actionRow, record.intervalMs, record.startedAtElapsedMs).joinToString(",")).commit()
    }

    @Synchronized override fun delete(widgetId: Int, tombstone: PlaybackRecord) = write(widgetId, tombstone)
}
