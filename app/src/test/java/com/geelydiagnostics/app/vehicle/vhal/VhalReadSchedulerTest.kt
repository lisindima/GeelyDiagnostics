package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.vehicle.property.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test

class VhalReadSchedulerTest {
    @Test fun stuckReadDoesNotBlockOthersAndConcurrencyIsBounded() {
        val stuck = CountDownLatch(1)
        val completed = CopyOnWriteArrayList<CarPropertySnapshot>()
        val otherResults = CountDownLatch(5)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val scheduler = VhalReadScheduler(
            read = { config, _ ->
                val count = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, count) }
                try {
                    if (config.propertyId == 0) stuck.await()
                    snapshot(config.propertyId)
                } finally { active.decrementAndGet() }
            },
            unavailable = { config, _, error -> snapshot(config.propertyId).copy(
                status = VehiclePropertyStatus.ERROR, error = error,
            ) },
            concurrency = 2,
            timeoutMillis = 500,
        )
        val runner = Executors.newSingleThreadExecutor()
        try {
            val run = runner.submit {
                scheduler.readAll((0..5).map { VhalPropertyConfig(it, 1, 0, listOf(0)) }) { result, _ ->
                    completed += result
                    if (result.sourceSignalId != 0) otherResults.countDown()
                }
            }
            assertTrue(otherResults.await(2, TimeUnit.SECONDS))
            run.get(2, TimeUnit.SECONDS)
            assertEquals(6, completed.size)
            assertTrue(completed.single { it.sourceSignalId == 0 }.error.contains("Таймаут"))
            assertTrue(maximum.get() <= 2)
            assertTrue(completed.filter { it.sourceSignalId != 0 }.all { it.status == VehiclePropertyStatus.AVAILABLE })
        } finally { stuck.countDown(); scheduler.close(); runner.shutdownNow() }
    }

    private fun snapshot(id: Int) = CarPropertySnapshot(
        propertyId = null, value = CarValue.IntValue(id), displayValue = "$id", rawValue = RawVehicleValue("$id"),
        status = VehiclePropertyStatus.AVAILABLE, source = VehiclePropertySource.VHAL,
        sourceSignalId = id, sourceSignalName = "$id", receivedAtMillis = 1,
    )
}
