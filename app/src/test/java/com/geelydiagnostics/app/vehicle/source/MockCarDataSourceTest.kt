package com.geelydiagnostics.app.vehicle.source

import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.repository.UnifiedParameterStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockCarDataSourceTest {
    @Test
    fun emitsInitialAndLiveValuesThroughPropertyFlow() = runBlocking {
        val store = UnifiedParameterStore()
        val statuses = mutableListOf<ReadStatus>()
        val sink = StoreSink(store) { statuses += it }
        val source = MockCarDataSource(sink, listOf(snapshot("40")))
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            store.observe(CarPropertyId.VEHICLE_SPEED)
                .filterNotNull()
                .take(2)
                .toList()
        }

        source.start()
        yield()
        source.emit(snapshot("41", receivedAtMillis = 2L))

        assertEquals(listOf("40", "41"), observed.await().map { it.value.raw })
        assertEquals(listOf(ReadStatus.CHECKING, ReadStatus.AVAILABLE), statuses)
        source.close()
    }

    @Test
    fun rejectsSnapshotsFromRealVehicleSources() {
        val source = MockCarDataSource(StoreSink(UnifiedParameterStore()))
        source.start()

        val error = runCatching {
            source.emit(snapshot("1").copy(source = VehiclePropertySource.VHAL))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        source.close()
    }

    private class StoreSink(
        private val store: UnifiedParameterStore,
        private val onStatus: (ReadStatus) -> Unit = {},
    ) : VehicleParameterSink {
        override fun onParameterStatus(
            source: VehiclePropertySource,
            status: ReadStatus,
            detail: String,
        ) = onStatus(status)

        override fun onParameterSnapshot(
            source: VehiclePropertySource,
            values: List<CarPropertySnapshot>,
        ) = store.replaceSource(source, values)

        override fun onParameterValue(value: CarPropertySnapshot) {
            store.update(value)
        }

        override fun onParameterLog(
            source: VehiclePropertySource,
            message: String,
            error: Throwable?,
        ) = Unit
    }

    private fun snapshot(raw: String, receivedAtMillis: Long = 1L) = CarPropertySnapshot(
        propertyId = CarPropertyId.VEHICLE_SPEED,
        value = CarValue.IntValue(raw.toInt()),
        displayValue = "$raw км/ч",
        rawValue = RawVehicleValue(raw, raw.toDouble()),
        status = VehiclePropertyStatus.AVAILABLE,
        source = VehiclePropertySource.MOCK,
        sourceSignalId = CarPropertyId.VEHICLE_SPEED.rawValue,
        sourceSignalName = "MOCK_VEHICLE_SPEED",
        sourceTitle = "Скорость автомобиля",
        receivedAtMillis = receivedAtMillis,
        autoUpdates = true,
        valueKind = "int",
    )
}
