package com.coffee.pet.android.playback

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.coffee.pet.android.widget.WidgetRenderer

object PlaybackRuntime {
    @Volatile private var instance: PlaybackCoordinator? = null
    fun get(context: Context): PlaybackCoordinator = instance ?: synchronized(this) {
        instance ?: PlaybackCoordinator(
            SharedPreferencesPlaybackRepository(context.applicationContext),
            object : PlaybackRenderer {
                override fun render(widgetId: Int, imageResource: Int?) = WidgetRenderer.commit(context.applicationContext, widgetId, imageResource)
                override fun renderHostSequence(widgetId: Int, actionRow: Int, transactionId: Long, intervalMs: Int) =
                    WidgetRenderer.commitHostSequence(context.applicationContext, widgetId, actionRow, transactionId, intervalMs)
            },
            SystemClock::elapsedRealtime,
            { Log.i("CoffeeP0", it); Unit },
        ).also { instance = it }
    }
}
