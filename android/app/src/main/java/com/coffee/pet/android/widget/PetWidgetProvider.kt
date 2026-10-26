package com.coffee.pet.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.coffee.pet.android.playback.PlaybackRuntime
import com.coffee.pet.android.playback.PlaybackJobScheduler
import com.coffee.pet.android.settings.SettingsRepository

abstract class PetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Log.i("CoffeeP0", "provider-onUpdate ids=${ids.joinToString()}")
        ids.forEach {
            kotlinx.coroutines.runBlocking { SettingsRepository.ensureInstance(context, it) }
            val stale = PlaybackRuntime.get(context).register(it, "provider_onUpdate")
            if (stale) PlaybackJobScheduler.cancel(context, it)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        Log.i("CoffeeP0", "provider-options-changed id=$id options=$newOptions")
        PlaybackJobScheduler.cancel(context, id)
        PlaybackRuntime.get(context).cancel(id, "provider_options_changed")
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        Log.i("CoffeeP0", "provider-onDeleted ids=${ids.joinToString()}")
        ids.forEach {
            PlaybackJobScheduler.cancel(context, it)
            PlaybackRuntime.get(context).delete(it)
            kotlinx.coroutines.runBlocking { SettingsRepository.delete(context, it) }
        }
    }
}

class SmallPetWidgetProvider : PetWidgetProvider()
class LargePetWidgetProvider : PetWidgetProvider()
