package com.coffee.pet.android.playback

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

data class PlaybackTxn(val widgetId: Int, val transactionId: Long, val renderGeneration: Long, val configVersionSnapshot: Long, val actionRow: Int, val intervalMs: Int, val startedAtElapsedMs: Long)
data class PlaybackRecord(
    val exists: Boolean = true,
    val busy: Boolean = false,
    val transactionId: Long = 0,
    val renderGeneration: Long = 0,
    val nextConfigVersion: Long = 0,
    val actionRow: Int = -1,
    val intervalMs: Int = PlaybackPolicy.DEFAULT_INTERVAL_MS,
    val startedAtElapsedMs: Long = 0,
)

object PlaybackPolicy {
    const val DEFAULT_INTERVAL_MS = 170
    const val MAX_INTERVAL_MS = 300
    const val TRANSACTION_DEADLINE_MS = 5_000L
    const val JOB_START_DEADLINE_MS = 1_500L
}

interface PlaybackRepository {
    fun read(widgetId: Int): PlaybackRecord?
    fun write(widgetId: Int, record: PlaybackRecord)
    fun delete(widgetId: Int, tombstone: PlaybackRecord)
}

interface PlaybackRenderer {
    /** A null resource commits the static idle frame. Called under the per-instance coordinator lock. */
    fun render(widgetId: Int, imageResource: Int?)
    /** Install a host-advanced finite sequence, including its terminal idle item. */
    fun renderHostSequence(widgetId: Int, actionRow: Int, transactionId: Long, intervalMs: Int)
}

