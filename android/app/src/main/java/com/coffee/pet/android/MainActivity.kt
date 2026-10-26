package com.coffee.pet.android

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import com.coffee.pet.android.settings.SettingsScreen
import com.coffee.pet.android.settings.SettingsRepository
import com.coffee.pet.android.widget.SmallPetWidgetProvider
import com.coffee.pet.android.widget.LargePetWidgetProvider
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private var refreshKey by mutableIntStateOf(0)
    private var routedWidgetId by mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routedWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(this)
        val existing = (manager.getAppWidgetIds(ComponentName(this, SmallPetWidgetProvider::class.java)).toList() +
            manager.getAppWidgetIds(ComponentName(this, LargePetWidgetProvider::class.java)).toList()).distinct()
        // One-time migration for widget instances created by the P0 app, which predate canonical settings.
        runBlocking { existing.forEach { SettingsRepository.ensureInstance(this@MainActivity, it) } }
        setContent { MaterialTheme { SettingsScreen(initialWidgetId = routedWidgetId.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }, refreshKey = refreshKey) } }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routedWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        refreshKey++
    }
}
