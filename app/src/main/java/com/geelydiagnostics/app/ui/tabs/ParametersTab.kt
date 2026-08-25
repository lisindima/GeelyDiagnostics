package com.geelydiagnostics.app.ui.tabs

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.catalog.*
import com.geelydiagnostics.app.ui.components.*
import com.geelydiagnostics.app.ui.parameters.*
import com.geelydiagnostics.app.ui.theme.*

import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
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
        val undecodedCount = filtered.count { !it.decoded }
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
            undecodedCount = undecodedCount,
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
            ParameterFullscreenDialog(
                parameter = parameter,
                history = state.parameterHistory[parameter.favoriteKey].orEmpty(),
                nowMillis = nowMillis,
                isFavorite = parameter.favoriteKey in state.favoriteKeys,
                onFavoriteToggle = { onFavoriteToggle(parameter.favoriteKey) },
                onDismiss = { expandedParameterKey = null },
            )
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
            border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.tertiary),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = AppSpacing.Default,
                    vertical = AppSpacing.Small,
                ),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Транспорт VHAL · временный переключатель",
                        fontSize = AppType.Label,
                    )
                    Text(
                        text = selected.title,
                        fontSize = AppType.BodyEmphasis,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = selected.description,
                        fontSize = AppType.Technical,
                    )
                }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text("Изменить", fontSize = AppType.Supporting)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        VhalGatewayBackend.entries.forEach { backend ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(backend.title, fontWeight = FontWeight.SemiBold)
                                        Text(backend.description, fontSize = AppType.Technical)
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
                .padding(top = AppSpacing.XSmall),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = AppSpacing.Default,
                    vertical = AppSpacing.Small,
                ),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Расшифровка VHAL",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = AppType.Label,
                    )
                    Text(
                        text = "${selected.key} · ${selected.vehicle}",
                        fontSize = AppType.BodyEmphasis,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (selected == VehicleProfile.RAW) {
                            "Исходные VHAL-сигналы без профильной расшифровки"
                        } else {
                            "Расшифровано $decodedCount из $totalCount VHAL-сигналов"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = AppType.Technical,
                    )
                }
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                    ) {
                        Text("Изменить", fontSize = AppType.Supporting)
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
        undecodedCount: Int,
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
            contentPadding = PaddingValues(
                top = AppSpacing.ScreenTop,
                bottom = AppSpacing.ScreenBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Default),
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
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small),
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
                        "без расшифровки $undecodedCount · доступно $supportedCount",
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
            modifier = Modifier.padding(top = AppSpacing.Small),
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
                modifier = Modifier.padding(
                    horizontal = AppSpacing.Medium,
                    vertical = AppSpacing.Small,
                ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = AppType.Label, fontWeight = FontWeight.Bold)
                Text(status.labelForSource, fontSize = AppType.Label)
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
                    fontSize = AppType.Label,
                    maxLines = 2,
                )
            }
        }
    }

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

}
