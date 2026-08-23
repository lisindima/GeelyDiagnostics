package com.geelydiagnostics.app.vehicle.mapping

import android.content.Context
import com.geelydiagnostics.app.vehicle.property.CarPropertyCatalog
import com.geelydiagnostics.app.vehicle.property.JsonCarPropertyCatalog

internal class VehicleMetadataStore(context: Context) {
    private val assets = context.applicationContext.assets
    private val mappingLoader = JsonVehicleProfileMappingLoader()
    private val mappings = mutableMapOf<VehicleProfile, VehicleProfileMapping>()

    val properties: CarPropertyCatalog by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        assets.open(PROPERTY_CATALOG_ASSET).use { input -> JsonCarPropertyCatalog(input) }
    }

    fun mapping(profile: VehicleProfile): VehicleProfileMapping = if (profile == VehicleProfile.RAW) {
        VehicleProfileMapping.raw()
    } else synchronized(mappings) {
        mappings.getOrPut(profile) {
            assets.open("$PROFILE_ASSET_DIRECTORY/${profile.key}.json").use { input ->
                mappingLoader.load(profile, input)
            }
        }
    }

    companion object {
        private const val PROPERTY_CATALOG_ASSET = "vehicle/car_properties.json"
        private const val PROFILE_ASSET_DIRECTORY = "vehicle/profiles"
    }
}
