package com.coffee.pet.android.playback

import java.util.concurrent.ConcurrentHashMap

/** Tracks the exact running job instance so stale callbacks cannot mutate a replacement. */
class PlaybackJobRunRegistry<T : Any> {
    private val active = ConcurrentHashMap<Int, T>()

    fun put(jobId: Int, record: T) { active[jobId] = record }

    fun current(jobId: Int): T? = active[jobId]

    fun remove(jobId: Int, expected: T): Boolean = active.remove(jobId, expected)

    fun removeIf(jobId: Int, matches: (T) -> Boolean): T? {
        val record = active[jobId] ?: return null
        if (!matches(record)) return null
        return if (active.remove(jobId, record)) record else null
    }
}
