package com.geelydiagnostics.app

import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.VehicleSourceReading
import com.geelydiagnostics.app.vehicle.property.favoriteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFilteringTest {

    private val mapped = parameter(
        id = 0x2170901E,
        title = "Оставшееся топливо",
        value = VehicleDisplayValue("41.7 л", "41700"),
        sourceProfile = "G426",
        propertyId = 10012,
        decoded = true,
    )
    private val raw = parameter(
        id = 0x21400401,
        title = "Неизвестное свойство",
        value = VehicleDisplayValue.raw("[1, 2]"),
    )
    private val changed = parameter(
        id = 0x21400402,
        title = "Передача",
        value = VehicleDisplayValue("D", "3"),
        sourceProfile = "G426",
        propertyId = 10003,
        changedSinceScan = true,
        decoded = true,
    )
    private val error = parameter(
        id = 0x21400403,
        title = "Ошибочное свойство",
        value = VehicleDisplayValue.unavailable,
        status = VehiclePropertyStatus.ERROR,
        error = "timeout",
    )

    @Test
    fun parameterFiltersSeparateMappedRawChangedErrorsAndFavorites() {
        val records = listOf(mapped, raw, changed, error)

        assertEquals(listOf(mapped, changed), filtered(records, ParameterValueFilter.DECODED))
        assertEquals(listOf(raw), filtered(records, ParameterValueFilter.RAW))
        assertEquals(listOf(changed), filtered(records, ParameterValueFilter.CHANGED))
        assertEquals(listOf(error), filtered(records, ParameterValueFilter.ERRORS))
        assertEquals(
            listOf(mapped),
            filterParameters(
                records,
                ParameterValueFilter.FAVORITES,
                "",
                setOf(mapped.favoriteKey),
            ),
        )
    }

    @Test
    fun parameterSearchMatchesMultipleTokensApiIdAndRaw() {
        val records = listOf(mapped, raw, changed)
        assertEquals(listOf(mapped), filtered(records, ParameterValueFilter.ALL, "топливо 41700"))
        assertEquals(listOf(raw), filtered(records, ParameterValueFilter.ALL, "0x21400401"))
        assertEquals(listOf(mapped), filtered(records, ParameterValueFilter.ALL, "10012"))
    }

    @Test
    fun searchIncludesAllReadingsOfUnifiedParameter() {
        val combined = mapped.copy(
            sourceReadings = listOf(
                VehicleSourceReading(
                    source = VehiclePropertySource.VHAL,
                    signalId = mapped.sourceReadings.single().signalId,
                    signalName = mapped.sourceReadings.single().signalName,
                    value = mapped.value,
                    status = VehiclePropertyStatus.AVAILABLE,
                    profile = "G426",
                    decoded = true,
                ),
                VehicleSourceReading(
                    source = VehiclePropertySource.ECARX,
                    signalId = 1050112,
                    signalName = "SENSOR_TYPE_FUEL_LEVEL",
                    value = VehicleDisplayValue("63 %", "63"),
                    status = VehiclePropertyStatus.AVAILABLE,
                    decoded = true,
                ),
            ),
        )

        assertEquals(listOf(combined), filtered(listOf(combined), ParameterValueFilter.ALL, "ECARX 1050112"))
    }

    @Test
    fun mappingFailureRemainsVisibleAsRawAndIsAlsoMarkedAsError() {
        val mappingFailure = parameter(
            id = 0x21400404,
            title = "Неизвестное значение enum",
            value = VehicleDisplayValue.raw("99"),
            sourceProfile = "G426",
            error = "No mapping for 99",
            decoded = false,
        )

        assertTrue(mappingFailure in filtered(listOf(mappingFailure), ParameterValueFilter.ALL))
        assertTrue(mappingFailure in filtered(listOf(mappingFailure), ParameterValueFilter.RAW))
        assertTrue(mappingFailure in filtered(listOf(mappingFailure), ParameterValueFilter.ERRORS))
    }

    @Test
    fun vehicleAndFunctionCatalogsSupportFavoritesAndErrors() {
        val info = VehicleInfoRecord(
            id = 1,
            apiName = "STRING_INFO_VIN",
            title = "VIN",
            value = VehicleDisplayValue.raw("TEST"),
            support = ApiSupportStatus.ACTIVE,
        )
        val functionError = VehicleFunctionRecord(
            id = 2,
            apiName = "SETTING_FUNC_TEST",
            title = "Тестовая функция",
            support = ApiSupportStatus.ERROR,
            error = "denied",
        )

        assertEquals(
            listOf(info),
            filterVehicleInfo(listOf(info), CatalogListFilter.FAVORITES, "vin", setOf(info.favoriteKey)),
        )
        assertEquals(
            listOf(functionError),
            filterFunctions(listOf(functionError), CatalogListFilter.ERRORS, "", emptySet()),
        )
    }

    private fun filtered(
        records: List<VehicleParameter>,
        filter: ParameterValueFilter,
        query: String = "",
    ) = filterParameters(records, filter, query, emptySet())

    private fun parameter(
        id: Int,
        title: String,
        value: VehicleDisplayValue,
        sourceProfile: String? = null,
        changedSinceScan: Boolean = false,
        status: VehiclePropertyStatus = VehiclePropertyStatus.AVAILABLE,
        error: String = "",
        decoded: Boolean? = null,
        source: VehiclePropertySource = VehiclePropertySource.VHAL,
        propertyId: Int? = null,
    ) = VehicleParameter(
        propertyId = propertyId?.let(::CarPropertyId),
        areaId = 0,
        title = title,
        value = value,
        valueKind = "raw",
        status = status,
        error = error,
        changedSinceScan = changedSinceScan,
        decoded = decoded ?: (propertyId != null),
        sourceReadings = listOf(
            VehicleSourceReading(
                source = source,
                signalId = id,
                signalName = "VHAL_0x${id.toUInt().toString(16)}",
                value = value,
                status = status,
                error = error,
                profile = sourceProfile,
                decoded = decoded ?: (propertyId != null),
            ),
        ),
    )
}
