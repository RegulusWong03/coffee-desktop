package com.coffee.pet.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.coffee.pet.android.R
import com.coffee.pet.android.playback.FrameCatalog
import com.coffee.pet.android.playback.PlaybackReceiver
import com.coffee.pet.android.playback.HostFrameService
import com.coffee.pet.android.MainActivity

object WidgetRenderer {
    fun commit(context: Context, widgetId: Int, imageResource: Int?) {
        val settings = com.coffee.pet.android.settings.SettingsRepository.readFast(context, widgetId)
        val views = RemoteViews(context.packageName, R.layout.pet_widget)
        views.setImageViewResource(R.id.pet_frame, imageResource ?: FrameCatalog.frame(0, 0))
        views.setOnClickPendingIntent(R.id.pet_frame, if (settings.clickOpensSettings) settingsIntent(context, widgetId) else clickIntent(context, widgetId))
        views.setFloat(R.id.pet_frame, "setScaleX", settings.sizePercent / 100f)
        views.setFloat(R.id.pet_frame, "setScaleY", settings.sizePercent / 100f)
        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
    }

    fun refreshClickBehavior(context: Context, widgetId: Int, openSettings: Boolean) {
        val update = RemoteViews(context.packageName, R.layout.pet_widget)
        update.setOnClickPendingIntent(R.id.pet_frame, if (openSettings) settingsIntent(context, widgetId) else clickIntent(context, widgetId))
        AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(widgetId, update)
    }

    fun cancelAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val coordinator = com.coffee.pet.android.playback.PlaybackRuntime.get(context)
        listOf(SmallPetWidgetProvider::class.java, LargePetWidgetProvider::class.java).forEach { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                com.coffee.pet.android.playback.PlaybackJobScheduler.cancel(context, id)
                coordinator.cancel(id, "debug_activity")
            }
        }
    }

    fun commitHostSequence(context: Context, widgetId: Int, actionRow: Int, transactionId: Long, intervalMs: Int) {
        val views = RemoteViews(context.packageName, R.layout.pet_widget_host)
        views.setOnClickPendingIntent(R.id.host_widget_root, clickIntent(context, widgetId))
        views.setInt(R.id.host_flipper, "setFlipInterval", intervalMs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
            val frameCount = FrameCatalog.counts[actionRow]
            for (position in 0..frameCount) {
                val resource = if (position == frameCount) FrameCatalog.frame(0, 0) else FrameCatalog.frame(actionRow, position)
                val item = RemoteViews(context.packageName, R.layout.widget_frame).apply { setImageViewResource(R.id.frame_image, resource) }
                items.addItem(position.toLong(), item)
            }
            views.setRemoteAdapter(R.id.host_flipper, items.build())
        } else {
            val adapterIntent = Intent(context, HostFrameService::class.java)
                .setData(Uri.parse("coffee-p0://$widgetId/$transactionId"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(HostFrameService.EXTRA_ACTION_ROW, actionRow)
            views.setRemoteAdapter(R.id.host_flipper, adapterIntent)
        }
        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
    }

    private fun clickIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, PlaybackReceiver::class.java).setAction(PlaybackReceiver.ACTION_PLAY)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun settingsIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 100000 + widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

}
