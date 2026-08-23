package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.vehicle.mapping.ReadSignalMapping
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfileMapping
import com.geelydiagnostics.app.vehicle.property.CarPropertyCatalog
import com.geelydiagnostics.app.vehicle.property.CarPropertyDefinition
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.CarValueType
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VhalDataSourceTest {
    @Test
    fun readsMappedAndUnknownValuesAndClassifiesSubscriptions() {
        val gateway = FakeGateway(
            configs = listOf(
                config(SPEED_SIGNAL_ID, dynamic = true),
                config(UNKNOWN_SIGNAL_ID, dynamic = false),
                VhalPropertyConfig(UNREADABLE_SIGNAL_ID, 0, 0, listOf(0)),
            ),
            initialValues = mapOf(
                (SPEED_SIGNAL_ID to 0) to value(SPEED_SIGNAL_ID, "36", 36.0, 10L),
                (UNKNOWN_SIGNAL_ID to 0) to value(UNKNOWN_SIGNAL_ID, "[1, 2]", null, 20L),
            ),
        )
        val listener = RecordingListener()
        val source = VhalDataSource(
            profile = VehicleProfile.G426,
            listener = listener,
            mapping = VehicleProfileMapping(
                VehicleProfile.G426,
                listOf(ReadSignalMapping(CarPropertyId.VEHICLE_SPEED, SPEED_SIGNAL_ID, "speed")),
            ),
            catalog = catalog(
                CarPropertyDefinition(
                    CarPropertyId.VEHICLE_SPEED,
                    CarValueType.INT,
                    "speed",
                ),
            ),
            gateway = gateway,
        )

        try {
            source.start()
            assertTrue("initial snapshot timeout", listener.snapshotLatch.await(2, TimeUnit.SECONDS))

            val mapped = listener.snapshot.single { it.sourceSignalId == SPEED_SIGNAL_ID }
            val unknown = listener.snapshot.single { it.sourceSignalId == UNKNOWN_SIGNAL_ID }
            val unreadable = listener.snapshot.single { it.sourceSignalId == UNREADABLE_SIGNAL_ID }
            assertEquals(CarPropertyId.VEHICLE_SPEED, mapped.id)
            assertEquals("36 км/ч", mapped.displayValue)
            assertTrue(mapped.autoUpdates)
            assertNull(unknown.id)
            assertEquals("[1, 2]", unknown.rawValue?.text)
            assertFalse(unknown.autoUpdates)
            assertEquals(VehiclePropertyStatus.ERROR, unreadable.status)
            assertNull(unreadable.rawValue)
            assertTrue(unreadable.error.contains("не помечено доступным"))
            assertEquals(setOf(SPEED_SIGNAL_ID), gateway.subscribedIds)
        } finally {
            source.close()
        }
        assertTrue(gateway.closed)
    }

    @Test
    fun forwardsLiveValueWithSourceAndReceiveTimestampsSeparated() {
        val gateway = FakeGateway(
            configs = listOf(config(SPEED_SIGNAL_ID, dynamic = true)),
            initialValues = mapOf(
                (SPEED_SIGNAL_ID to 0) to value(SPEED_SIGNAL_ID, "36", 36.0, 10L),
            ),
        )
        val listener = RecordingListener()
        val source = VhalDataSource(
            VehicleProfile.G426,
            listener,
            VehicleProfileMapping(
                VehicleProfile.G426,
                listOf(ReadSignalMapping(CarPropertyId.VEHICLE_SPEED, SPEED_SIGNAL_ID, "speed")),
            ),
            catalog(CarPropertyDefinition(CarPropertyId.VEHICLE_SPEED, CarValueType.INT, "speed")),
            gateway,
        )

        try {
            source.start()
            assertTrue(listener.snapshotLatch.await(2, TimeUnit.SECONDS))
            val beforeEvent = System.currentTimeMillis()
            gateway.emit(value(SPEED_SIGNAL_ID, "37", 37.0, 999L))
            assertTrue("live value timeout", listener.valueLatch.await(2, TimeUnit.SECONDS))

            assertEquals("37 км/ч", listener.liveValue?.displayValue)
            assertEquals("37", listener.liveValue?.rawValue?.text)
            assertEquals(999L, listener.liveValue?.sourceTimestampNanos)
            assertTrue(requireNotNull(listener.liveValue).receivedAtMillis >= beforeEvent)
            assertTrue(requireNotNull(listener.liveValue).autoUpdates)
        } finally {
            source.close()
        }
    }

    private class RecordingListener : VhalDataListener {
        val snapshotLatch = CountDownLatch(1)
        val valueLatch = CountDownLatch(1)
        var snapshot = emptyList<CarPropertySnapshot>()
        var liveValue: CarPropertySnapshot? = null

        override fun onVhalStatus(status: SourceReadStatus, detail: String) = Unit

        override fun onVhalSnapshot(values: List<CarPropertySnapshot>) {
            snapshot = values
            snapshotLatch.countDown()
        }

        override fun onVhalValue(value: CarPropertySnapshot) {
            liveValue = value
            valueLatch.countDown()
        }

        override fun onVehicleLog(message: String, error: Throwable?) = Unit
    }

    private class FakeGateway(
        private val configs: List<VhalPropertyConfig>,
        private val initialValues: Map<Pair<Int, Int>, VhalPropertyValue>,
    ) : VhalGateway {
        private var callback: ((VhalPropertyValue) -> Unit)? = null
        var subscribedIds = emptySet<Int>()
            private set
        var closed = false
            private set

        override fun connect() = Unit

        override fun readConfigs(): List<VhalPropertyConfig> = configs

        override fun read(propertyId: Int, areaId: Int): VhalPropertyValue =
            requireNotNull(initialValues[propertyId to areaId])

        override fun subscribe(
            configs: List<VhalPropertyConfig>,
            onValue: (VhalPropertyValue) -> Unit,
        ): Set<Int> {
            callback = onValue
            subscribedIds = configs.filter(VhalPropertyConfig::dynamic)
                .map(VhalPropertyConfig::propertyId)
                .toSet()
            return subscribedIds
        }

        fun emit(value: VhalPropertyValue) {
            requireNotNull(callback)(value)
        }

        override fun close() {
            closed = true
        }
    }

    companion object {
        private const val SPEED_SIGNAL_ID = 0x00400001
        private const val UNKNOWN_SIGNAL_ID = 0x00400002
        private const val UNREADABLE_SIGNAL_ID = 0x00400003

        private fun config(propertyId: Int, dynamic: Boolean) = VhalPropertyConfig(
            propertyId = propertyId,
            access = 1,
            changeMode = if (dynamic) 2 else 0,
            areaIds = listOf(0),
        )

        private fun value(
            propertyId: Int,
            raw: String,
            number: Double?,
            timestampNanos: Long,
        ) = VhalPropertyValue(
            propertyId = propertyId,
            areaId = 0,
            raw = RawVehicleValue(raw, number),
            sourceTimestampNanos = timestampNanos,
        )

        private fun catalog(vararg definitions: CarPropertyDefinition): CarPropertyCatalog =
            object : CarPropertyCatalog {
                private val byId = definitions.associateBy(CarPropertyDefinition::id)

                override fun definition(id: CarPropertyId): CarPropertyDefinition? = byId[id]

                override fun all(): List<CarPropertyDefinition> = definitions.toList()
            }
    }
}
