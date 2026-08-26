package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.vehicle.mapping.ReadSignalMapping
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfileMapping
import com.geelydiagnostics.app.vehicle.property.CarPropertyCatalog
import com.geelydiagnostics.app.vehicle.property.CarPropertyDefinition
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.CarValueType
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.VehicleDiscoveryProgress
import com.geelydiagnostics.app.vehicle.property.VehicleMappingOrigin
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import com.geelydiagnostics.app.vehicle.source.VehicleParameterSink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VhalDataSourceTest {
    @Test fun obd2PropertiesNeverEnterGenericReadsOrSubscriptions() {
        val reads = java.util.concurrent.CopyOnWriteArrayList<Int>()
        val gateway = FakeGateway(Obd2Properties.all.map { config(it, dynamic = true) }, emptyMap(), reads::add)
        val listener = RecordingListener()
        val source = mappedSource(listener, gateway)
        try {
            source.start()
            assertTrue(listener.snapshotLatch.await(2, TimeUnit.SECONDS))
            assertTrue(reads.isEmpty())
            assertTrue(gateway.subscribedIds.isEmpty())
        } finally { source.close() }
    }

    @Test
    fun knownAospPropertyWithoutNormalizedMappingUsesProfile() {
        val ignitionId = 289_475_088 // HW_KEY_INPUT
        val gateway = FakeGateway(
            configs = listOf(config(ignitionId, dynamic = true)),
            initialValues = mapOf(
                (ignitionId to 0) to value(ignitionId, "[0, 1, 2]", null, 10L),
            ),
        )
        val listener = RecordingListener()
        val source = VhalDataSource(
            profile = VehicleProfile.G426,
            listener = listener,
            mapping = VehicleProfileMapping(
                VehicleProfile.G426,
                listOf(ReadSignalMapping(CarPropertyId(10037), ignitionId, "HW_KEY_INPUT")),
            ),
            catalog = catalog(
                CarPropertyDefinition(CarPropertyId(10037), CarValueType.STRING, "key event"),
            ),
            gateway = gateway,
        )

        try {
            source.start()
            assertTrue(listener.snapshotLatch.await(2, TimeUnit.SECONDS))

            val ignition = listener.snapshot.single()
            assertEquals(CarPropertyId(10037), ignition.propertyId)
            assertEquals("HW_KEY_INPUT", ignition.sourceSignalName)
            assertEquals("[0, 1, 2]", ignition.displayValue)
            assertEquals(CarValue.StringValue("[0, 1, 2]"), ignition.value)
            assertNull(ignition.sourceDescription)
            assertEquals("G426", ignition.profileKey)
            assertEquals(VehicleMappingOrigin.PROFILE, ignition.mappingOrigin)
            assertEquals("HIDL", ignition.backend)
            assertTrue(ignition.decoded)
        } finally {
            source.close()
        }
    }

    @Test
    fun appliesAndroidRegistryBeforeVehicleProfileMapping() {
        val androidSpeedId = 0x11600207
        val gateway = FakeGateway(
            configs = listOf(config(androidSpeedId, dynamic = true)),
            initialValues = mapOf(
                (androidSpeedId to 0) to value(androidSpeedId, "10", 10.0, 10L),
            ),
        )
        val listener = RecordingListener()
        val source = VhalDataSource(
            profile = VehicleProfile.G426,
            listener = listener,
            mapping = VehicleProfileMapping(VehicleProfile.G426, listOf(
                ReadSignalMapping(CarPropertyId.GEAR, androidSpeedId, "conflicting_profile_signal"),
            )),
            catalog = catalog(
                CarPropertyDefinition(CarPropertyId.VEHICLE_SPEED, CarValueType.INT, "speed"),
            ),
            gateway = gateway,
        )

        try {
            source.start()
            assertTrue("initial snapshot timeout", listener.snapshotLatch.await(2, TimeUnit.SECONDS))

            val speed = listener.snapshot.single()
            assertEquals(CarPropertyId.VEHICLE_SPEED, speed.propertyId)
            assertEquals("36 км/ч", speed.displayValue)
            assertEquals("10", speed.rawValue?.text)
            assertEquals("PERF_VEHICLE_SPEED", speed.sourceSignalName)
            assertEquals("Скорость автомобиля", speed.sourceTitle)
            assertEquals("AOSP", speed.profileKey)
        } finally {
            source.close()
        }
    }

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
            assertEquals(CarPropertyId.VEHICLE_SPEED, mapped.propertyId)
            assertEquals("36 км/ч", mapped.displayValue)
            assertTrue(mapped.autoUpdates)
            assertNull(unknown.propertyId)
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

    @Test
    fun mappedBootstrapAndLiveEventsDoNotWaitForRawDiscovery() {
        val rawReadEntered = CountDownLatch(1)
        val releaseRaw = CountDownLatch(1)
        val liveArrived = CountDownLatch(1)
        val listener = RecordingListener { if (it.rawValue?.text == "37") liveArrived.countDown() }
        val gateway = FakeGateway(
            listOf(config(UNKNOWN_SIGNAL_ID, false), config(SPEED_SIGNAL_ID, true)),
            mapOf((SPEED_SIGNAL_ID to 0) to value(SPEED_SIGNAL_ID, "36", 36.0, 1),
                (UNKNOWN_SIGNAL_ID to 0) to value(UNKNOWN_SIGNAL_ID, "1", 1.0, 1)),
        ) { id -> if (id == UNKNOWN_SIGNAL_ID) {
            rawReadEntered.countDown()
            releaseRaw.await(2, TimeUnit.SECONDS)
        } }
        val source = mappedSource(listener, gateway)
        try {
            source.start()
            assertTrue(rawReadEntered.await(2, TimeUnit.SECONDS))
            assertTrue(listener.bootstrapLatch.await(2, TimeUnit.SECONDS))
            assertTrue(listener.progress.rawDiscoveryRunning)
            assertFalse(listener.progress.rawDiscoveryCompleted)
            assertEquals("36 км/ч", listener.snapshot.single().displayValue)
            assertTrue(SPEED_SIGNAL_ID in gateway.subscribedIds)
            gateway.emit(value(SPEED_SIGNAL_ID, "37", 37.0, 2))
            assertTrue(liveArrived.await(2, TimeUnit.SECONDS))
            releaseRaw.countDown()
            assertTrue(listener.snapshotLatch.await(2, TimeUnit.SECONDS))
            assertEquals("37 км/ч", listener.snapshot.single { it.sourceSignalId == SPEED_SIGNAL_ID }.displayValue)
            assertEquals(2, listener.snapshot.size)
        } finally { releaseRaw.countDown(); source.close() }
    }

    @Test
    fun lateInitialReadCannotOverwriteCallbackAndErrorsReachCache() {
        val readEntered = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val eventArrived = CountDownLatch(1)
        val errorArrived = CountDownLatch(1)
        val listener = RecordingListener {
            if (it.rawValue?.text == "37") eventArrived.countDown()
            if (it.status == VehiclePropertyStatus.ERROR) errorArrived.countDown()
        }
        val gateway = FakeGateway(listOf(config(SPEED_SIGNAL_ID, true)),
            mapOf((SPEED_SIGNAL_ID to 0) to value(SPEED_SIGNAL_ID, "36", 36.0, 1))) {
            readEntered.countDown()
            releaseRead.await(2, TimeUnit.SECONDS)
        }
        val source = mappedSource(listener, gateway)
        try {
            source.start()
            assertTrue(readEntered.await(2, TimeUnit.SECONDS))
            gateway.emit(value(SPEED_SIGNAL_ID, "37", 37.0, 2))
            assertTrue(eventArrived.await(2, TimeUnit.SECONDS))
            releaseRead.countDown()
            assertTrue(listener.snapshotLatch.await(2, TimeUnit.SECONDS))
            assertEquals("37 км/ч", listener.snapshot.single().displayValue)
            gateway.emit(value(SPEED_SIGNAL_ID, "—", null, 3).copy(error = "unavailable"))
            assertTrue(errorArrived.await(2, TimeUnit.SECONDS))
            assertEquals(VehiclePropertyStatus.ERROR, listener.snapshot.single().status)
        } finally { releaseRead.countDown(); source.close() }
    }

    @Test
    fun unavailableKnownAospPropertyKeepsProfileMappingIdentity() {
        val id = 289_475_088
        val listener = RecordingListener()
        val source = VhalDataSource(VehicleProfile.G426, listener,
            VehicleProfileMapping(VehicleProfile.G426, listOf(ReadSignalMapping(CarPropertyId(10037), id, "HW_KEY_INPUT"))),
            catalog(CarPropertyDefinition(CarPropertyId(10037), CarValueType.STRING, "keys")),
            FakeGateway(listOf(VhalPropertyConfig(id, 0, 0, listOf(0))), emptyMap()))
        try {
            source.start()
            assertTrue(listener.snapshotLatch.await(2, TimeUnit.SECONDS))
            val failed = listener.snapshot.single()
            assertEquals(CarPropertyId(10037), failed.propertyId)
            assertEquals("G426", failed.profileKey)
            assertEquals(VehicleMappingOrigin.PROFILE, failed.mappingOrigin)
            assertFalse(failed.decoded)
        } finally { source.close() }
    }

    private fun mappedSource(listener: RecordingListener, gateway: VhalGateway) = VhalDataSource(
        VehicleProfile.G426, listener,
        VehicleProfileMapping(VehicleProfile.G426, listOf(ReadSignalMapping(CarPropertyId.VEHICLE_SPEED, SPEED_SIGNAL_ID, "speed"))),
        catalog(CarPropertyDefinition(CarPropertyId.VEHICLE_SPEED, CarValueType.FLOAT, "speed")), gateway,
    )

    private class RecordingListener(private val observe: (CarPropertySnapshot) -> Unit = {}) : VehicleParameterSink {
        val snapshotLatch = CountDownLatch(1)
        val valueLatch = CountDownLatch(1)
        val bootstrapLatch = CountDownLatch(1)
        @Volatile var progress = VehicleDiscoveryProgress()
        @Volatile var snapshot = emptyList<CarPropertySnapshot>()
        @Volatile var liveValue: CarPropertySnapshot? = null

        override fun onParameterStatus(
            source: VehiclePropertySource,
            status: ReadStatus,
            detail: String,
        ) {
            if (status == ReadStatus.AVAILABLE) snapshotLatch.countDown()
        }

        override fun onParameterDiscovery(source: VehiclePropertySource, progress: VehicleDiscoveryProgress) {
            this.progress = progress
            if (progress.mappedBootstrapReady) bootstrapLatch.countDown()
        }

        override fun onParameterSnapshot(
            source: VehiclePropertySource,
            section: VehicleDataSection?,
            values: List<CarPropertySnapshot>,
        ) {
            snapshot = values
            snapshotLatch.countDown()
        }

        override fun onParameterValue(value: CarPropertySnapshot) {
            snapshot = snapshot.filterNot {
                it.sourceSignalId == value.sourceSignalId && it.areaId == value.areaId
            } + value
            liveValue = value
            if (snapshotLatch.count == 0L) valueLatch.countDown()
            observe(value)
        }

        override fun onParameterLog(
            source: VehiclePropertySource,
            message: String,
            error: Throwable?,
        ) = Unit
    }

    private class FakeGateway(
        private val configs: List<VhalPropertyConfig>,
        private val initialValues: Map<Pair<Int, Int>, VhalPropertyValue>,
        private val beforeRead: (Int) -> Unit = {},
    ) : VhalGateway {
        @Volatile private var callback: ((VhalPropertyValue) -> Unit)? = null
        var subscribedIds = emptySet<Int>()
            private set
        var closed = false
            private set

        override fun connect() = Unit

        override fun readConfigs(): List<VhalPropertyConfig> = configs

        override fun read(propertyId: Int, areaId: Int): VhalPropertyValue {
            beforeRead(propertyId)
            return requireNotNull(initialValues[propertyId to areaId])
        }

        override fun subscribe(
            configs: List<VhalPropertyConfig>,
            onValue: (VhalPropertyValue) -> Unit,
        ): Set<Int> {
            callback = onValue
            val added = configs.filter(VhalPropertyConfig::dynamic)
                .map(VhalPropertyConfig::propertyId)
                .toSet()
            subscribedIds += added
            return added
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
