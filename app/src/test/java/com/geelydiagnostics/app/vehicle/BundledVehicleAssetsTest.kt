package com.geelydiagnostics.app.vehicle

import com.geelydiagnostics.app.vehicle.mapping.JsonVehicleProfileMappingLoader
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.JsonCarPropertyCatalog
import com.geelydiagnostics.app.vehicle.property.MappedPropertyDecoder
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledVehicleAssetsTest {
    private val assets = locateAssets()
    private val catalogFile = File(assets, "vehicle/car_properties.json")
    private val catalog by lazy {
        catalogFile.inputStream().use { input -> JsonCarPropertyCatalog(input) }
    }

    @Test
    fun bundledCatalogContainsAllVerifiedDefinitionsAndNoAccessMetadata() {
        val raw = JSONArray(catalogFile.readText())

        assertEquals(73, catalog.all().size)
        assertEquals(73, raw.length())
        for (index in 0 until raw.length()) {
            val item = raw.getJSONObject(index)
            assertFalse("property ${item.getInt("propertyId")}", item.has("access"))
        }
        assertEquals(
            "剩余油量体积-整数-升值",
            catalog.definition(CarPropertyId.REMAINING_FUEL_LITERS)?.description,
        )
    }

    @Test
    fun everyBundledProfileParsesAndReferencesKnownProperties() {
        val expectedCounts = mapOf(
            VehicleProfile.FS11_A2 to 69,
            VehicleProfile.KX11_A2 to 69,
            VehicleProfile.KX11_22_LSHD to 22,
            VehicleProfile.KX11_24 to 69,
            VehicleProfile.KX11_24_TJ to 69,
            VehicleProfile.KX11_25 to 69,
            VehicleProfile.FX12 to 72,
            VehicleProfile.FX121_8 to 69,
            VehicleProfile.FS12 to 72,
            VehicleProfile.FX11 to 69,
            VehicleProfile.G636 to 69,
            VehicleProfile.G426 to 65,
        )

        expectedCounts.forEach { (profile, expectedCount) ->
            val file = profileFile(profile)
            val mapping = file.inputStream().use { input ->
                JsonVehicleProfileMappingLoader().load(profile, input)
            }
            assertEquals(profile.key, expectedCount, mapping.mappings.size)
            mapping.mappings.forEach { signal ->
                assertNotNull(
                    "${profile.key} property ${signal.propertyId}",
                    catalog.definition(signal.propertyId),
                )
            }
        }
    }

    @Test
    fun bundledProfilesContainNoWriteSideFields() {
        VehicleProfile.entries.filterNot { it == VehicleProfile.RAW }.forEach { profile ->
            val raw = JSONArray(profileFile(profile).readText())
            for (index in 0 until raw.length()) {
                val item = raw.getJSONObject(index)
                assertFalse("${profile.key}[$index] writeSignalId", item.has("writeSignalId"))
                assertFalse("${profile.key}[$index] writeSignalName", item.has("writeSignalName"))
                assertFalse("${profile.key}[$index] writeTransform", item.has("writeTransform"))
            }
        }
    }

    @Test
    fun rawProfileHasNoMapping() {
        val raw = com.geelydiagnostics.app.vehicle.mapping.VehicleProfileMapping.raw()

        assertEquals(VehicleProfile.RAW, raw.profile)
        assertTrue(raw.mappings.isEmpty())
        assertNull(raw.forSignal(561025054))
    }

    @Test
    fun g426GearAndFuelUseVerifiedMappingsAndKeepRawValues() {
        val mapping = profileFile(VehicleProfile.G426).inputStream().use { input ->
            JsonVehicleProfileMappingLoader().load(VehicleProfile.G426, input)
        }
        val decoder = MappedPropertyDecoder(catalog)
        val gear = requireNotNull(mapping.mappings.singleOrNull {
            it.propertyId == CarPropertyId.GEAR
        })
        val fuel = requireNotNull(mapping.mappings.singleOrNull {
            it.propertyId == CarPropertyId.REMAINING_FUEL_LITERS
        })

        val decodedGear = decoder.decode(
            gear,
            RawVehicleValue("3", 3.0),
            gear.signalId,
            gear.signalName,
            0,
            "G426",
            10L,
            20L,
            true,
        )
        val decodedFuel = decoder.decode(
            fuel,
            RawVehicleValue("41700", 41700.0),
            fuel.signalId,
            fuel.signalName,
            0,
            "G426",
            30L,
            40L,
            true,
        )

        assertEquals(557874334, gear.signalId)
        assertEquals(CarValue.CharValue('D'), decodedGear.value)
        assertEquals("D", decodedGear.displayValue)
        assertEquals("3", decodedGear.rawValue?.text)
        assertEquals(561025054, fuel.signalId)
        assertEquals(CarValue.FloatValue(41.7), decodedFuel.value)
        assertEquals("41.7 л", decodedFuel.displayValue)
        assertEquals("41700", decodedFuel.rawValue?.text)
        assertEquals(30L, decodedFuel.sourceTimestampNanos)
        assertEquals(40L, decodedFuel.receivedAtMillis)
    }

    private fun profileFile(profile: VehicleProfile): File =
        File(assets, "vehicle/profiles/${profile.key}.json").also { file ->
            check(file.isFile) { "Missing bundled profile ${file.absolutePath}" }
        }

    private fun locateAssets(): File {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        return sequenceOf(
            File(workingDirectory, "src/main/assets"),
            File(workingDirectory, "app/src/main/assets"),
            File(workingDirectory.parentFile, "app/src/main/assets"),
        ).firstOrNull(File::isDirectory)
            ?: error("Cannot locate app/src/main/assets from ${workingDirectory.absolutePath}")
    }
}
