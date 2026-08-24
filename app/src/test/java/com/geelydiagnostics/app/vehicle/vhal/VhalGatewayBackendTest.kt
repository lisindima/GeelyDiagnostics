package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.model.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class VhalGatewayBackendTest {
    @Test
    fun carPropertyManagerIsDefaultWhileHidlRemainsSelectable() {
        assertEquals(VhalGatewayBackend.CAR_PROPERTY_MANAGER, AppUiState().selectedVhalBackend)
        assertEquals(
            listOf(VhalGatewayBackend.CAR_PROPERTY_MANAGER, VhalGatewayBackend.HIDL),
            VhalGatewayBackend.entries,
        )
    }
}