/** Production receiver and providers share this tested commit boundary. */
class PlaybackCoordinator(
    private val repository: PlaybackRepository,
    private val renderer: PlaybackRenderer,
    private val elapsedRealtime: () -> Long,
    private val trace: (String) -> Unit = {},
) {
    private val locks = ConcurrentHashMap<Int, ReentrantLock>()
    private fun lockFor(widgetId: Int) = locks.computeIfAbsent(widgetId) { ReentrantLock() }
    private fun <T> withWidget(widgetId: Int, block: () -> T): T {
        val lock = lockFor(widgetId)
        lock.lock()
        try { return block() } finally { lock.unlock() }
    }
    private fun <T> tryWithWidget(widgetId: Int, block: () -> T): T? {
        val lock = lockFor(widgetId)
        if (!lock.tryLock()) return null
        try { return block() } finally { lock.unlock() }
    }

    fun register(widgetId: Int, reason: String = "provider_update"): Boolean = withWidget(widgetId) {
        val old = repository.read(widgetId) ?: PlaybackRecord(exists = false)
        if (old.exists) {
            val age = elapsedRealtime() - old.startedAtElapsedMs
            if (old.busy && age in 0..PlaybackPolicy.TRANSACTION_DEADLINE_MS) {
                trace("register-preserved widget=$widgetId reason=$reason busy=true txn=${old.transactionId} age=${age}ms")
                return@withWidget false
            }
            if (old.busy) {
                val next = old.copy(busy = false, transactionId = old.transactionId + 1, renderGeneration = old.renderGeneration + 1, startedAtElapsedMs = 0)
                repository.write(widgetId, next)
                trace("register-expired widget=$widgetId reason=$reason age=${age}ms txn=${old.transactionId} nextTxn=${next.transactionId}")
                renderer.render(widgetId, null)
                return@withWidget true
            }
            trace("register-preserved widget=$widgetId reason=$reason busy=false txn=${old.transactionId} generation=${old.renderGeneration}")
            renderer.render(widgetId, null)
            return@withWidget false
        }
        val next = old.copy(exists = true, busy = false, transactionId = old.transactionId + 1, renderGeneration = old.renderGeneration + 1, startedAtElapsedMs = 0)
        repository.write(widgetId, next)
        trace("register-new widget=$widgetId reason=$reason txn=${next.transactionId} generation=${next.renderGeneration}")
        renderer.render(widgetId, null)
        true
    }

    /** Click admission never waits behind an in-flight frame or idle commit; a contended click is dropped. */
    fun begin(widgetId: Int, actionRow: Int, receivedAtElapsedMs: Long = elapsedRealtime()): PlaybackTxn? = tryWithWidget(widgetId) {
        beginLocked(widgetId, actionRow, receivedAtElapsedMs, null)
    }

    /** Atomically admit a click and snapshot the next-action configuration under the same nonblocking lock. */
    fun beginConfigured(
        widgetId: Int,
        actionRow: Int,
        configuredActionRow: Int,
        intervalMs: Int,
        receivedAtElapsedMs: Long = elapsedRealtime(),
    ): PlaybackTxn? {
        if ((configuredActionRow != -1 && configuredActionRow !in 1..8) || intervalMs !in PlaybackPolicy.DEFAULT_INTERVAL_MS..PlaybackPolicy.MAX_INTERVAL_MS) return null
        return tryWithWidget(widgetId) {
            beginLocked(widgetId, actionRow, receivedAtElapsedMs, configuredActionRow to intervalMs)
        }
    }

    private fun beginLocked(widgetId: Int, actionRow: Int, receivedAtElapsedMs: Long, nextConfig: Pair<Int, Int>?): PlaybackTxn? {
        if (actionRow !in 1..8) return null
        var state = repository.read(widgetId) ?: return null
        if (!state.exists) return null
        if (state.busy) {
            val age = receivedAtElapsedMs - state.startedAtElapsedMs
            if (age in 0..PlaybackPolicy.TRANSACTION_DEADLINE_MS) return null
            // Stale receiver or elapsedRealtime reset after reboot: invalidate and visually recover before reuse.
            state = state.copy(busy = false, transactionId = state.transactionId + 1, renderGeneration = state.renderGeneration + 1, startedAtElapsedMs = 0)
            repository.write(widgetId, state)
            renderer.render(widgetId, null)
        }
        if (nextConfig != null) {
            state = state.copy(actionRow = nextConfig.first, intervalMs = nextConfig.second, nextConfigVersion = state.nextConfigVersion + 1)
            repository.write(widgetId, state)
        }
        val started = state.copy(busy = true, transactionId = state.transactionId + 1, actionRow = actionRow, startedAtElapsedMs = receivedAtElapsedMs)
        repository.write(widgetId, started)
        return PlaybackTxn(widgetId, started.transactionId, started.renderGeneration, started.nextConfigVersion, actionRow, started.intervalMs, receivedAtElapsedMs)
    }

    fun renderFrame(txn: PlaybackTxn, imageResource: Int): Boolean = withWidget(txn.widgetId) {
        if (!matches(txn, repository.read(txn.widgetId))) return@withWidget false
        renderer.render(txn.widgetId, imageResource)
        true
    }

    fun renderHostSequence(txn: PlaybackTxn): Boolean = withWidget(txn.widgetId) {
        if (!matches(txn, repository.read(txn.widgetId))) return@withWidget false
        renderer.renderHostSequence(txn.widgetId, txn.actionRow, txn.transactionId, txn.intervalMs)
        true
    }

    /** Submit idle while still busy and current; release busy only after the host update call succeeds. */
    fun complete(txn: PlaybackTxn): Boolean = withWidget(txn.widgetId) {
        val current = repository.read(txn.widgetId)
        if (!matches(txn, current)) return@withWidget false
        renderer.render(txn.widgetId, null)
        repository.write(txn.widgetId, current!!.copy(busy = false, startedAtElapsedMs = 0))
        true
    }

    fun abort(txn: PlaybackTxn): Boolean = complete(txn)

    fun cancel(widgetId: Int, reason: String = "explicit_cancel"): Boolean = withWidget(widgetId) {
        val state = repository.read(widgetId) ?: return@withWidget false
        if (!state.exists) return@withWidget false
        val next = state.copy(busy = false, transactionId = state.transactionId + 1, renderGeneration = state.renderGeneration + 1, startedAtElapsedMs = 0)
        repository.write(widgetId, next)
        trace("cancel widget=$widgetId reason=$reason wasBusy=${state.busy} txn=${state.transactionId} nextTxn=${next.transactionId}")
        renderer.render(widgetId, null)
        true
    }

    /** A stopped job may only invalidate the transaction it owns, never a newer click. */
    fun cancel(txn: PlaybackTxn): Boolean = withWidget(txn.widgetId) {
        val state = repository.read(txn.widgetId)
        if (!matches(txn, state)) return@withWidget false
        val next = state!!.copy(busy = false, transactionId = state.transactionId + 1, renderGeneration = state.renderGeneration + 1, startedAtElapsedMs = 0)
        repository.write(txn.widgetId, next)
        renderer.render(txn.widgetId, null)
        true
    }

    fun saveNextConfig(widgetId: Int, actionRow: Int, intervalMs: Int): Boolean = withWidget(widgetId) {
        val s = repository.read(widgetId) ?: return@withWidget false
        if (!s.exists || (actionRow != -1 && actionRow !in 1..8) || intervalMs !in PlaybackPolicy.DEFAULT_INTERVAL_MS..PlaybackPolicy.MAX_INTERVAL_MS) return@withWidget false
        repository.write(widgetId, s.copy(actionRow = actionRow, intervalMs = intervalMs, nextConfigVersion = s.nextConfigVersion + 1))
        true
    }

    fun delete(widgetId: Int) = withWidget(widgetId) {
        val state = repository.read(widgetId) ?: PlaybackRecord(exists = false)
        // Keep a compact generation tombstone so an appWidgetId reused by the host cannot match a late callback.
        repository.delete(widgetId, state.copy(exists = false, busy = false, actionRow = -1, startedAtElapsedMs = 0, transactionId = state.transactionId + 1, renderGeneration = state.renderGeneration + 1))
    }

    private fun matches(txn: PlaybackTxn, state: PlaybackRecord?): Boolean = state != null && state.exists && state.busy &&
        state.transactionId == txn.transactionId && state.renderGeneration == txn.renderGeneration && state.startedAtElapsedMs == txn.startedAtElapsedMs

}
