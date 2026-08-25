package com.geelydiagnostics.app.vehicle.vhal

import org.junit.Assert.assertEquals
import org.junit.Test

class CarPropertyManagerValueTest {
    @Test
    fun preservesNumbersFromBoxedArraysReturnedByCarPropertyManager() {
        val raw = arrayOf(1715, 4510, 1865).toRawVehicleValue()

        assertEquals("[1715, 4510, 1865]", raw.text)
        assertEquals(listOf(1715.0, 4510.0, 1865.0), raw.numbers)
    }

    @Test
    fun preservesExactFloatTextAndNumericVector() {
        val raw = floatArrayOf(21.6f, 22.25f).toRawVehicleValue()

        assertEquals("[21.6, 22.25]", raw.text)
        assertEquals(listOf(21.6, 22.25), raw.numbers)
    }
}
