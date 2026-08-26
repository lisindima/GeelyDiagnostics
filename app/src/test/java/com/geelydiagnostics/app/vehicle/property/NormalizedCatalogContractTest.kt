package com.geelydiagnostics.app.vehicle.property

import com.geelydiagnostics.app.vehicle.mapping.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class NormalizedCatalogContractTest {
    private val assets = sequenceOf(File("src/main/assets/vehicle"), File("app/src/main/assets/vehicle"))
        .first(File::isDirectory)
    private val catalog = File(assets, "car_properties.json").inputStream().use(::JsonCarPropertyCatalog)

    @Test fun fractionalMeasurementContractsAreExplicit() {
        listOf(10001, 10011, 10012, 10021, 10022, 10055, 10058, 10060, 10061, 10062).forEach { id ->
            assertEquals("property $id", CarValueType.FLOAT, catalog.definition(CarPropertyId(id))?.valueType)
        }
    }

    @Test fun everyProfileArithmeticTransformFitsDeclaredTypeAndRoundTrips() {
        VehicleProfile.entries.filterNot { it == VehicleProfile.RAW }.forEach { profile ->
            val mapping = File(assets, "profiles/${profile.key}.json").inputStream().use {
                JsonVehicleProfileMappingLoader().load(profile, it)
            }
            mapping.mappings.forEach { signal ->
                assertEquals(signal.transform, ReadTransformParser.parse(signal.transform.toJson()))
                val steps = (signal.transform as? ReadTransform.Pipeline)?.steps.orEmpty()
                if (steps.isNotEmpty() && steps.all { it is ReadTransformStep.Arithmetic }) {
                    listOf(1.0, 3.0, 41701.0).forEach { number ->
                        val result = MappedPropertyDecoder(catalog).decode(signal,
                            RawVehicleValue("$number", number), signal.signalId, signal.signalName,
                            0, profile.key, null, 100, false)
                        assertEquals("${profile.key}/${signal.propertyId}: ${result.error}",
                            VehiclePropertyStatus.AVAILABLE, result.status)
                        val expected = catalog.definition(signal.propertyId)!!.valueType
                        assertTrue("${profile.key}/${signal.propertyId}: $expected vs ${result.value}",
                            when (expected) {
                                CarValueType.FLOAT -> result.value is CarValue.FloatValue
                                CarValueType.INT -> result.value is CarValue.IntValue
                                else -> false
                            })
                    }
                }
            }
        }
    }
}
