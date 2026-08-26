package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.model.*
import org.junit.Assert.*
import org.junit.Test

class Obd2Test {
    @Test fun mixedMaskSkipsAbsentSlotsAndKeepsVendorOffset() {
        val raw = Obd2RawPayload(int32Values = listOf(0, 99, 7), floatValues = listOf(0.0, 36.5),
            bytes = listOf(0b00010001), stringValue = "P0123")
        val frame = raw.decodeFrame(9007199254740993L)
        assertEquals(mapOf(0 to 0), frame.integers)
        assertEquals(mapOf(1 to 36.5), frame.floats)
        assertEquals(9007199254740993L, frame.timestampNanos)
        assertEquals("P0123", frame.dtc)
        assertSame(raw, frame.raw)
    }

    @Test fun shortMaskDoesNotInventValidZeros() {
        val frame = Obd2RawPayload(int32Values = listOf(0), bytes = emptyList()).decodeFrame(1)
        assertTrue(frame.error.isNotBlank())
        assertTrue(frame.integers.isEmpty())
    }

    @Test fun carDiagnosticJsonKeepsSparseIndicesAndExactTimestamp() {
        val frame = decodeCarDiagnosticJson("""{"type":"freeze","timestamp":9007199254740993,
            "intValues":[{"id":120,"value":0}],"floatValues":[{"id":8,"value":748.5}],"stringValue":"P0123"}""")
        assertEquals(9007199254740993L, frame.timestampNanos)
        assertEquals(mapOf(120 to 0), frame.integers)
        assertEquals(mapOf(8 to 748.5), frame.floats)
        assertEquals("P0123", frame.dtc)
    }

    @Test fun unsupportedPropertiesAreNotReadOrSubscribed() {
        val gateway = FakeGateway(emptySet())
        val states = mutableListOf<Obd2Snapshot>()
        Obd2Reader(gateway, states::add, {}).use { it.scan(emptyList()) }
        assertTrue(gateway.calls.isEmpty())
        assertEquals(3, states.last().capabilities.size)
    }

    @Test fun liveFailureDoesNotBlockFreezeAndEachTimestampIsUsedExactly() {
        val gateway = FakeGateway(Obd2Properties.readable.toSet()).apply { failLive = true }
        val states = mutableListOf<Obd2Snapshot>()
        Obd2Reader(gateway, states::add, {}).use { it.scan(emptyList()) }
        assertEquals(listOf(9007199254740993L, 9007199254740994L), gateway.requests)
        val result = states.last()
        assertEquals(ReadStatus.ERROR, result.capabilities.single { it.propertyId == Obd2Properties.LIVE }.status)
        assertEquals("P0123", result.freezeFrames.last().dtc)
        assertEquals(9007199254740994L, result.freezeFrames.last().requestedTimestampNanos)
        assertTrue(result.freezeFrames.first().error.contains("expired"))
        assertFalse(gateway.calls.any { it.contains("clear", true) })
    }

    @Test fun callbackWinsOverSlowInitialLiveRead() {
        val gateway = FakeGateway(setOf(Obd2Properties.LIVE)).apply { emitDuringRead = true }
        val states = mutableListOf<Obd2Snapshot>()
        val reader = Obd2Reader(gateway, states::add, {})
        reader.scan(emptyList())
        assertEquals(2L, states.last().live?.timestampNanos)
        val count = states.size
        reader.close()
        gateway.listener?.invoke(Obd2Frame(3))
        assertEquals(count, states.size)
    }

    @Test fun freezeCountIsBoundedButFullTimestampListIsRetained() {
        val gateway = FakeGateway(setOf(Obd2Properties.INFO, Obd2Properties.FREEZE)).apply {
            timestamps = (1L..100L).toList()
        }
        val states = mutableListOf<Obd2Snapshot>()
        Obd2Reader(gateway, states::add, {}).use { it.scan(emptyList()) }
        assertEquals(32, gateway.requests.size)
        assertEquals(100, states.last().freezeTimestamps.size)
    }

    @Test fun freezeWithoutInfoDoesNotGuessTimestamp() {
        val gateway = FakeGateway(setOf(Obd2Properties.FREEZE))
        val states = mutableListOf<Obd2Snapshot>()
        Obd2Reader(gateway, states::add, {}).use { it.scan(emptyList()) }
        assertTrue(gateway.requests.isEmpty())
        assertTrue(states.last().capabilities.single { it.propertyId == Obd2Properties.FREEZE }.detail.contains("INFO"))
    }

    private class FakeGateway(private val supported: Set<Int>) : Obd2Gateway {
        override val backend = "TEST"
        val calls = mutableListOf<String>()
        val requests = mutableListOf<Long>()
        var failLive = false
        var emitDuringRead = false
        var listener: ((Obd2Frame) -> Unit)? = null
        var timestamps = listOf(9007199254740993L, 9007199254740994L)
        override fun discover(configs: List<VhalPropertyConfig>) = Obd2Properties.readable.map { Obd2Capability(it, it in supported) }
        override fun readLive(): Obd2Frame {
            calls += "live"
            if (failLive) error("permission denied")
            if (emitDuringRead) listener?.invoke(Obd2Frame(2))
            return Obd2Frame(1)
        }
        override fun readTimestamps(): List<Long> { calls += "info"; return timestamps }
        override fun readFreeze(timestamp: Long): Obd2Frame {
            calls += "freeze"; requests += timestamp
            if (timestamp == 9007199254740993L) error("expired")
            return Obd2Frame(timestamp, dtc = "P0123")
        }
        override fun subscribeLive(onFrame: (Obd2Frame) -> Unit): Boolean { calls += "subscribe"; listener = onFrame; return true }
        override fun close() = Unit
    }
}
