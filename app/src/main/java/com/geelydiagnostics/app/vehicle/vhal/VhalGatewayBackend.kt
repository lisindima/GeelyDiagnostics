package com.geelydiagnostics.app.vehicle.vhal

enum class VhalGatewayBackend(
    val title: String,
    val description: String,
) {
    CAR_PROPERTY_MANAGER(
        title = "Car API",
        description = "CarPropertyManager через системный CarService",
    ),
    HIDL(
        title = "HIDL",
        description = "Прямой VHAL 2.0 для старых систем Geely",
    ),
}
