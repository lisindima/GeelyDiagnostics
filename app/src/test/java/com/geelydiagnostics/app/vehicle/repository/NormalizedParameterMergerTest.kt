package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.ApiSupportStatus
import com.geelydiagnostics.app.ApiValue
import com.geelydiagnostics.app.SensorRecord
import com.geelydiagnostics.app.VehicleDataSource
import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizedParameterMergerTest {
    @Test
    fun mergesOnlyMatchingPropertyIdsAndPrefersReadableVhal() {
        val ecarx = record(
            id = 1050880,
            source = VehicleDataSource.ECARX,
            propertyId = 10021,
            value = ApiValue("750 об/мин", "750"),
        )
        val vhal = record(
            id = 0x214080A3,
            source = VehicleDataSource.VHAL,
            propertyId = 10021,
            value = ApiValue("748 об/мин", "1496"),
        )
        val unknown = record(
            id = 0x21400401,
            source = VehicleDataSource.VHAL,
            propertyId = null,
            value = ApiValue.raw("[1, 2]"),
        )

        val result = NormalizedParameterMerger.merge(listOf(ecarx, vhal, unknown))
        val rpm = result.single { it.propertyId == 10021 }

        assertEquals(2, result.size)
        assertEquals(VehicleDataSource.VHAL, rpm.source)
        assertEquals("1496", rpm.value.raw)
        assertEquals(listOf(VehicleDataSource.VHAL, VehicleDataSource.ECARX), rpm.sourceReadings.map { it.source })
        assertEquals(unknown, result.single { it.propertyId == null }.copy(sourceReadings = emptyList()))
    }

    @Test
    fun fallsBackToEcarxWhenVhalIsNotReadable() {
        val ecarx = record(1, VehicleDataSource.ECARX, 10001, ApiValue("42 км/ч", "42"))
        val vhal = record(
            id = 2,
            source = VehicleDataSource.VHAL,
            propertyId = 10001,
            value = ApiValue.unavailable,
            support = ApiSupportStatus.ERROR,
        )

        val result = NormalizedParameterMerger.merge(listOf(vhal, ecarx)).single()

        assertEquals(VehicleDataSource.ECARX, result.source)
        assertEquals("42", result.value.raw)
        assertEquals(2, result.sourceReadings.size)
    }

    private fun record(
        id: Int,
        source: VehicleDataSource,
        propertyId: Int?,
        value: ApiValue,
        support: ApiSupportStatus = ApiSupportStatus.ACTIVE,
    ) = SensorRecord(
        id = id,
        apiName = "signal_$id",
        title = "Параметр",
        value = value,
        valueKind = "int",
        support = support,
        source = source,
        sourceProfile = if (source == VehicleDataSource.VHAL) "G426" else null,
        propertyId = propertyId,
        decoded = propertyId != null && support == ApiSupportStatus.ACTIVE,
    )
}
