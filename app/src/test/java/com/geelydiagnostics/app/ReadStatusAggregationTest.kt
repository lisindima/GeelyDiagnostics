package com.geelydiagnostics.app

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
}
