package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.DtcRecord
import com.geelydiagnostics.app.ReadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsRepositoryTest {
    @Test
    fun keepsDtcStateSeparateFromVehicleProperties() {
        val repository = DiagnosticsRepository()
        val dtc = DtcRecord("P0001", "1", 2, 3, 4L)

        repository.reset()
        repository.updateDiagnostics(ReadStatus.AVAILABLE, "available")
        repository.updateManager(ReadStatus.AVAILABLE, "available")
        repository.updateDtcs(listOf(dtc))

        assertEquals(ReadStatus.AVAILABLE, repository.snapshot().diagnosticsStatus)
        assertEquals(ReadStatus.AVAILABLE, repository.snapshot().dtcManagerStatus)
        assertEquals(listOf(dtc), repository.snapshot().dtcs)
    }

    @Test
    fun resetStartsFreshCheckingStateAndDropsPreviousDtcs() {
        val repository = DiagnosticsRepository()
        repository.updateDtcs(listOf(DtcRecord("P0001", "1", 2, 3, 4L)))

        repository.reset()

        assertEquals(ReadStatus.CHECKING, repository.snapshot().diagnosticsStatus)
        assertEquals(ReadStatus.CHECKING, repository.snapshot().dtcManagerStatus)
        assertEquals(emptyList<DtcRecord>(), repository.snapshot().dtcs)
    }
}
