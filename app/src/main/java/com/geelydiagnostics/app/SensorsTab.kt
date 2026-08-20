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

    private enum class SourceFilter(val title: String) {
        ALL("Все"),
        ECARX("ECARX"),
        VHAL("VHAL"),
    }

    @Composable
    fun Content(
        state: AppUiState,
        onVhalProfileSelected: (VhalProfile) -> Unit,
    ) {
        var expandedSensorKey by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedFilterIndex by rememberSaveable { mutableIntStateOf(SourceFilter.ALL.ordinal) }
        val selectedFilter = SourceFilter.entries[selectedFilterIndex]
        val supported = state.sensors.filter { it.support.isVisibleAsSupported }
        val groups = when (selectedFilter) {
            SourceFilter.ALL -> listOf(
                "ECARX" to supported.filter { it.source == VehicleDataSource.ECARX },
                "VHAL · все свойства · декодер ${state.selectedVhalProfile.key}" to
                    supported.filter { it.source == VehicleDataSource.VHAL },
            )
            SourceFilter.ECARX -> listOf(
                "ECARX" to supported.filter { it.source == VehicleDataSource.ECARX },
            )
            SourceFilter.VHAL -> listOf(
                "VHAL · все свойства · декодер ${state.selectedVhalProfile.key}" to
                    supported.filter { it.source == VehicleDataSource.VHAL },
            )
        }.map { (title, values) -> title to values.sortedBy(SensorRecord::title) }
            .filter { (_, values) -> values.isNotEmpty() }

        Column(Modifier.fillMaxSize()) {
            ProfileSelector(state.selectedVhalProfile, onVhalProfileSelected)
            PrimaryTabRow(selectedTabIndex = selectedFilterIndex) {
                SourceFilter.entries.forEachIndexed { index, filter ->
                    Tab(
                        selected = selectedFilterIndex == index,
                        onClick = { selectedFilterIndex = index },
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
                modeLabel = "АВТООБНОВЛЕНИЕ",
                onDismiss = { expandedSensorKey = null },
            ) {
                SensorDetails(sensor)
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
                    text = "Используется только для расшифровки; raw-свойства не скрываются",
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
        onSensorSelected: (SensorRecord) -> Unit,
    ) {
        val combinedStatus = combineReadStatus(state.sensorStatus, state.vhalStatus)
        val combinedDetail = listOf(
            "ECARX: ${state.sensorDetail.ifBlank { state.sensorStatus.labelForSource }}",
            "VHAL ${state.selectedVhalProfile.key}: ${state.vhalDetail.ifBlank { state.vhalStatus.labelForSource }}",
        ).joinToString(" · ")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                StatusCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Live Data",
                    description = "Показаны все доступные VHAL-свойства. Профиль только расшифровывает известные значения; изменяемые данные обновляются автоматически.",
                    status = combinedStatus,
                    detail = combinedDetail,
                )
            }
            item {
                SectionTitle(
                    "Показано: $displayedCount · Поддерживается: $supportedCount · Проверено: ${state.sensors.size}",
                )
            }
            if (groups.isEmpty()) {
                item { EmptyMessage("Поддерживаемые сенсоры пока не найдены.") }
            } else {
                groups.forEach { (groupTitle, sensors) ->
                    item { SectionTitle(groupTitle) }
                    sensors.chunked(2).forEach { row ->
                        item {
                            TwoColumnRow(row) { sensor ->
                                SensorCard(sensor, onClick = { onSensorSelected(sensor) })
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SensorCard(sensor: SensorRecord, onClick: () -> Unit) {
        DataCard(
            title = sensor.title,
            apiName = sensor.apiName,
            id = sensor.id,
            value = sensor.value,
            sourceLabel = sensor.sourceLabel,
            onClick = onClick,
        ) {
            SensorDetails(sensor)
        }
    }

    @Composable
    private fun SensorDetails(sensor: SensorRecord) {
        ValueLine("Тип", sensor.valueKind)
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
        first == ReadStatus.AVAILABLE || second == ReadStatus.AVAILABLE -> ReadStatus.AVAILABLE
        first == ReadStatus.CHECKING || second == ReadStatus.CHECKING -> ReadStatus.CHECKING
        first == ReadStatus.ERROR && second == ReadStatus.ERROR -> ReadStatus.ERROR
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
