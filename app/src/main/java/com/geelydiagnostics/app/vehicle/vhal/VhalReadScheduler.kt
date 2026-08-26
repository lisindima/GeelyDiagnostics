package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bounded reads with independent deadlines. A stuck Binder call cannot create unbounded threads. */
internal class VhalReadScheduler(
    private val read: (VhalPropertyConfig, Int) -> CarPropertySnapshot,
    private val unavailable: (VhalPropertyConfig, Int, String) -> CarPropertySnapshot,
    private val timeoutMillis: Long = 3_000L,
    private val concurrency: Int = 4,
) : Closeable {
    private val workers = ThreadPoolExecutor(
        concurrency, concurrency, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(concurrency),
        { task -> Thread(task, "VhalRead").apply { isDaemon = true } },
    )
    @Volatile private var closed = false

    fun readAll(configs: List<VhalPropertyConfig>, emit: (CarPropertySnapshot, Long) -> Unit) {
        val requests = ArrayDeque(configs.flatMap { config -> config.areaIds.map { Request(config, it) } })
        val completed = LinkedBlockingQueue<FutureTask<Result>>()
        val active = linkedMapOf<FutureTask<Result>, Pending>()
        try {
            while (!closed && (requests.isNotEmpty() || active.isNotEmpty())) {
                while (requests.isNotEmpty() && active.size < concurrency && !closed) {
                    val request = requests.removeFirst()
                    val submitted = System.nanoTime()
                    val task = object : FutureTask<Result>({
                        val started = System.nanoTime()
                        Result(read(request.config, request.areaId), started)
                    }) {
                        override fun done() { completed.offer(this) }
                    }
                    try {
                        workers.execute(task)
                        active[task] = Pending(request, submitted)
                    } catch (error: java.util.concurrent.RejectedExecutionException) {
                        if (!closed) emit(unavailable(request.config, request.areaId, "Очередь чтения VHAL занята"), submitted)
                    }
                }
                if (active.isEmpty()) continue
                val earliest = active.values.minOf { it.submittedAtNanos }
                val remaining = timeoutMillis * 1_000_000 - (System.nanoTime() - earliest)
                val finished = completed.poll(remaining.coerceAtLeast(0), TimeUnit.NANOSECONDS)
                if (finished != null) {
                    val pending = active.remove(finished)
                    if (pending != null && !closed) {
                        val result = runCatching { finished.get() }.getOrElse { error ->
                            Result(unavailable(pending.request.config, pending.request.areaId,
                                error.cause?.toString() ?: error.toString()), pending.submittedAtNanos)
                        }
                        emit(result.snapshot, result.startedAtNanos)
                    }
                }
                val expired = active.filterValues {
                    System.nanoTime() - it.submittedAtNanos >= timeoutMillis * 1_000_000
                }
                expired.forEach { (task, pending) ->
                    active.remove(task)
                    task.cancel(true)
                    workers.remove(task)
                    if (!closed) emit(unavailable(pending.request.config, pending.request.areaId,
                        "Таймаут чтения VHAL: $timeoutMillis мс"), pending.submittedAtNanos)
                }
            }
        } finally {
            active.keys.forEach { it.cancel(true); workers.remove(it) }
        }
    }

    override fun close() {
        closed = true
        workers.shutdownNow()
    }

    private data class Request(val config: VhalPropertyConfig, val areaId: Int)
    private data class Pending(val request: Request, val submittedAtNanos: Long)
    private data class Result(val snapshot: CarPropertySnapshot, val startedAtNanos: Long)
}
