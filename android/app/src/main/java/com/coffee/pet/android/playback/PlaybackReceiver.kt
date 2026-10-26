package com.coffee.pet.android.playback

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ThreadLocalRandom
import com.coffee.pet.android.settings.SettingsRepository
import com.coffee.pet.android.widget.WidgetRenderer

class PlaybackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PLAY) return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val receivedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "receive widget=$widgetId uptime=${SystemClock.uptimeMillis()} elapsed=$receivedAt")
        val settings = SettingsRepository.readFast(context, widgetId)
        if (!settings.animationEnabled || settings.clickOpensSettings) {
            Log.i(TAG, "drop widget=$widgetId reason=animation_disabled_or_settings_click")
            return
        }
        val row = settings.actionRow.takeIf { it in 1..8 } ?: ThreadLocalRandom.current().nextInt(1, 9)
        val coordinator = PlaybackRuntime.get(context)
        val txn = coordinator.beginConfigured(
            widgetId = widgetId,
            actionRow = row,
            configuredActionRow = if (settings.actionRow == 0) -1 else settings.actionRow,
            intervalMs = settings.intervalMs,
            receivedAtElapsedMs = receivedAt,
        )
        if (txn == null) {
            Log.i(TAG, "drop widget=$widgetId reason=busy_or_removed elapsed=${SystemClock.elapsedRealtime() - receivedAt}ms")
            return
        }

        val extras = PersistableBundle().apply {
            putInt(KEY_WIDGET_ID, txn.widgetId)
            putLong(KEY_TRANSACTION_ID, txn.transactionId)
            putLong(KEY_RENDER_GENERATION, txn.renderGeneration)
            putLong(KEY_CONFIG_VERSION, txn.configVersionSnapshot)
            putInt(KEY_ACTION_ROW, txn.actionRow)
            putInt(KEY_INTERVAL_MS, txn.intervalMs)
            putLong(KEY_STARTED_AT, txn.startedAtElapsedMs)
            putBoolean(KEY_HOST_ADVANCE, false)
        }
        val builder = JobInfo.Builder(widgetId, ComponentName(context, PlaybackJobService::class.java))
            .setExtras(extras)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setExpedited(true)
        val result = try {
            (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).schedule(builder.build())
        } catch (failure: Exception) {
            Log.e(TAG, "schedule failed widget=$widgetId txn=${txn.transactionId}", failure)
            JobScheduler.RESULT_FAILURE
        }
        if (result != JobScheduler.RESULT_SUCCESS) {
            coordinator.abort(txn)
            Log.w(TAG, "schedule rejected widget=$widgetId txn=${txn.transactionId} result=$result idle=true")
        } else {
            Log.i(TAG, "scheduled widget=$widgetId txn=${txn.transactionId} expedited=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.S} elapsed=${SystemClock.elapsedRealtime() - receivedAt}ms")
        }
    }

    companion object {
        const val ACTION_PLAY = "com.coffee.pet.android.P0_PLAY"
        const val EXTRA_DEBUG_ACTION_ROW = "com.coffee.pet.android.P0_DEBUG_ACTION_ROW"
        const val KEY_WIDGET_ID = "widgetId"
        const val KEY_TRANSACTION_ID = "transactionId"
        const val KEY_RENDER_GENERATION = "renderGeneration"
        const val KEY_CONFIG_VERSION = "configVersion"
        const val KEY_ACTION_ROW = "actionRow"
        const val KEY_INTERVAL_MS = "intervalMs"
        const val KEY_STARTED_AT = "startedAtElapsedMs"
        const val KEY_HOST_ADVANCE = "hostAdvance"
        private const val TAG = "CoffeeP0"
    }
}
