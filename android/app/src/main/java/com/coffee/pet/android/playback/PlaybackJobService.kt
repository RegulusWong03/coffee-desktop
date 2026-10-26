package com.coffee.pet.android.playback

import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.Future

class PlaybackJobService : JobService() {
    private class RunningJob(val params: JobParameters, val txn: PlaybackTxn, @Volatile var future: Future<*>? = null)

    override fun onStartJob(params: JobParameters): Boolean {
        val extras = params.extras
        val widgetId = extras.getInt(PlaybackReceiver.KEY_WIDGET_ID, -1)
        val txnId = extras.getLong(PlaybackReceiver.KEY_TRANSACTION_ID, -1)
        val generation = extras.getLong(PlaybackReceiver.KEY_RENDER_GENERATION, -1)
        val row = extras.getInt(PlaybackReceiver.KEY_ACTION_ROW, -1)
        val interval = extras.getInt(PlaybackReceiver.KEY_INTERVAL_MS, -1)
        val startedAt = extras.getLong(PlaybackReceiver.KEY_STARTED_AT, 0)
        if (widgetId < 0 || txnId < 0 || generation < 0 || row !in 1..8 || interval !in PlaybackPolicy.DEFAULT_INTERVAL_MS..PlaybackPolicy.MAX_INTERVAL_MS || startedAt <= 0) {
            Log.e(TAG, "reject malformed job jobId=${params.jobId}")
            return false
        }

        val txn = PlaybackTxn(
            widgetId = widgetId,
            transactionId = txnId,
            renderGeneration = generation,
            configVersionSnapshot = extras.getLong(PlaybackReceiver.KEY_CONFIG_VERSION, 0),
            actionRow = row,
            intervalMs = interval,
            startedAtElapsedMs = startedAt,
        )
        val coordinator = PlaybackRuntime.get(this)
        val age = SystemClock.elapsedRealtime() - startedAt
        val expedited = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || params.isExpeditedJob
        if (!expedited || age !in 0..PlaybackPolicy.JOB_START_DEADLINE_MS) {
            val idle = coordinator.abort(txn)
            Log.w(TAG, "reject job widget=$widgetId txn=$txnId expedited=$expedited age=${age}ms idle=$idle")
            return false
        }

        val mode = if (extras.getBoolean(PlaybackReceiver.KEY_HOST_ADVANCE, false)) P0PlaybackMode.HOST_ADVANCE else P0PlaybackMode.PER_FRAME
        Log.i(TAG, "job-start widget=$widgetId txn=$txnId jobId=${params.jobId} expedited=$expedited startup=${age}ms mode=$mode row=$row")
        val run = RunningJob(params, txn)
        activeRuns.put(params.jobId, run)
        run.future = EXECUTOR.submit { runPlayback(run, mode, coordinator) }
        return true
    }

    private fun runPlayback(run: RunningJob, mode: P0PlaybackMode, coordinator: PlaybackCoordinator) {
        val params = run.params
        val txn = run.txn
        val receivedAt = txn.startedAtElapsedMs
        val frames = FrameCatalog.counts[txn.actionRow]
        try {
            if (mode == P0PlaybackMode.HOST_ADVANCE) {
                if (!coordinator.renderHostSequence(txn)) {
                    Log.i(TAG, "job-stale widget=${txn.widgetId} txn=${txn.transactionId} stage=host")
                    return
                }
                Log.i(TAG, "host-sequence widget=${txn.widgetId} txn=${txn.transactionId} elapsed=${SystemClock.elapsedRealtime() - receivedAt}ms")
                val budget = PlaybackPolicy.TRANSACTION_DEADLINE_MS - (SystemClock.elapsedRealtime() - receivedAt)
                if (budget <= 0) coordinator.abort(txn)
                else {
                    Thread.sleep(minOf(frames.toLong() * txn.intervalMs, budget))
                    if (SystemClock.elapsedRealtime() - receivedAt >= PlaybackPolicy.TRANSACTION_DEADLINE_MS) coordinator.abort(txn)
                    else {
                        val idle = coordinator.complete(txn)
                        Log.i(TAG, "idle widget=${txn.widgetId} txn=${txn.transactionId} committed=$idle elapsed=${SystemClock.elapsedRealtime() - receivedAt}ms")
                    }
                }
            } else {
                for (col in 0 until frames) {
                    if (SystemClock.elapsedRealtime() - receivedAt >= PlaybackPolicy.TRANSACTION_DEADLINE_MS) {
                        coordinator.abort(txn)
                        Log.w(TAG, "deadline widget=${txn.widgetId} txn=${txn.transactionId}")
                        return
                    }
                    if (!coordinator.renderFrame(txn, FrameCatalog.frame(txn.actionRow, col))) {
                        Log.i(TAG, "job-stale widget=${txn.widgetId} txn=${txn.transactionId} stage=frame col=$col")
                        return
                    }
                    Log.i(TAG, "frame widget=${txn.widgetId} txn=${txn.transactionId} row=${txn.actionRow} col=$col elapsed=${SystemClock.elapsedRealtime() - receivedAt}ms")
                    Thread.sleep(txn.intervalMs.toLong())
                }
                val idle = coordinator.complete(txn)
                Log.i(TAG, "idle widget=${txn.widgetId} txn=${txn.transactionId} committed=$idle elapsed=${SystemClock.elapsedRealtime() - receivedAt}ms")
            }
            Log.i(TAG, "end widget=${txn.widgetId} txn=${txn.transactionId} mode=$mode row=${txn.actionRow} elapsed=${SystemClock.elapsedRealtime() - receivedAt}ms")
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            coordinator.abort(txn)
            Log.i(TAG, "job-interrupted widget=${txn.widgetId} txn=${txn.transactionId}")
        } catch (failure: Exception) {
            coordinator.abort(txn)
            Log.e(TAG, "job-failed widget=${txn.widgetId} txn=${txn.transactionId}", failure)
        } finally {
            if (activeRuns.remove(params.jobId, run)) jobFinished(params, false)
        }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val widgetId = params.extras.getInt(PlaybackReceiver.KEY_WIDGET_ID, -1)
        val txnId = params.extras.getLong(PlaybackReceiver.KEY_TRANSACTION_ID, -1)
        val generation = params.extras.getLong(PlaybackReceiver.KEY_RENDER_GENERATION, -1)
        val run = activeRuns.removeIf(params.jobId) {
            it.txn.widgetId == widgetId && it.txn.transactionId == txnId && it.txn.renderGeneration == generation
        } ?: run {
            Log.i(TAG, "job-stop-stale jobId=${params.jobId} widget=$widgetId txn=$txnId generation=$generation")
            return false
        }
        run.future?.cancel(true)
        val txn = run.txn
        val idle = PlaybackRuntime.get(this).cancel(txn)
        Log.w(TAG, "job-stopped widget=${txn.widgetId} txn=${txn.transactionId} idle=$idle retry=false")
        return false
    }

    companion object {
        private const val TAG = "CoffeeP0"
        private val EXECUTOR = Executors.newCachedThreadPool()
        private val activeRuns = PlaybackJobRunRegistry<RunningJob>()
    }
}

object PlaybackJobScheduler {
    fun cancel(context: android.content.Context, widgetId: Int) {
        (context.getSystemService(android.content.Context.JOB_SCHEDULER_SERVICE) as JobScheduler).cancel(widgetId)
    }
}
