package com.geelydiagnostics.app.vehicle

import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.source.VehicleParameterDataSource
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

    @Test
    fun commonParameterSourceDoesNotExposeMutationMethods() {
        val methodNames = VehicleParameterDataSource::class.java.methods.map { it.name }
        listOf("set", "write", "clear", "delete", "reset").forEach { prefix ->
            assertFalse(
                "VehicleParameterDataSource unexpectedly exposes $prefix*: $methodNames",
                methodNames.any { it.startsWith(prefix, ignoreCase = true) },
            )
        }
    }

    @Test
    fun normalizedParameterDoesNotExposePrimarySourceSignalAsIdentity() {
        val fields = VehicleParameter::class.java.declaredFields.map { it.name }.toSet()
        assertTrue("propertyId is required", "propertyId" in fields)
        assertTrue("sourceReadings are required", "sourceReadings" in fields)
        assertFalse("raw signal id must be nested", "id" in fields)
        assertFalse("raw API name must be nested", "apiName" in fields)
        assertFalse("source must be nested", "source" in fields)
    }
}
