package com.geelydiagnostics.app.vehicle.vhal

import android.util.JsonWriter
import com.geelydiagnostics.app.model.*
import java.io.StringWriter
import java.lang.reflect.Proxy
import org.json.JSONObject

/** Optional Android 11 SystemApi, accessed reflectively so absence does not break ordinary Car API. */
internal class CarDiagnosticObd2Gateway(
    private val obtainManager: () -> Any?,
    private val requireReadPermission: () -> Unit = {},
) : Obd2Gateway {
    override val backend = "CAR_DIAGNOSTIC_MANAGER"
    private var manager: Any? = null
    private var callback: Any? = null
    @Volatile private var closed = false

    override fun discover(configs: List<VhalPropertyConfig>): List<Obd2Capability> {
        val current = obtainManager() ?: error("CarDiagnosticManager недоступен")
        manager = current
        return Obd2Properties.readable.map { id ->
            try {
                val method = if (id == Obd2Properties.LIVE) "isLiveFrameSupported" else "isGetFreezeFrameSupported"
                val supported = current.javaClass.getMethod(method).invoke(current) as Boolean
                Obd2Capability(id, supported, detail = if (supported) "Поддерживается CarDiagnosticManager" else "Не предоставляется CarDiagnosticManager")
            } catch (error: Throwable) {
                Obd2Capability(id, status = ReadStatus.ERROR, detail = error.cause?.toString() ?: error.toString())
            }
        }
    }
    override fun readLive(): Obd2Frame? {
        requireReadPermission()
        return invoke("getLatestLiveFrame")?.toFrame()
    }
    override fun readTimestamps(): List<Long> {
        requireReadPermission()
        return (invoke("getFreezeFrameTimestamps") as? LongArray)?.toList()
            ?: error("CarDiagnosticManager did not return timestamps")
    }
    override fun readFreeze(timestamp: Long): Obd2Frame? {
        requireReadPermission()
        return requireNotNull(manager).let {
            it.javaClass.getMethod("getFreezeFrame", Long::class.javaPrimitiveType).invoke(it, timestamp)?.toFrame()
        }
    }
    override fun subscribeLive(onFrame: (Obd2Frame) -> Unit): Boolean {
        if (closed) return false
        requireReadPermission()
        val current = requireNotNull(manager)
        val method = current.javaClass.methods.first { it.name == "registerListener" && it.parameterCount == 3 }
        val listenerType = method.parameterTypes[0]
        val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, called, args ->
            when (called.name) {
                "onDiagnosticEvent" -> {
                    args?.firstOrNull()?.let { event ->
                        val frame = runCatching { event.toFrame() }.getOrElse { Obd2Frame(null, error = it.toString()) }
                        onFrame(frame)
                    }
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "ReadOnlyObd2Listener"
                else -> null
            }
        }
        callback = listener
        // Android 11: FRAME_TYPE_LIVE=0; CarSensorManager.SENSOR_RATE_NORMAL=1.
        val registered = method.invoke(current, listener, 0, 1) as Boolean
        if (closed) {
            unregister(current, listener)
            callback = null
            return false
        }
        return registered
    }
    override fun close() {
        closed = true
        val current = manager
        val listener = callback
        if (current != null && listener != null) unregister(current, listener)
        callback = null
        manager = null
    }
    private fun unregister(current: Any, listener: Any) {
        runCatching { current.javaClass.methods.first { it.name == "unregisterListener" && it.parameterCount == 1 }
            .invoke(current, listener) }
    }
    private fun invoke(name: String): Any? = requireNotNull(manager).let { it.javaClass.getMethod(name).invoke(it) }

    private fun Any.toFrame(): Obd2Frame {
        // writeToJson serializes this already-received local object. It does not call a vehicle API.
        val output = StringWriter()
        JsonWriter(output).use { writer -> javaClass.getMethod("writeToJson", JsonWriter::class.java).invoke(this, writer) }
        return decodeCarDiagnosticJson(output.toString())
    }
}

internal fun decodeCarDiagnosticJson(text: String): Obd2Frame {
    val json = JSONObject(text)
    val ints = json.getJSONArray("intValues")
    val floats = json.getJSONArray("floatValues")
    return Obd2Frame(
        timestampNanos = json.getLong("timestamp"), dtc = json.optString("stringValue", ""),
        integers = (0 until ints.length()).associate { i -> ints.getJSONObject(i).let { it.getInt("id") to it.getInt("value") } },
        floats = (0 until floats.length()).associate { i -> floats.getJSONObject(i).let { it.getInt("id") to it.getDouble("value") } },
    )
}
