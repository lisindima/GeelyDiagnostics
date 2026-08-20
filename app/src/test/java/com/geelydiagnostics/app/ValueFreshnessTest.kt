package com.geelydiagnostics.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueFreshnessTest {

    @Test
    fun continuousValueBecomesStaleAfterItsLimit() {
        val record = sensor(updatedAt = 10_000L, staleAfter = 15_000L)
        assertFalse(record.isStale(25_000L))
        assertTrue(record.isStale(25_001L))
    }

    @Test
    fun onChangeValueNeverLooksStaleOnlyBecauseItDidNotChange() {
        assertFalse(sensor(updatedAt = 10_000L, staleAfter = null).isStale(1_000_000L))
    }

    @Test
    fun updateLabelContainsRelativeAge() {
        assertTrue(formatUpdateTime(10_000L, 15_000L).endsWith("5 с назад"))
    }

    private fun sensor(updatedAt: Long, staleAfter: Long?) = SensorRecord(
        id = 1,
        apiName = "TEST",
        title = "Тест",
        value = ApiValue.raw("1"),
        valueKind = "int",
        support = ApiSupportStatus.ACTIVE,
        updatedAtMillis = updatedAt,
        expectedUpdateIntervalMillis = staleAfter,
    )
}
