package com.geelydiagnostics.app.vehicle.vhal

import org.junit.Assert.assertEquals
import org.junit.Test

class VhalNumberFormattingTest {
    @Test
    fun floatRawUsesItsShortestDecimalRepresentation() {
        assertEquals("21.6", formatVhalNumber(21.6f))
        assertEquals("0.1", formatVhalNumber(0.1f))
        assertEquals("42", formatVhalNumber(42f))
    }

    @Test
    fun keepsIntentionalDoublePrecisionAndExactIntegers() {
        assertEquals("21.600000381469727", formatVhalNumber(21.600000381469727))
        assertEquals("42", formatVhalNumber(42))
        assertEquals("9007199254740991", formatVhalNumber(9_007_199_254_740_991L))
    }
}
