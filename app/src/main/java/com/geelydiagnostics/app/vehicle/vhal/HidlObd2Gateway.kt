package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.model.*

internal class HidlObd2Gateway(private val vehicle: HidlVhalGateway) : Obd2Gateway {
    override val backend = "HIDL"
    private var configs: List<VhalPropertyConfig> = emptyList()
    override fun discover(configs: List<VhalPropertyConfig>): List<Obd2Capability> {
        this.configs = configs
        return Obd2Properties.readable.map { id ->
            val config = configs.firstOrNull { it.propertyId == id }
            Obd2Capability(id, config?.readable == true, detail = when {
                config == null -> "Свойство не объявлено в каталоге VHAL"
                !config.readable -> "Свойство не разрешено читать"
                else -> "Объявлено в VHAL; чтение ещё не завершено"
            })
        }
    }
    override fun readLive(): Obd2Frame = vehicle.read(Obd2Properties.LIVE, 0).frame()
    override fun readTimestamps(): List<Long> = vehicle.read(Obd2Properties.INFO, 0).let {
        check(it.error == null) { it.error.orEmpty() }
        requireNotNull(it.obd2Payload) { "OBD2 int64 payload missing" }.int64Values
    }
    override fun readFreeze(timestamp: Long): Obd2Frame = vehicle.readFreezeFrame(timestamp).frame()
    override fun subscribeLive(onFrame: (Obd2Frame) -> Unit): Boolean =
        Obd2Properties.LIVE in vehicle.subscribe(configs.filter { it.propertyId == Obd2Properties.LIVE }) {
            onFrame(it.frame())
        }
    override fun close() = Unit // The owning VhalDataSource closes all subscriptions and the shared vehicle.

    private fun VhalPropertyValue.frame(): Obd2Frame = if (error != null) Obd2Frame(sourceTimestampNanos, error = error)
        else requireNotNull(obd2Payload) { "OBD2 mixed payload missing" }.decodeFrame(sourceTimestampNanos)
}
