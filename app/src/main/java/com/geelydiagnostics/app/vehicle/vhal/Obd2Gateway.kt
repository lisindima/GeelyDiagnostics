package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.model.*
import java.io.Closeable

internal object Obd2Properties {
    const val LIVE = 0x11e00d00
    const val FREEZE = 0x11e00d01
    const val INFO = 0x11e00d02
    const val CLEAR = 0x11e00d03
    val readable = listOf(LIVE, FREEZE, INFO)
    val all = readable + CLEAR
    fun name(id: Int): String = when (id) {
        LIVE -> "OBD2_LIVE_FRAME"
        FREEZE -> "OBD2_FREEZE_FRAME"
        INFO -> "OBD2_FREEZE_FRAME_INFO"
        else -> "OBD2_UNKNOWN"
    }
}

/** No clearing, sessions, diagnostic commands or arbitrary requests. */
internal interface Obd2Gateway : Closeable {
    val backend: String
    fun discover(configs: List<VhalPropertyConfig>): List<Obd2Capability>
    fun readLive(): Obd2Frame?
    fun readTimestamps(): List<Long>
    fun readFreeze(timestamp: Long): Obd2Frame?
    fun subscribeLive(onFrame: (Obd2Frame) -> Unit): Boolean
}

/** AOSP bitmask covers all integer slots first (including vendor slots), then floats. */
internal fun Obd2RawPayload.decodeFrame(timestamp: Long?): Obd2Frame {
    val count = int32Values.size + floatValues.size
    if (bytes.size * 8 < count) return Obd2Frame(timestamp, stringValue, raw = this,
        error = "Неполная OBD2-маска: ${bytes.size * 8} бит для $count значений")
    fun present(index: Int) = bytes[index / 8] and (1 shl (index % 8)) != 0
    return Obd2Frame(
        timestampNanos = timestamp, dtc = stringValue,
        integers = int32Values.mapIndexedNotNull { i, v -> if (present(i)) i to v else null }.toMap(),
        floats = floatValues.mapIndexedNotNull { i, v -> if (present(int32Values.size + i)) i to v else null }.toMap(),
        raw = this,
    )
}
