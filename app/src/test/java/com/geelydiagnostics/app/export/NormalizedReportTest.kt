package com.geelydiagnostics.app.export

import com.geelydiagnostics.app.model.AppUiState
import com.geelydiagnostics.app.vehicle.mapping.*
import com.geelydiagnostics.app.vehicle.property.*
import com.geelydiagnostics.app.vehicle.repository.UnifiedParameterCache
import com.geelydiagnostics.app.vehicle.ecarx.EcarxNormalizedValueDecoder
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NormalizedReportTest {
    private val assets = sequenceOf(File("src/main/assets/vehicle"), File("app/src/main/assets/vehicle"))
        .first(File::isDirectory)
    private val catalog = File(assets, "car_properties.json").inputStream().use(::JsonCarPropertyCatalog)

    @Test fun reportPreservesTypedFuelTransformAndActualBackendPerReading() {
        val mapping = File(assets, "profiles/G426.json").inputStream().use {
            JsonVehicleProfileMappingLoader().load(VehicleProfile.G426, it)
        }.forSignal(561025054)!!
        val snapshot = MappedPropertyDecoder(catalog).decode(mapping, RawVehicleValue("41700", 41700.0),
            mapping.signalId, mapping.signalName, 0, "G426", 123, 100, false).copy(backend = "HIDL")
        val cache = UnifiedParameterCache()
        cache.update(snapshot)
        val record = export(cache).getJSONArray("parameters").getJSONObject(0)
        assertEquals(10012, record.getInt("normalizedPropertyId"))
        assertEquals(41.7, record.getDouble("normalizedValue"), 0.000001)
        assertEquals("FLOAT", record.getString("normalizedValueType"))
        val source = record.getJSONArray("sources").getJSONObject(0)
        assertEquals("HIDL", source.getString("backend")) // not inferred from the UI default backend
        assertTrue(source.getBoolean("primary"))
        assertEquals("PROFILE", source.getString("mappingOrigin"))
        assertEquals("41700", source.getString("raw"))
        assertEquals("л", source.getString("unit"))
        assertEquals(41.7, source.getDouble("normalizedValue"), 0.000001)
        val transform = source.getJSONObject("readTransform")
        assertEquals("x / 1000", transform.getJSONArray("steps").getJSONObject(0).getString("expression"))
        assertEquals(mapping.transform, ReadTransformParser.parse(transform))
    }

    @Test fun canonicalValueAndBothSourceValuesSurviveMergeAndExport() {
        val speed = ReadSignalMapping(CarPropertyId.VEHICLE_SPEED, 1, "speed")
        val vhal = MappedPropertyDecoder(catalog).decode(speed, RawVehicleValue("36", 36.0),
            1, "speed", 0, "G426", 1, 100, false).copy(backend = "HIDL")
        val ecarx = EcarxNormalizedValueDecoder(catalog).decode("SENSOR_TYPE_CAR_SPEED", 2,
            RawVehicleValue("10.1", 10.1), 101)!!
        val cache = UnifiedParameterCache()
        cache.update(vhal)
        cache.update(ecarx)
        val record = export(cache).getJSONArray("parameters").getJSONObject(0)
        assertEquals(36.0, record.getDouble("normalizedValue"), 0.000001)
        val sources = record.getJSONArray("sources")
        assertEquals(2, sources.length())
        assertEquals(36.36, sources.getJSONObject(1).getDouble("normalizedValue"), 0.000001)
        assertFalse(sources.getJSONObject(1).getBoolean("primary"))
        assertEquals("ECARX", sources.getJSONObject(1).getString("mappingOrigin"))
    }

    @Test fun unknownGearIsNotExportedAsCanonicalEncodedInteger() {
        val cache = UnifiedParameterCache()
        cache.update(EcarxNormalizedValueDecoder(catalog).decode("SENSOR_TYPE_GEAR", 2,
            RawVehicleValue("999", 999.0), 100)!!)
        val record = export(cache).getJSONArray("parameters").getJSONObject(0)
        assertTrue(record.isNull("normalizedValue"))
        assertFalse(record.getBoolean("decoded"))
        assertEquals("999", record.getString("raw"))
    }

    private fun export(cache: UnifiedParameterCache) = JSONObject(DiagnosticsReportExporter.create(
        AppUiState(parameters = cache.parameters(200)), 200, "test",
    ))
}
