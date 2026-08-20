package com.geelydiagnostics.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VhalProfileRegistryTest {

    @Test
    fun rawProfileNeverAppliesAMapping() {
        assertTrue(VhalProfileRegistry.signals(VhalProfile.RAW).isEmpty())
        assertEquals(VhalProfile.RAW, AppUiState().selectedVhalProfile)
    }

    @Test
    fun everyBundledProfileCanBeParsed() {
        val expectedCounts = mapOf(
            VhalProfile.FS11_A2 to 69,
            VhalProfile.KX11_A2 to 69,
            VhalProfile.KX11_22_LSHD to 22,
            VhalProfile.KX11_24 to 69,
            VhalProfile.KX11_24_TJ to 69,
            VhalProfile.KX11_25 to 69,
            VhalProfile.FX12 to 72,
            VhalProfile.FX121_8 to 69,
            VhalProfile.FS12 to 72,
            VhalProfile.FX11 to 69,
            VhalProfile.G636 to 69,
            VhalProfile.G426 to 65,
        )
        expectedCounts.forEach { (profile, count) ->
            val signals = VhalProfileRegistry.signals(profile)
            assertEquals(profile.key, count, signals.size)
            assertTrue(signals.all { it.profile == profile })
        }
    }

    @Test
    fun g426GearMappingPreservesRaw() {
        val gear = VhalProfileRegistry.signals(VhalProfile.G426)
            .single { it.propertyId == 10003 }

        assertEquals(
            ApiValue(display = "D", raw = "3"),
            VhalProfileRegistry.decode(gear, VhalRawValue.number(3)),
        )
    }

    @Test
    fun g426FuelVolumeIsConvertedToLitresAndPreservesRaw() {
        val fuelVolume = VhalProfileRegistry.signals(VhalProfile.G426)
            .single { it.propertyId == 10012 }

        assertEquals(561025054, fuelVolume.readSignalId)
        assertEquals(
            ApiValue(display = "41.7 л", raw = "41700"),
            VhalProfileRegistry.decode(fuelVolume, VhalRawValue.number(41700)),
        )
    }

    @Test
    fun selectedProfileChangesTheLowLevelSignalMapping() {
        val g426Speed = VhalProfileRegistry.signals(VhalProfile.G426)
            .single { it.propertyId == 10001 }
        val fx12Speed = VhalProfileRegistry.signals(VhalProfile.FX12)
            .single { it.propertyId == 10001 }

        assertNotEquals(g426Speed.readSignalId, fx12Speed.readSignalId)
    }
}
