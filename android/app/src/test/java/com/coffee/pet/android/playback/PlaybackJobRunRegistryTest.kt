package com.coffee.pet.android.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackJobRunRegistryTest {
    private class Run(val txn: PlaybackTxn)

    @Test fun oldCompletionCannotRemoveReplacementRunForSameWidgetJobId() {
        val registry = PlaybackJobRunRegistry<Run>()
        val old = Run(PlaybackTxn(171, 40, 8, 0, 8, 170, 1000))
        val replacement = Run(PlaybackTxn(171, 41, 8, 0, 8, 170, 1100))
        registry.put(171, old)
        registry.put(171, replacement)

        assertFalse(registry.remove(171, old))
        assertSame(replacement, registry.current(171))
        assertTrue(registry.remove(171, replacement))
        assertNull(registry.current(171))
    }

    @Test fun oldStopCallbackCannotTakeReplacementWithDifferentTransactionGeneration() {
        val registry = PlaybackJobRunRegistry<Run>()
        val old = Run(PlaybackTxn(172, 7, 3, 0, 8, 170, 2000))
        val replacement = Run(PlaybackTxn(172, 8, 4, 0, 8, 170, 2100))
        registry.put(172, replacement)

        assertNull(registry.removeIf(172) {
            it.txn.widgetId == old.txn.widgetId &&
                it.txn.transactionId == old.txn.transactionId &&
                it.txn.renderGeneration == old.txn.renderGeneration
        })
        assertSame(replacement, registry.current(172))

        assertSame(replacement, registry.removeIf(172) {
            it.txn.widgetId == replacement.txn.widgetId &&
                it.txn.transactionId == replacement.txn.transactionId &&
                it.txn.renderGeneration == replacement.txn.renderGeneration
        })
        assertNull(registry.current(172))
    }
}
