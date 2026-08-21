package com.geelydiagnostics.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal object SensorsTab {

    @Composable
    fun Content(
        state: AppUiState,
        onVhalProfileSelected: (VhalProfile) -> Unit,
        onFavoriteToggle: (String) -> Unit,
    ) {
        var expandedSensorKey by rememberSaveable { mutableStateOf<String?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var selectedSourceIndex by rememberSaveable { mutableIntStateOf(SensorSourceFilter.ALL.ordinal) }
        var selectedValueFilterIndex by rememberSaveable { mutableIntStateOf(SensorValueFilter.ALL.ordinal) }
        val selectedSource = SensorSourceFilter.entries[selectedSourceIndex]
        val selectedValueFilter = SensorValueFilter.entries[selectedValueFilterIndex]
        val nowMillis by rememberCurrentTimeMillis()
        val supported = state.sensors.filter { it.support.isVisibleAsSupported }
        val filtered = filterSensors(
            records = state.sensors,
            sourceFilter = selectedSource,
            valueFilter = selectedValueFilter,
            query = query,
            favoriteKeys = state.favoriteKeys,
        )
        val autoUpdating = filtered.filter(SensorRecord::autoUpdates)
        val manuallyUpdated = filtered.filterNot(SensorRecord::autoUpdates)
        val groups = listOf(
            "Обновляются автоматически · по подписке (${autoUpdating.size})" to autoUpdating,
            "Обновляются вручную · стартовый снимок (${manuallyUpdated.size})" to manuallyUpdated,
        ).filter { (_, values) -> values.isNotEmpty() }
        val emptyText = when {
            selectedSource == SensorSourceFilter.VHAL && state.vhalStatus == ReadStatus.ERROR ->
                "VHAL недоступен: ${state.vhalDetail.ifBlank { "причина записана в журнале" }}"
            selectedSource != SensorSourceFilter.ECARX &&
                selectedValueFilter == SensorValueFilter.DECODED &&
                state.selectedVhalProfile == VhalProfile.RAW ->
                "В профиле RAW расшифровка отключена. Выберите фильтр RAW/Все значения или профиль автомобиля."
            else -> "По выбранному фильтру значения не найдены."
        }

        Column(Modifier.fillMaxSize()) {
            ProfileSelector(state.selectedVhalProfile, onVhalProfileSelected)
            PrimaryTabRow(selectedTabIndex = selectedSourceIndex) {
                SensorSourceFilter.entries.forEachIndexed { index, filter ->
                    Tab(
                        selected = selectedSourceIndex == index,
                        onClick = { selectedSourceIndex = index },
                        text = {
                            Text(filter.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        },
                    )
                }
            }
            SensorList(
                state = state,
                supportedCount = supported.size,
                displayedCount = groups.sumOf { (_, sensors) -> sensors.size },
                groups = groups,
                emptyText = emptyText,
                query = query,
                onQueryChange = { query = it },
                selectedValueFilterIndex = selectedValueFilterIndex,
                onValueFilterSelected = { selectedValueFilterIndex = it },
                favoriteKeys = state.favoriteKeys,
                nowMillis = nowMillis,
                onFavoriteToggle = onFavoriteToggle,
                onSensorSelected = { expandedSensorKey = it.selectionKey },
            )
        }
        state.sensors.firstOrNull { it.selectionKey == expandedSensorKey }?.let { sensor ->
            FullscreenValueDialog(
                title = sensor.title,
                apiName = sensor.apiName,
                idText = "id ${sensor.id}",
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
            ) {
                SensorDetails(sensor, nowMillis)
            }
        }
    }

    @Composable
    private fun ProfileSelector(
        selected: VhalProfile,
        onSelected: (VhalProfile) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Профиль VHAL",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Text(
                    text = selected.vehicle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (selected == VhalProfile.RAW) {
                        "Без расшифровки: все свойства показаны как RAW"
                    } else {
                        "Используется только для расшифровки; raw-свойства не скрываются"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            Box {
                Button(onClick = { expanded = true }) {
                    Text(selected.key, fontSize = 16.sp)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    VhalProfile.entries.forEach { profile ->
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

    @Composable
    private fun SensorList(
        state: AppUiState,
        supportedCount: Int,
        displayedCount: Int,
        groups: List<Pair<String, List<SensorRecord>>>,
        emptyText: String,
        query: String,
        onQueryChange: (String) -> Unit,
        selectedValueFilterIndex: Int,
        onValueFilterSelected: (Int) -> Unit,
        favoriteKeys: Set<String>,
        nowMillis: Long,
        onFavoriteToggle: (String) -> Unit,
        onSensorSelected: (SensorRecord) -> Unit,
    ) {
        val combinedStatus = combineReadStatus(state.sensorStatus, state.vhalStatus)
        val combinedDetail = listOf(
            "ECARX: ${state.sensorDetail.ifBlank { state.sensorStatus.labelForSource }}",
            "VHAL ${state.selectedVhalProfile.key}: ${state.vhalDetail.ifBlank { state.vhalStatus.labelForSource }}",
        ).joinToString(" · ")
        val ecarxCount = state.sensors.count { it.source == VehicleDataSource.ECARX }
        val vhalCount = state.sensors.count { it.source == VehicleDataSource.VHAL }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                StatusCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Live Data",
                    description = "Показаны все доступные VHAL-свойства. Профиль только расшифровывает известные значения; live-данные обновляются только по подписке.",
                    status = combinedStatus,
                    detail = combinedDetail,
                )
            }
            item {
                CatalogSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = "Название, API name, ID или значение",
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
                SectionTitle(
                    "Показано: $displayedCount · ECARX: $ecarxCount · VHAL: $vhalCount · " +
                        "Поддерживается: $supportedCount",
                )
            }
            if (groups.isEmpty()) {
                item { EmptyMessage(emptyText) }
            } else {
                groups.forEach { (groupTitle, sensors) ->
                    item { SectionTitle(groupTitle) }
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
    private fun SensorCard(
        sensor: SensorRecord,
        nowMillis: Long,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
    ) {
        DataCard(
            title = sensor.title,
            apiName = sensor.apiName,
            id = sensor.id,
            value = sensor.value,
            sourceLabel = sensor.sourceLabel,
            isFavorite = isFavorite,
            onFavoriteToggle = onFavoriteToggle,
            onClick = onClick,
        ) {
            SensorDetails(sensor, nowMillis)
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
                sensor.sourceProfile?.let { "профиль $it" } ?: "нет — показан raw VHAL",
            )
            ValueLine("VHAL ID", String.format(Locale.US, "0x%08X", sensor.id))
            if (sensor.areaId != 0) {
                ValueLine("Area ID", String.format(Locale.US, "0x%08X", sensor.areaId))
            }
        }
        sensor.profilePropertyId?.let { ValueLine("Поле профиля", it.toString()) }
        if (sensor.error.isNotBlank()) ValueLine("Ошибка", sensor.error)
    }

    private val SensorRecord.selectionKey: String
        get() = "${source.name}:$id:$areaId"

    private val SensorRecord.sourceLabel: String
        get() = when {
            source == VehicleDataSource.VHAL && sourceProfile != null ->
                "VHAL · маппинг $sourceProfile"
            source == VehicleDataSource.VHAL -> "VHAL · RAW"
            else -> source.label
        }

    private fun combineReadStatus(first: ReadStatus, second: ReadStatus): ReadStatus = when {
        first == ReadStatus.ERROR || second == ReadStatus.ERROR -> ReadStatus.ERROR
        first == ReadStatus.CHECKING || second == ReadStatus.CHECKING -> ReadStatus.CHECKING
        first == ReadStatus.AVAILABLE || second == ReadStatus.AVAILABLE -> ReadStatus.AVAILABLE
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
