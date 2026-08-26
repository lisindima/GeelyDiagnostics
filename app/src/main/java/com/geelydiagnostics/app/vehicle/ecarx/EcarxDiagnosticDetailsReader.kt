package com.geelydiagnostics.app.vehicle.ecarx

import com.geelydiagnostics.app.model.*
import java.lang.reflect.InvocationTargetException

/** Only two allowlisted getters are invoked. Extended diagnostics/monitor APIs are inspected, never called. */
internal object EcarxDiagnosticDetailsReader {
    fun read(diagnostics: Any, dtcManager: Any?): EcarxDiagnosticDetails {
        val apis = listOf("getPartInfoManager", "getDiagMonitor").map { inspect(diagnostics, it) } +
            listOf("getDtcInfos", "diagGetDTCData", "diagReadInfoFromHal", "diagSubscribe",
                "diagUnsubscribe", "diagReadClientsReport").map { inspect(dtcManager, it) }
        return try {
            val manager = diagnostics.javaClass.getMethod("getPartInfoManager").invoke(diagnostics)
                ?: error("getPartInfoManager() returned null")
            val getter = manager.javaClass.getMethod("getPartInfoString", Int::class.javaPrimitiveType)
            val values = fields.map { field ->
                try {
                    field.copy(value = getter.invoke(manager, field.id) as? String)
                } catch (error: Throwable) {
                    field.copy(error = errorText(error))
                }
            }
            EcarxDiagnosticDetails(
                partInfoStatus = when {
                    values.all { it.error.isNotEmpty() } -> ReadStatus.ERROR
                    values.any { it.error.isNotEmpty() } -> ReadStatus.PARTIAL
                    else -> ReadStatus.AVAILABLE
                },
                partInfoDetail = "${values.count { !it.value.isNullOrBlank() }} из ${values.size} полей заполнено",
                parts = values, apis = apis,
            )
        } catch (error: Throwable) {
            EcarxDiagnosticDetails(ReadStatus.ERROR, errorText(error), apis = apis)
        }
    }

    private fun inspect(target: Any?, name: String): DiagnosticApiInfo {
        if (target == null) return DiagnosticApiInfo(name, null, detail = "Менеджер недоступен")
        return try {
            val signatures = target.javaClass.methods.filter { it.name == name }
                .map { it.toGenericString() }.sorted()
            DiagnosticApiInfo(name, signatures.isNotEmpty(), signatures, "Только наличие метода; не проверка работоспособности")
        } catch (error: Throwable) {
            DiagnosticApiInfo(name, null, detail = errorText(error))
        }
    }

    private fun errorText(error: Throwable): String {
        val cause = (error as? InvocationTargetException)?.targetException ?: error
        return "${cause.javaClass.name}: ${cause.message.orEmpty()}"
    }

    // Existing IPartInfos contract; no probing of unknown numeric keys.
    private val fields = listOf(
        PartInfoValue(1, "PART_INFO_ECU_CORE_ASSEMBLY_NO", "Номер основной сборки"),
        PartInfoValue(2, "PART_INFO_ECU_DELIVERY_ASSEMBLY_NO", "Номер поставочной сборки"),
        PartInfoValue(3, "PART_INFO_IHU_VP_LOAD_MODULE_NO", "Модуль VP"),
        PartInfoValue(4, "PART_INFO_IHU_AP_LOAD_MODULE_NO", "Модуль AP"),
        PartInfoValue(5, "PART_INFO_IHU_VP_LOCAL_CONFIG_NO", "Конфигурация VP"),
        PartInfoValue(6, "PART_INFO_IHU_AP_LOCAL_CONFIG_NO", "Конфигурация AP"),
        PartInfoValue(7, "PART_INFO_IHU_POST_BUILD_NO", "Номер post-build"),
    )
}
