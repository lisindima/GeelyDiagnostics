package com.geelydiagnostics.app

import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal object ParametersTab {

    @Composable
    fun Content(
        state: AppUiState,
        onVhalProfileSelected: (VehicleProfile) -> Unit,
        onFavoriteToggle: (String) -> Unit,
    ) {
        var expandedSensorKey by rememberSaveable { mutableStateOf<String?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var selectedValueFilterIndex by rememberSaveable { mutableIntStateOf(SensorValueFilter.ALL.ordinal) }
        val selectedValueFilter = SensorValueFilter.entries[selectedValueFilterIndex]
        val nowMillis by rememberCurrentTimeMillis()
        val supported = state.sensors.filter { it.support.isVisibleAsSupported }
        val filtered = filterSensors(
            records = state.sensors,
            valueFilter = selectedValueFilter,
            query = query,
            favoriteKeys = state.favoriteKeys,
        )
        val autoUpdating = filtered.filter(SensorRecord::autoUpdates)
        val manuallyUpdated = filtered.filterNot(SensorRecord::autoUpdates)
        val groups = listOf(
            Triple("Автообновление", "Новые значения приходят по подписке", autoUpdating),
            Triple("Ручное обновление", "Значения обновляются по запросу", manuallyUpdated),
        ).filter { (_, _, values) -> values.isNotEmpty() }
        val emptyText = when {
            state.sensorStatus == ReadStatus.ERROR && state.vhalStatus == ReadStatus.ERROR ->
                "Источники данных недоступны. Подробности записаны в журнале."
            selectedValueFilter == SensorValueFilter.DECODED &&
                state.selectedVhalProfile == VehicleProfile.RAW ->
                "Для VHAL выбран RAW. Здесь останутся только значения, расшифрованные другими источниками."
            else -> "По выбранному фильтру значения не найдены."
        }

        SensorList(
            state = state,
            supportedCount = supported.size,
            displayedCount = filtered.size,
            autoUpdatingCount = autoUpdating.size,
            groups = groups,
            emptyText = emptyText,
            query = query,
            onQueryChange = { query = it },
            selectedValueFilterIndex = selectedValueFilterIndex,
            onValueFilterSelected = { selectedValueFilterIndex = it },
            favoriteKeys = state.favoriteKeys,
            nowMillis = nowMillis,
            onVhalProfileSelected = onVhalProfileSelected,
            onFavoriteToggle = onFavoriteToggle,
            onSensorSelected = { expandedSensorKey = it.selectionKey },
        )
        state.sensors.firstOrNull { it.selectionKey == expandedSensorKey }?.let { sensor ->
            FullscreenValueDialog(
                title = sensor.title,
                apiName = sensor.apiName,
                idText = sensor.cardIdLabel,
                value = sensor.value,
                sourceLabel = sensor.sourceLabel,
                modeLabel = if (sensor.autoUpdates) {
                    "АВТООБНОВЛЕНИЕ · ПОДПИСКА"
                } else {
                    "РУЧНОЕ ОБНОВЛЕНИЕ"
                },
                isFavorite = sensor.favoriteKey in state.favoriteKeys,
                onFavoriteToggle = { onFavoriteToggle(sensor.favoriteKey) },
                onDismiss = { expandedSensorKey = null },
                chart = if (sensor.chartable) {
                    {
                        SensorHistoryChart(
                            samples = state.sensorHistory[sensor.favoriteKey].orEmpty(),
                            isLive = sensor.autoUpdates,
                        )
                    }
                } else {
                    null
                },
            ) {
                SensorDetails(sensor, nowMillis)
            }
        }
    }

    @Composable
    private fun ProfileSelector(
        selected: VehicleProfile,
        onSelected: (VehicleProfile) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Расшифровка VHAL",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "${selected.key} · ${selected.vehicle}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                    ) {
                        Text("Изменить", fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        VehicleProfile.entries.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text("${profile.key} · ${profile.vehicle}") },
                                onClick = {
                                    expanded = false
                                    onSelected(profile)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SensorList(
        state: AppUiState,
        supportedCount: Int,
        displayedCount: Int,
        autoUpdatingCount: Int,
        groups: List<Triple<String, String, List<SensorRecord>>>,
        emptyText: String,
        query: String,
        onQueryChange: (String) -> Unit,
        selectedValueFilterIndex: Int,
        onValueFilterSelected: (Int) -> Unit,
        favoriteKeys: Set<String>,
        nowMillis: Long,
        onVhalProfileSelected: (VehicleProfile) -> Unit,
        onFavoriteToggle: (String) -> Unit,
        onSensorSelected: (SensorRecord) -> Unit,
    ) {
        val combinedStatus = combineReadStatus(state.sensorStatus, state.vhalStatus)
        val combinedDetail = listOf(
            "ECARX: ${state.sensorDetail.ifBlank { state.sensorStatus.labelForSource }}",
            "VHAL ${state.selectedVhalProfile.key}: ${state.vhalDetail.ifBlank { state.vhalStatus.labelForSource }}",
        ).joinToString(" · ")
        val displayedSensors = groups.flatMap { (_, _, sensors) -> sensors }
        val ecarxCount = displayedSensors.count { it.source == VehicleDataSource.ECARX }
        val vhalCount = displayedSensors.count { it.source == VehicleDataSource.VHAL }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                StatusCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Параметры автомобиля",
                    description = "Единый каталог данных ECARX и VHAL. Источник указан на каждой карточке; значения по подписке обновляются автоматически.",
                    status = combinedStatus,
                    detail = combinedDetail,
                )
            }
            item {
                ProfileSelector(state.selectedVhalProfile, onVhalProfileSelected)
            }
            item {
                CatalogSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = "Название, property ID, API name или значение",
                )
            }
            item {
                CatalogFilterRow(
                    labels = SensorValueFilter.entries.map(SensorValueFilter::title),
                    selectedIndex = selectedValueFilterIndex,
                    onSelected = onValueFilterSelected,
                )
            }
            item {
                CountSummary(
                    title = "Показано",
                    count = displayedCount,
                    detail = "Автообновление $autoUpdatingCount · ECARX $ecarxCount · " +
                        "VHAL $vhalCount · доступно $supportedCount",
                )
            }
            if (groups.isEmpty()) {
                item { EmptyMessage(emptyText) }
            } else {
                groups.forEach { (groupTitle, groupSubtitle, sensors) ->
                    item {
                        SensorGroupHeader(
                            title = groupTitle,
                            subtitle = groupSubtitle,
                            count = sensors.size,
                        )
                    }
                    sensors.chunked(2).forEach { row ->
                        item {
                            TwoColumnRow(row) { sensor ->
                                SensorCard(
                                    sensor = sensor,
                                    nowMillis = nowMillis,
                                    isFavorite = sensor.favoriteKey in favoriteKeys,
                                    onFavoriteToggle = { onFavoriteToggle(sensor.favoriteKey) },
                                    onClick = { onSensorSelected(sensor) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SensorGroupHeader(title: String, subtitle: String, count: Int) {
        CountSummary(
            title = title,
            count = count,
            detail = subtitle,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    @Composable
    private fun SensorCard(
        sensor: SensorRecord,
        nowMillis: Long,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
    ) {
        val stale = sensor.isStale(nowMillis)
        DataCard(
            title = sensor.title,
            apiName = sensor.apiName,
            id = sensor.id,
            idLabel = sensor.cardIdLabel,
            value = sensor.value,
            sourceLabel = sensor.sourceLabel,
            modeLabel = if (sensor.autoUpdates) "● ПО ПОДПИСКЕ" else "РУЧНОЕ ОБНОВЛЕНИЕ",
            modeIsHighlighted = sensor.autoUpdates,
            footerText = formatUpdateTime(sensor.updatedAtMillis, nowMillis) +
                if (stale) " · УСТАРЕЛО" else "",
            footerIsError = stale,
            isFavorite = isFavorite,
            onFavoriteToggle = onFavoriteToggle,
            onClick = onClick,
        ) {
            if (sensor.error.isNotBlank()) {
                Text(
                    text = sensor.error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }
        }
    }

    @Composable
    private fun SensorDetails(sensor: SensorRecord, nowMillis: Long) {
        ValueLine("Тип", sensor.valueKind)
        ValueLine(
            "Обновлено",
            formatUpdateTime(sensor.updatedAtMillis, nowMillis) +
                if (sensor.isStale(nowMillis)) " · УСТАРЕЛО" else "",
        )
        ValueLine(
            "Обновление",
            if (sensor.autoUpdates) "автоматически по подписке" else "только вручную",
        )
        if (sensor.changedSinceScan) ValueLine("Состояние", "изменилось после сканирования")
        if (sensor.source == VehicleDataSource.VHAL) {
            ValueLine(
                "Расшифровка",
                when {
                    sensor.decoded == true -> "профиль ${sensor.sourceProfile}"
                    sensor.sourceProfile != null ->
                        "профиль ${sensor.sourceProfile} не смог преобразовать значение · показан raw"
                    else -> "нет — показан raw VHAL"
                },
            )
            ValueLine("VHAL ID", String.format(Locale.US, "0x%08X", sensor.id))
            sensor.sourceTimestampNanos?.let {
                ValueLine("VHAL timestamp", "$it нс от запуска системы")
            }
            if (sensor.areaId != 0) {
                ValueLine("Area ID", String.format(Locale.US, "0x%08X", sensor.areaId))
            }
        }
        sensor.profilePropertyId?.let { ValueLine("Property ID", it.toString()) }
        if (sensor.error.isNotBlank()) ValueLine("Ошибка", sensor.error)
    }

    private val SensorRecord.selectionKey: String
        get() = "${source.name}:$id:$areaId"

    private val SensorRecord.sourceLabel: String
        get() = when {
            source == VehicleDataSource.VHAL && sourceProfile != null ->
                "VHAL · $sourceProfile"
            source == VehicleDataSource.VHAL -> "VHAL · RAW"
            else -> source.label
        }

    private val SensorRecord.cardIdLabel: String
        get() = when {
            profilePropertyId != null -> "property $profilePropertyId" + areaSuffix
            source == VehicleDataSource.VHAL ->
                "signal ${String.format(Locale.US, "0x%08X", id)}" + areaSuffix
            else -> "signal $id"
        }

    private val SensorRecord.areaSuffix: String
        get() = if (areaId == 0) "" else String.format(Locale.US, " · area 0x%08X", areaId)

    private fun combineReadStatus(first: ReadStatus, second: ReadStatus): ReadStatus = when {
        first == ReadStatus.AVAILABLE || second == ReadStatus.AVAILABLE -> ReadStatus.AVAILABLE
        first == ReadStatus.CHECKING || second == ReadStatus.CHECKING -> ReadStatus.CHECKING
        first == ReadStatus.ERROR || second == ReadStatus.ERROR -> ReadStatus.ERROR
        else -> ReadStatus.NOT_CHECKED
    }

    private val ReadStatus.labelForSource: String
        get() = when (this) {
            ReadStatus.NOT_CHECKED -> "не проверено"
            ReadStatus.CHECKING -> "проверка"
            ReadStatus.AVAILABLE -> "доступен"
            ReadStatus.ERROR -> "ошибка"
        }
}
