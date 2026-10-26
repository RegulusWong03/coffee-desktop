package com.coffee.pet.android.playback

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.coffee.pet.android.R

class HostFrameService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = FrameFactory(intent.getIntExtra(EXTRA_ACTION_ROW, -1))

    private inner class FrameFactory(private val actionRow: Int) : RemoteViewsFactory {
        override fun onCreate() = Unit
        override fun onDataSetChanged() = Unit
        override fun onDestroy() = Unit
        override fun getCount(): Int = if (actionRow in 1..8) FrameCatalog.counts[actionRow] + 1 else 1
        override fun getViewAt(position: Int): RemoteViews {
            val frames = if (actionRow in 1..8) FrameCatalog.counts[actionRow] else 0
            val resource = if (position >= frames) FrameCatalog.frame(0, 0) else FrameCatalog.frame(actionRow, position)
            return RemoteViews(packageName, R.layout.widget_frame).apply { setImageViewResource(R.id.frame_image, resource) }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = true
    }

    companion object { const val EXTRA_ACTION_ROW = "p0.action-row" }
}
