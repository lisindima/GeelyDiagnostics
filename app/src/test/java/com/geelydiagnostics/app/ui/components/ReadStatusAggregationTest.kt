package com.geelydiagnostics.app.ui.components

import com.geelydiagnostics.app.model.*

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadStatusAggregationTest {
    @Test
    fun reportsPartialWhenOnlyOneSourceIsAvailable() {
        assertEquals(
            ReadStatus.PARTIAL,
            aggregateReadStatus(listOf(ReadStatus.AVAILABLE, ReadStatus.ERROR)),
        )
        assertEquals(
            ReadStatus.AVAILABLE,
            aggregateReadStatus(listOf(ReadStatus.AVAILABLE, ReadStatus.AVAILABLE)),
        )
    }

    @Test
    fun statusCardKeepsOnlyDetailsThatNeedAttention() {
        assertEquals("", statusAttentionDetail(ReadStatus.AVAILABLE, "21 из 102"))
        assertEquals("", statusAttentionDetail(ReadStatus.CHECKING, "getSensorManager()"))
        assertEquals(
            "часть каталога недоступна",
            statusAttentionDetail(ReadStatus.PARTIAL, "часть каталога недоступна"),
        )
        assertEquals(
            "ECARX API unavailable",
            statusAttentionDetail(ReadStatus.ERROR, "ECARX API unavailable"),
        )
    }
}
