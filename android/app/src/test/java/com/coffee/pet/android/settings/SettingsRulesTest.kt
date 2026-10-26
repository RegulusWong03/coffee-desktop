package com.coffee.pet.android.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRulesTest {
    @Test fun readingMissingInstanceFieldsDoesNotCreatePreferencesAndKeepsFollowingGlobalDefaults() {
        val stored = mutableMapOf<String, Any>()
        val firstGlobal = PetSettings(actionRow = 3, intervalMs = 220, sizePercent = 75, clickOpensSettings = true)
        assertEquals(firstGlobal, SettingsRules.readWithoutMutation(firstGlobal, stored, null))
        assertEquals(emptyMap<String, Any>(), stored)

        val updatedGlobal = PetSettings(actionRow = 6, intervalMs = 280, sizePercent = 95, clickOpensSettings = false)
        assertEquals(updatedGlobal, SettingsRules.readWithoutMutation(updatedGlobal, stored, null))
        assertEquals(emptyMap<String, Any>(), stored)
    }

    @Test fun initializedInstanceSnapshotRemainsIndependentFromLaterGlobalChanges() {
        val firstGlobal = PetSettings(actionRow = 4, intervalMs = 250, sizePercent = 80, clickOpensSettings = true)
        val initialized = SettingsRules.initialInstance(firstGlobal)
        val stored = mutableMapOf<String, Any>(
            "action" to initialized.actionRow,
            "speed" to initialized.intervalMs,
            "size" to initialized.sizePercent,
            "click" to initialized.clickOpensSettings,
        )
        val laterGlobal = PetSettings(actionRow = 8, intervalMs = 170, sizePercent = 50, clickOpensSettings = false)
        val resolved = SettingsRules.readWithoutMutation(laterGlobal, stored, null)
        assertEquals(4, resolved.actionRow)
        assertEquals(250, resolved.intervalMs)
        assertEquals(80, resolved.sizePercent)
        assertEquals(true, resolved.clickOpensSettings)
        assertEquals(4, stored["action"])
    }

    @Test fun animationFollowIsDynamicUntilAnInstanceOverrideIsSaved() {
        val defaults = SettingsRules.initialInstance(PetSettings(animationEnabled = true, actionRow = 2))
        assertNull(defaults.animationOverride)
        val off = PetSettings(animationEnabled = false, actionRow = 7)
        assertEquals(false, SettingsRules.resolve(off, defaults, null).animationEnabled)
        assertEquals(true, SettingsRules.resolve(off, defaults, true).animationEnabled)
    }
}
