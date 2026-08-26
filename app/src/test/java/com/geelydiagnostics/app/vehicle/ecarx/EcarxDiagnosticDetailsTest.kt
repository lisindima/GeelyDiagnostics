package com.geelydiagnostics.app.vehicle.ecarx

import com.geelydiagnostics.app.model.*
import org.junit.Assert.*
import org.junit.Test

class EcarxDiagnosticDetailsTest {
    @Test fun ecuNamesMatchIecuAndUnknownValuesArePreserved() {
        assertEquals(listOf("UNKNOWN", "IHU", "CSD", "WPC", "CCSM", "AUD", "TEM2", "VCM", "PAC"),
            (0..8).map(EcarxEcuNames::name))
        assertEquals("Блок 99", EcarxEcuNames.name(99))
    }

    @Test fun partInfoFailureDoesNotHideOtherFieldsOrInvokeExtendedMethods() {
        val part = Parts()
        val result = EcarxDiagnosticDetailsReader.read(Diagnostics(part), Dtc())
        assertEquals(ReadStatus.PARTIAL, result.partInfoStatus)
        assertEquals((1..7).toList(), part.reads)
        assertEquals("value-7", result.parts.last().value)
        assertTrue(result.parts[2].error.contains("denied"))
        assertTrue(result.apis.single { it.name == "diagReadInfoFromHal" }.present == true)
        assertFalse(result.apis.single { it.name == "diagGetDTCData" }.present!!)
        assertTrue(result.apis.single { it.name == "getDiagMonitor" }.present!!)
    }

    @Test fun absentOptionalManagerDoesNotThrow() {
        val result = EcarxDiagnosticDetailsReader.read(Any(), null)
        assertEquals(ReadStatus.ERROR, result.partInfoStatus)
        assertTrue(result.parts.isEmpty())
        assertNull(result.apis.single { it.name == "getDtcInfos" }.present)
    }

    class Parts {
        val reads = mutableListOf<Int>()
        fun getPartInfoString(id: Int): String { reads += id; if (id == 3) error("denied"); return "value-$id" }
    }
    class Diagnostics(private val parts: Parts) {
        fun getPartInfoManager() = parts
        fun getDiagMonitor(): Any = error("Monitor getter must not be called")
    }
    class Dtc {
        fun getDtcInfos(): Any = error("Not part of capability inspection")
        fun diagReadInfoFromHal(did: Int): Any = error("Must not invoke DID $did")
    }
}
