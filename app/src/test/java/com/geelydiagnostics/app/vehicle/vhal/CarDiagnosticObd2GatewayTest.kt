package com.geelydiagnostics.app.vehicle.vhal

import org.junit.Assert.*
import org.junit.Test

class CarDiagnosticObd2GatewayTest {
    @Test fun capabilitiesDoNotInvokeReadsAndSubscriptionUsesAndroid11Rate() {
        val manager = Manager()
        val gateway = CarDiagnosticObd2Gateway({ manager })
        assertTrue(gateway.discover(emptyList()).all { it.supported == true })
        assertTrue(manager.calls.isEmpty())
        assertTrue(gateway.subscribeLive {})
        assertEquals(listOf("subscribe:0:1"), manager.calls)
        assertEquals(listOf(9007199254740993L), gateway.readTimestamps())
        gateway.close()
        assertEquals("unsubscribe", manager.calls.last())
    }

    @Test fun missingReadPermissionPreventsAllReadsAndSubscription() {
        val manager = Manager()
        val gateway = CarDiagnosticObd2Gateway({ manager }, { throw SecurityException("denied") })
        gateway.discover(emptyList())
        listOf<() -> Unit>({ gateway.readLive() }, { gateway.readTimestamps() },
            { gateway.readFreeze(123) }, { gateway.subscribeLive {} }).forEach { call ->
            assertTrue(runCatching(call).exceptionOrNull() is SecurityException)
        }
        assertTrue(manager.calls.isEmpty())
        gateway.close()
    }

    interface Listener { fun onDiagnosticEvent(event: Any) }
    class Manager {
        val calls = mutableListOf<String>()
        fun isLiveFrameSupported() = true
        fun isGetFreezeFrameSupported() = true
        fun registerListener(listener: Listener, type: Int, rate: Int): Boolean {
            calls += "subscribe:$type:$rate"
            assertTrue(listener == listener)
            return true
        }
        fun unregisterListener(listener: Listener) { calls += "unsubscribe"; assertNotNull(listener) }
        fun getFreezeFrameTimestamps(): LongArray { calls += "timestamps"; return longArrayOf(9007199254740993L) }
    }
}
