package com.geelydiagnostics.app.ui.tabs

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.catalog.*
import com.geelydiagnostics.app.ui.components.*

import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.VehicleSourceReading
import com.geelydiagnostics.app.vehicle.property.favoriteKey
import com.geelydiagnostics.app.vehicle.property.primaryReading
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend

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
        onVhalBackendSelected: (VhalGatewayBackend) -> Unit,
        onFavoriteToggle: (String) -> Unit,
    ) {
        var expandedParameterKey by rememberSaveable { mutableStateOf<String?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var selectedValueFilterIndex by rememberSaveable { mutableIntStateOf(ParameterValueFilter.ALL.ordinal) }
        val selectedValueFilter = ParameterValueFilter.entries[selectedValueFilterIndex]
        val nowMillis by rememberCurrentTimeMillis()
        val supported = state.parameters.filter { it.status == VehiclePropertyStatus.AVAILABLE }
        val filtered = filterParameters(
            records = state.parameters,
            valueFilter = selectedValueFilter,
            query = query,
            favoriteKeys = state.favoriteKeys,
        )
        val autoUpdating = filtered.filter(VehicleParameter::autoUpdates)
        val manuallyUpdated = filtered.filterNot(VehicleParameter::autoUpdates)
        val unmappedCount = filtered.count { it.propertyId == null || !it.decoded }
        val groups = listOf(
            Triple("Автообновление", "Новые значения приходят по подписке", autoUpdating),
            Triple("Ручное обновление", "Значения обновляются по запросу", manuallyUpdated),
        ).filter { (_, _, values) -> values.isNotEmpty() }
        val emptyText = when {
            state.ecarxParameterStatus == ReadStatus.ERROR && state.vhalStatus == ReadStatus.ERROR ->
                "Источники данных недоступны. Подробности записаны в журнале."
            selectedValueFilter == ParameterValueFilter.DECODED &&
                state.selectedVhalProfile == VehicleProfile.RAW ->
                "Для VHAL выбран RAW. Здесь останутся только значения, расшифрованные другими источниками."
            else -> "По выбранному фильтру значения не найдены."
        }

        ParameterList(
            state = state,
            supportedCount = supported.size,
            displayedCount = filtered.size,
            autoUpdatingCount = autoUpdating.size,
            manuallyUpdatedCount = manuallyUpdated.size,
            unmappedCount = unmappedCount,
            groups = groups,
            emptyText = emptyText,
            query = query,
            onQueryChange = { query = it },
            selectedValueFilterIndex = selectedValueFilterIndex,
            onValueFilterSelected = { selectedValueFilterIndex = it },
            favoriteKeys = state.favoriteKeys,
            nowMillis = nowMillis,
            onVhalProfileSelected = onVhalProfileSelected,
            onVhalBackendSelected = onVhalBackendSelected,
            onFavoriteToggle = onFavoriteToggle,
            onParameterSelected = { expandedParameterKey = it.selectionKey },
        )
        state.parameters.firstOrNull { it.selectionKey == expandedParameterKey }?.let { parameter ->
            FullscreenValueDialog(
                title = parameter.title,
                apiName = parameter.fieldName,
                idText = parameter.cardIdLabel,
                value = parameter.value,
                sourceLabels = parameter.sourceLabels,
                modeLabel = if (parameter.autoUpdates) {
                    "АВТООБНОВЛЕНИЕ · ПОДПИСКА"
                } else {
                    "РУЧНОЕ ОБНОВЛЕНИЕ"
                },
                isFavorite = parameter.favoriteKey in state.favoriteKeys,
                onFavoriteToggle = { onFavoriteToggle(parameter.favoriteKey) },
                onDismiss = { expandedParameterKey = null },
                chart = if (parameter.chartable) {
                    {
                        ParameterHistoryChart(
                            samples = state.parameterHistory[parameter.favoriteKey].orEmpty(),
                            isLive = parameter.autoUpdates,
                        )
                    }
                } else {
                    null
                },
            ) {
                ParameterDetails(parameter, nowMillis)
            }
        }
    }

    @Composable
    private fun BackendSelector(
        selected: VhalGatewayBackend,
        onSelected: (VhalGatewayBackend) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Транспорт VHAL · временный переключатель",
                        fontSize = 12.sp,
                    )
                    Text(
                        text = selected.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = selected.description,
                        fontSize = 11.sp,
                    )
                }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text("Изменить", fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        VhalGatewayBackend.entries.forEach { backend ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(backend.title, fontWeight = FontWeight.SemiBold)
                                        Text(backend.description, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    expanded = false
                                    onSelected(backend)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ProfileSelector(
        selected: VehicleProfile,
        decodedCount: Int,
        totalCount: Int,
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
                    Text(
                        text = if (selected == VehicleProfile.RAW) {
                            "Исходные VHAL-сигналы без профильной расшифровки"
                        } else {
                            "Расшифровано $decodedCount из $totalCount VHAL-сигналов"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
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
    private fun ParameterList(
        state: AppUiState,
        supportedCount: Int,
        displayedCount: Int,
        autoUpdatingCount: Int,
        manuallyUpdatedCount: Int,
        unmappedCount: Int,
        groups: List<Triple<String, String, List<VehicleParameter>>>,
        emptyText: String,
        query: String,
        onQueryChange: (String) -> Unit,
        selectedValueFilterIndex: Int,
        onValueFilterSelected: (Int) -> Unit,
        favoriteKeys: Set<String>,
        nowMillis: Long,
        onVhalProfileSelected: (VehicleProfile) -> Unit,
        onVhalBackendSelected: (VhalGatewayBackend) -> Unit,
        onFavoriteToggle: (String) -> Unit,
        onParameterSelected: (VehicleParameter) -> Unit,
    ) {
        val combinedStatus = aggregateReadStatus(listOf(state.ecarxParameterStatus, state.vhalStatus))
        val combinedDetail = listOfNotNull(
            sourceAttentionDetail(
                "ECARX",
                state.ecarxParameterStatus,
                state.ecarxParameterDetail,
            ),
            sourceAttentionDetail(
                "VHAL",
                state.vhalStatus,
                state.vhalDetail,
            ),
        ).joinToString(" · ")
        val vhalReadings = state.parameters.flatMap(VehicleParameter::sourceReadings)
            .filter { it.source == VehiclePropertySource.VHAL }
        val decodedVhalCount = vhalReadings.count { it.decoded }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                StatusCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Параметры автомобиля",
                    description = "Нормализованные параметры автомобиля и неизвестные исходные сигналы. Источники указаны на карточках.",
                    status = combinedStatus,
                    detail = combinedDetail,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SourceStateBadge("ECARX", state.ecarxParameterStatus, Modifier.weight(1f))
                        SourceStateBadge(
                            "VHAL",
                            state.vhalStatus,
                            Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                BackendSelector(
                    selected = state.selectedVhalBackend,
                    onSelected = onVhalBackendSelected,
                )
            }
            item {
                ProfileSelector(
                    selected = state.selectedVhalProfile,
                    decodedCount = decodedVhalCount,
                    totalCount = vhalReadings.size,
                    onSelected = onVhalProfileSelected,
                )
            }
            item {
                CatalogSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = "Название, ID свойства, API или значение",
                )
            }
            item {
                CatalogFilterRow(
                    labels = ParameterValueFilter.entries.map(ParameterValueFilter::title),
                    selectedIndex = selectedValueFilterIndex,
                    onSelected = onValueFilterSelected,
                )
            }
            item {
                CountSummary(
                    title = "Показано",
                    count = displayedCount,
                    detail = "По подписке $autoUpdatingCount · вручную $manuallyUpdatedCount · " +
                        "без расшифровки $unmappedCount · доступно $supportedCount",
                )
            }
            if (groups.isEmpty()) {
                item { EmptyMessage(emptyText) }
            } else {
                groups.forEach { (groupTitle, groupSubtitle, parameters) ->
                    item {
                        ParameterGroupHeader(
                            title = groupTitle,
                            subtitle = groupSubtitle,
                            count = parameters.size,
                        )
                    }
                    parameters.chunked(2).forEach { row ->
                        item {
                            TwoColumnRow(row) { parameter ->
                                ParameterCard(
                                    parameter = parameter,
                                    nowMillis = nowMillis,
                                    isFavorite = parameter.favoriteKey in favoriteKeys,
                                    onFavoriteToggle = { onFavoriteToggle(parameter.favoriteKey) },
                                    onClick = { onParameterSelected(parameter) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ParameterGroupHeader(title: String, subtitle: String, count: Int) {
        CountSummary(
            title = title,
            count = count,
            detail = subtitle,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    @Composable
    private fun SourceStateBadge(label: String, status: ReadStatus, modifier: Modifier = Modifier) {
        val colors = when (status) {
            ReadStatus.AVAILABLE -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
            ReadStatus.PARTIAL, ReadStatus.CHECKING -> MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer
            ReadStatus.ERROR -> MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer
            ReadStatus.NOT_CHECKED -> MaterialTheme.colorScheme.surfaceVariant to
                MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(
            modifier = modifier,
            color = colors.first,
            contentColor = colors.second,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(status.labelForSource, fontSize = 12.sp)
            }
        }
    }

    @Composable
    private fun ParameterCard(
        parameter: VehicleParameter,
        nowMillis: Long,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
    ) {
        val stale = parameter.isStale(nowMillis)
        DataCard(
            title = parameter.title,
            apiName = parameter.fieldName,
            id = parameter.propertyId?.rawValue ?: parameter.primaryReading.signalId,
            idLabel = parameter.cardIdLabel,
            value = parameter.value,
            sourceLabels = parameter.sourceLabels,
            modeLabel = if (parameter.autoUpdates) "● ПО ПОДПИСКЕ" else "РУЧНОЕ ОБНОВЛЕНИЕ",
            modeIsHighlighted = parameter.autoUpdates,
            footerText = formatUpdateTime(parameter.updatedAtMillis, nowMillis) +
                if (stale) " · УСТАРЕЛО" else "",
            footerIsError = stale,
            isFavorite = isFavorite,
            onFavoriteToggle = onFavoriteToggle,
            onClick = onClick,
        ) {
            parameter.sourceReadings.filter { it.error.isNotBlank() }.forEach { reading ->
                Text(
                    text = "${reading.badgeLabel}: ${reading.error}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }
        }
    }

    @Composable
    private fun ParameterDetails(parameter: VehicleParameter, nowMillis: Long) {
        ValueLine("Тип", parameter.valueKind)
        ValueLine(
            "Обновлено",
            formatUpdateTime(parameter.updatedAtMillis, nowMillis) +
                if (parameter.isStale(nowMillis)) " · УСТАРЕЛО" else "",
        )
        ValueLine(
            "Обновление",
            if (parameter.autoUpdates) "автоматически по подписке" else "только вручную",
        )
        if (parameter.changedSinceScan) ValueLine("Состояние", "изменилось после сканирования")
        ValueLine(
            "Расшифровка",
            if (parameter.decoded) "нормализованное свойство" else "нет — показано исходное значение",
        )
        parameter.sourceReadings.forEach { reading ->
            val label = reading.badgeLabel
            ValueLine("$label · сигнал", reading.signalLabel)
            ValueLine("$label · raw", reading.value.raw)
            reading.sourceTimestampNanos?.let {
                ValueLine("$label · timestamp", "$it нс от запуска системы")
            }
            if (reading.error.isNotBlank()) ValueLine("$label · ошибка", reading.error)
        }
    }

    private val VehicleParameter.selectionKey: String
        get() = favoriteKey

    private val VehicleParameter.sourceLabels: List<String>
        get() = sourceReadings.map { it.badgeLabel }.distinct()

    private val VehicleParameter.fieldName: String
        get() = primaryReading.signalName.takeUnless {
            it.startsWith("VHAL_0x", ignoreCase = true)
        }.orEmpty()

    private val VehicleParameter.cardIdLabel: String
        get() = when {
            propertyId != null -> "внутренний ID ${propertyId.rawValue}" + areaSuffix
            primaryReading.source == VehiclePropertySource.VHAL ->
                String.format(Locale.US, "0x%08X", primaryReading.signalId) + areaSuffix
            else -> primaryReading.signalId.toString()
        }

    private val VehicleParameter.areaSuffix: String
        get() = if (areaId == 0) "" else String.format(Locale.US, " · area 0x%08X", areaId)

    private val ReadStatus.labelForSource: String
        get() = when (this) {
            ReadStatus.NOT_CHECKED -> "не проверено"
            ReadStatus.CHECKING -> "проверка"
            ReadStatus.PARTIAL -> "частично доступен"
            ReadStatus.AVAILABLE -> "доступен"
            ReadStatus.ERROR -> "ошибка"
        }

    private fun sourceAttentionDetail(
        label: String,
        status: ReadStatus,
        detail: String,
    ): String? = statusAttentionDetail(status, detail)
        .ifBlank { null }
        ?.let { "$label: $it" }

    private val VehicleSourceReading.badgeLabel: String
        get() = when {
            source == VehiclePropertySource.VHAL && profile != null -> "VHAL · $profile"
            source == VehiclePropertySource.VHAL -> "VHAL · RAW"
            else -> source.label
        }

    private val VehicleSourceReading.signalLabel: String
        get() = buildString {
            if (source == VehiclePropertySource.VHAL) {
                append(String.format(Locale.US, "0x%08X", signalId))
            } else {
                append(signalId)
            }
            append(" · ")
            append(signalName)
            if (areaId != 0) append(String.format(Locale.US, " · area 0x%08X", areaId))
        }
}
