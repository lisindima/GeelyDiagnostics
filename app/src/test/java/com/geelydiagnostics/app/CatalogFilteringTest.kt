package com.geelydiagnostics.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFilteringTest {

    private val mapped = sensor(
        id = 0x2170901E,
        title = "Оставшееся топливо",
        value = ApiValue("41.7 л", "41700"),
        sourceProfile = "G426",
    )
    private val raw = sensor(
        id = 0x21400401,
        title = "Неизвестное свойство",
        value = ApiValue.raw("[1, 2]"),
    )
    private val changed = sensor(
        id = 0x21400402,
        title = "Передача",
        value = ApiValue("D", "3"),
        sourceProfile = "G426",
        changedSinceScan = true,
    )
    private val error = sensor(
        id = 0x21400403,
        title = "Ошибочное свойство",
        value = ApiValue.unavailable,
        support = ApiSupportStatus.ERROR,
        error = "timeout",
    )

    @Test
    fun sensorFiltersSeparateMappedRawChangedErrorsAndFavorites() {
        val records = listOf(mapped, raw, changed, error)

        assertEquals(listOf(mapped, changed), filtered(records, SensorValueFilter.DECODED))
        assertEquals(listOf(raw), filtered(records, SensorValueFilter.RAW))
        assertEquals(listOf(changed), filtered(records, SensorValueFilter.CHANGED))
        assertEquals(listOf(error), filtered(records, SensorValueFilter.ERRORS))
        assertEquals(
            listOf(mapped),
            filterSensors(
                records,
                SensorValueFilter.FAVORITES,
                "",
                setOf(mapped.favoriteKey),
            ),
        )
    }

    @Test
    fun sensorSearchMatchesMultipleTokensApiIdAndRaw() {
        val records = listOf(mapped, raw, changed)
        assertEquals(listOf(mapped), filtered(records, SensorValueFilter.ALL, "топливо 41700"))
        assertEquals(listOf(raw), filtered(records, SensorValueFilter.ALL, "0x21400401"))
    }

    @Test
    fun unifiedCatalogKeepsEcarxAndVhalRecordsTogether() {
        val ecarx = sensor(
            id = 1050112,
            title = "ECARX топливо",
            value = ApiValue("63 %", "63"),
            source = VehicleDataSource.ECARX,
        )
        val vhal = sensor(
            id = 0x2170901E,
            title = "VHAL топливо",
            value = ApiValue("41.7 л", "41700"),
            sourceProfile = "G426",
        )

        assertEquals(
            listOf(ecarx, vhal),
            filtered(listOf(vhal, ecarx), SensorValueFilter.ALL),
        )
    }

    @Test
    fun mappingFailureRemainsVisibleAsRawAndIsAlsoMarkedAsError() {
        val mappingFailure = sensor(
            id = 0x21400404,
            title = "Неизвестное значение enum",
            value = ApiValue.raw("99"),
            sourceProfile = "G426",
            error = "No mapping for 99",
            decoded = false,
        )

        assertTrue(mappingFailure in filtered(listOf(mappingFailure), SensorValueFilter.ALL))
        assertTrue(mappingFailure in filtered(listOf(mappingFailure), SensorValueFilter.RAW))
        assertTrue(mappingFailure in filtered(listOf(mappingFailure), SensorValueFilter.ERRORS))
    }

    @Test
    fun vehicleAndFunctionCatalogsSupportFavoritesAndErrors() {
        val info = VehicleInfoRecord(
            id = 1,
            apiName = "STRING_INFO_VIN",
            title = "VIN",
            value = ApiValue.raw("TEST"),
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
        records: List<SensorRecord>,
        filter: SensorValueFilter,
        query: String = "",
    ) = filterSensors(records, filter, query, emptySet())

    private fun sensor(
        id: Int,
        title: String,
        value: ApiValue,
        sourceProfile: String? = null,
        changedSinceScan: Boolean = false,
        support: ApiSupportStatus = ApiSupportStatus.ACTIVE,
        error: String = "",
        decoded: Boolean? = null,
        source: VehicleDataSource = VehicleDataSource.VHAL,
    ) = SensorRecord(
        id = id,
        apiName = "VHAL_0x${id.toUInt().toString(16)}",
        title = title,
        value = value,
        valueKind = "raw",
        support = support,
        error = error,
        source = source,
        sourceProfile = sourceProfile,
        changedSinceScan = changedSinceScan,
        decoded = decoded,
    )
}
