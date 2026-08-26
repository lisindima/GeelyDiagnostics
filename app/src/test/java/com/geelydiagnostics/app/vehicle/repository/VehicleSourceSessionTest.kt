package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.vehicle.ecarx.EcarxDataListener
import com.geelydiagnostics.app.vehicle.property.*
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleSourceSessionTest {
    @Test fun lateValuesStatusesAndLogsFromOldScanAreIgnored() {
        val delivered = mutableListOf<String>()
        val sink = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(EcarxDataListener::class.java)) { _, method, _ ->
            delivered += method.name
            null
        } as EcarxDataListener
        val monitor = Any()
        val previous = VehicleSourceSession(sink, monitor)
        val current = VehicleSourceSession(sink, monitor)
        val value = CarPropertySnapshot(propertyId = null, value = null, displayValue = "old",
            rawValue = null, status = VehiclePropertyStatus.ERROR, source = VehiclePropertySource.VHAL,
            sourceSignalId = 1, sourceSignalName = "raw", receivedAtMillis = 1)
        previous.onParameterValue(value)
        previous.close()
        previous.onParameterValue(value)
        previous.onParameterStatus(VehiclePropertySource.VHAL, ReadStatus.ERROR, "old")
        previous.onParameterDiscovery(VehiclePropertySource.VHAL, VehicleDiscoveryProgress())
        previous.onCarStatus(ReadStatus.ERROR, "old")
        previous.onLog("old", null)
        previous.onObd2Snapshot(com.geelydiagnostics.app.model.Obd2Snapshot())
        previous.onDiagnosticDetails(com.geelydiagnostics.app.model.EcarxDiagnosticDetails())
        current.onParameterValue(value.copy(displayValue = "new"))
        assertEquals(listOf("onParameterValue", "onParameterValue"), delivered)
    }
}
