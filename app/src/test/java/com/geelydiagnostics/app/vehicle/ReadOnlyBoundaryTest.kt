package com.geelydiagnostics.app.vehicle

import com.geelydiagnostics.app.vehicle.vhal.VhalGateway
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyBoundaryTest {
    @Test
    fun vhalGatewayDoesNotExposeVehicleMutationMethods() {
        val methodNames = VhalGateway::class.java.declaredMethods.map { it.name }
        val forbiddenPrefixes = listOf("set", "write", "clear", "delete", "reset")

        assertTrue("read is required", "read" in methodNames)
        assertTrue("subscribe is required", "subscribe" in methodNames)
        forbiddenPrefixes.forEach { prefix ->
            assertFalse(
                "VhalGateway unexpectedly exposes $prefix*: $methodNames",
                methodNames.any { name -> name.startsWith(prefix, ignoreCase = true) },
            )
        }
    }
}
