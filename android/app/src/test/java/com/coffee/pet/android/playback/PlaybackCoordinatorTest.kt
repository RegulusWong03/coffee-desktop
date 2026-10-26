package com.coffee.pet.android.playback

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class PlaybackCoordinatorTest {
    private class MemoryRepository : PlaybackRepository {
        val records = mutableMapOf<Int, PlaybackRecord>()
        @Synchronized override fun read(widgetId: Int) = records[widgetId]
        @Synchronized override fun write(widgetId: Int, record: PlaybackRecord) { records[widgetId] = record }
        @Synchronized override fun delete(widgetId: Int, tombstone: PlaybackRecord) { records[widgetId] = tombstone }
    }

    private class RecordingRenderer : PlaybackRenderer {
        val frames = mutableListOf<Pair<Int, Int?>>()
        val hostSequences = mutableListOf<List<Long>>()
        var idleBarrier: CountDownLatch? = null
        var releaseIdle: CountDownLatch? = null
        @Synchronized override fun render(widgetId: Int, imageResource: Int?) {
            frames += widgetId to imageResource
            if (imageResource == null && idleBarrier != null) {
                idleBarrier!!.countDown()
                releaseIdle!!.await(2, TimeUnit.SECONDS)
            }
        }
        @Synchronized override fun renderHostSequence(widgetId: Int, actionRow: Int, transactionId: Long, intervalMs: Int) {
            hostSequences += listOf(widgetId.toLong(), actionRow.toLong(), transactionId, intervalMs.toLong())
        }
    }

    private fun setup(now: AtomicLong = AtomicLong(100)) = Triple(MemoryRepository(), RecordingRenderer(), now).let { (repo, renderer, clock) ->
        val coordinator = PlaybackCoordinator(repo, renderer, clock::get)
        Triple(coordinator, renderer, repo)
    }

    @Test fun busyClicksDropAndDifferentWidgetsPlayIndependently() {
        val (c, _, _) = setup()
        c.register(7); c.register(8)
        val a = c.begin(7, 3)!!
        assertNull(c.begin(7, 4))
        val b = c.begin(8, 2)!!
        assertTrue(c.complete(a)); assertTrue(c.complete(b))
    }

    @Test fun repeatedProviderRegistrationDoesNotClearAnActivePlayback() {
        val (c, _, repo) = setup()
        c.register(44)
        val active = c.begin(44, 5)!!
        val before = repo.read(44)!!
        c.register(44, "provider_onUpdate")
        val after = repo.read(44)!!
        assertTrue(after.busy)
        assertEquals(before.transactionId, after.transactionId)
        assertEquals(before.renderGeneration, after.renderGeneration)
        assertNull(c.begin(44, 3))
        assertTrue(c.complete(active))
    }

    @Test fun providerRegistrationRecoversExpiredBusyState() {
        val clock = AtomicLong(100)
        val (c, _, repo) = setup(clock)
        c.register(45)
        val active = c.begin(45, 2)!!
        clock.set(100 + PlaybackPolicy.TRANSACTION_DEADLINE_MS + 1)
        assertTrue(c.register(45, "provider_restore"))
        val state = repo.read(45)!!
        assertFalse(state.busy)
        assertEquals(active.transactionId + 1, state.transactionId)
        assertFalse(c.complete(active))
    }

    @Test fun configUpdatesForNextActionWithoutInvalidatingCurrentTransaction() {
        val (c, _, repo) = setup()
        c.register(1)
        val current = c.begin(1, 3)!!
        assertTrue(c.saveNextConfig(1, 5, 260))
        assertEquals(170, current.intervalMs)
        assertTrue(c.complete(current))
        val next = c.begin(1, 5)!!
        assertEquals(260, next.intervalMs)
        assertEquals(1, next.configVersionSnapshot)
        val saved = repo.read(1)!!
        assertEquals(1, saved.nextConfigVersion)
        assertEquals(5, saved.actionRow)
        assertEquals(260, saved.intervalMs)
    }

    @Test fun hostSequenceUsesSameCurrentTransactionGateAsFrameRendering() {
        val (c, renderer, _) = setup()
        c.register(9)
        assertTrue(c.saveNextConfig(9, -1, 300))
        val txn = c.begin(9, 6)!!
        assertEquals(300, txn.intervalMs)
        assertTrue(c.renderHostSequence(txn))
        assertEquals(listOf(listOf(9L, 6L, txn.transactionId, 300L)), renderer.hostSequences)
        assertTrue(c.cancel(9))
        assertFalse(c.renderHostSequence(txn))
        assertFalse(c.complete(txn))
    }

    @Test fun cancellationAndDeleteRejectLateFramesAndCompletions() {
        val (c, renderer, _) = setup()
        c.register(2)
        val canceled = c.begin(2, 3)!!
        assertTrue(c.cancel(2))
        assertFalse(c.renderFrame(canceled, 300))
        assertFalse(c.complete(canceled))
        val deleted = c.begin(2, 4)!!
        c.delete(2)
        val before = renderer.frames.size
        assertFalse(c.renderFrame(deleted, 400))
        assertFalse(c.complete(deleted))
        assertNull(c.begin(2, 1))
        assertEquals(before, renderer.frames.size)
    }

    @Test fun oldFrameCannotCommitAfterConcurrentCancel() {
        val (c, renderer, _) = setup()
        c.register(3)
        val txn = c.begin(3, 1)!!
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        renderer.idleBarrier = entered; renderer.releaseIdle = release
        val cancelThread = Thread { c.cancel(3) }
        cancelThread.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        val frameResult = booleanArrayOf(true)
        val frameThread = Thread { frameResult[0] = c.renderFrame(txn, 77) }
        frameThread.start()
        release.countDown()
        cancelThread.join(1000); frameThread.join(1000)
        assertFalse(frameResult[0])
        assertFalse(renderer.frames.any { it.second == 77 }) // stale frame was never committed
    }

    @Test fun clickDuringIdleCommitIsDroppedAndOnlyLaterClickStarts() {
        val (c, renderer, repo) = setup()
        c.register(4)
        val first = c.beginConfigured(4, 2, -1, 170)!!
        val beforeBusyClick = repo.read(4)!!
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        renderer.idleBarrier = entered; renderer.releaseIdle = release
        val finish = Thread { c.complete(first) }
        finish.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        val nextResult = arrayOfNulls<PlaybackTxn>(1)
        val nextClickDone = CountDownLatch(1)
        val nextClick = Thread { nextResult[0] = c.beginConfigured(4, 3, 5, 260); nextClickDone.countDown() }
        nextClick.start()
        assertTrue("busy click must return immediately", nextClickDone.await(200, TimeUnit.MILLISECONDS))
        assertNull(nextResult[0])
        val unchanged = repo.read(4)!!
        assertEquals(first.transactionId, unchanged.transactionId)
        assertEquals(1, unchanged.nextConfigVersion)
        assertEquals(beforeBusyClick.actionRow, unchanged.actionRow)
        assertEquals(170, unchanged.intervalMs)
        release.countDown(); finish.join(1000); nextClick.join(1000)
        assertEquals(4, renderer.frames.last().first)
        assertNull(renderer.frames.last().second)
        val fresh = c.beginConfigured(4, 3, 5, 260)
        assertNotNull("a fresh click after the idle commit may start", fresh)
        assertEquals(260, fresh!!.intervalMs)
        assertEquals(2, fresh.configVersionSnapshot)
        assertEquals(3, repo.read(4)!!.actionRow)
    }

    @Test fun expiredOrRebootedProcessStateRecoversIdleAndInvalidatesLateCallback() {
        val clock = AtomicLong(100)
        val (c, renderer, _) = setup(clock)
        c.register(5)
        val old = c.begin(5, 3)!!
        clock.set(PlaybackPolicy.TRANSACTION_DEADLINE_MS + 101)
        val fresh = c.begin(5, 4)!!
        assertTrue(renderer.frames.any { it.first == 5 && it.second == null })
        assertFalse(c.complete(old))
        assertTrue(c.complete(fresh))

        val afterReboot = c.begin(5, 2, receivedAtElapsedMs = 1)!!
        assertTrue(c.complete(afterReboot))
    }
}
