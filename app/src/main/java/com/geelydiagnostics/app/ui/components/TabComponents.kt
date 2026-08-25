package com.geelydiagnostics.app.ui.components

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import java.util.Locale

@Composable
internal fun <T> CatalogScreen(
    status: ReadStatus,
    detail: String,
    title: String,
    subtitle: String,
    totalCount: Int,
    supportedCount: Int,
    displayedCount: Int,
    emptyText: String,
    rows: List<List<T>>,
    controls: @Composable () -> Unit,
    rowContent: @Composable (List<T>) -> Unit,
) {
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
                title = title,
                description = subtitle,
                status = status,
                detail = statusAttentionDetail(status, detail),
            )
        }
        item { controls() }
        item {
            CountSummary(
                title = "Показано",
                count = displayedCount,
                detail = "Поддерживается $supportedCount · проверено $totalCount",
            )
        }
        if (rows.isEmpty()) {
            item { EmptyMessage(emptyText) }
        } else {
            items(rows) { row -> rowContent(row) }
        }
    }
}

@Composable
internal fun <T> TwoColumnRow(items: List<T>, content: @Composable (T) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < AppBreakpoints.TwoColumns) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Default)) {
                items.forEach { item -> content(item) }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Default),
                verticalAlignment = Alignment.Top,
            ) {
                items.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) { content(item) }
                }
                if (items.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun DataCard(
    title: String,
    apiName: String,
    id: Int,
    value: VehicleDisplayValue,
    sourceLabels: List<String>,
    modeLabel: String? = null,
    modeIsHighlighted: Boolean = false,
    idLabel: String = "id $id",
    footerText: String? = null,
    footerIsError: Boolean = false,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = AppSizes.CardElevation),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.XSmall),
                ) {
                    SourceBadges(sourceLabels)
                    if (modeLabel != null) {
                        Text(
                            text = modeLabel,
                            color = if (modeIsHighlighted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = AppType.Technical,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.semantics {
                        contentDescription = if (isFavorite) {
                            "Удалить из избранного"
                        } else {
                            "Добавить в избранное"
                        }
                    },
                ) {
                    Text(
                        text = if (isFavorite) "★" else "☆",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = AppType.DialogTitle,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                SelectionContainer {
                    Text(
                        text = value.display,
                        modifier = Modifier.padding(
                            horizontal = AppSpacing.Default,
                            vertical = AppSpacing.Default,
                        ),
                        fontSize = AppType.CardValue,
                        lineHeight = AppType.CardValueLine,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Technical)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = AppType.CardTitle,
                    lineHeight = AppType.CardTitleLine,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SelectionContainer {
                    Text(
                        text = "RAW · ${value.raw}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = AppType.Label,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TechnicalLabel(apiName = apiName, idLabel = idLabel, fontSize = AppType.Technical)
            }

            content()

            Spacer(Modifier.height(AppSpacing.XSmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (footerText == null) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        text = footerText,
                        modifier = Modifier.weight(1f),
                        color = if (footerIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = AppType.Label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "ОТКРЫТЬ ↗",
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = AppType.Technical,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun TechnicalLabel(apiName: String, idLabel: String, fontSize: TextUnit) {
    val text = listOfNotNull(
        apiName.takeIf(String::isNotBlank)?.lowercase(Locale.ROOT),
        idLabel.takeIf(String::isNotBlank),
    ).joinToString(" · ")
    if (text.isEmpty()) return
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun SourceBadges(labels: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.distinct().forEach { label ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(
                        horizontal = AppSpacing.Small,
                        vertical = AppSpacing.XSmall,
                    ),
                    fontSize = AppType.Label,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun CatalogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Поиск") },
        placeholder = { Text(placeholder) },
    )
}

@Composable
internal fun CatalogFilterRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small)) {
        itemsIndexed(labels) { index, label ->
            val selected = selectedIndex == index
            FilterChip(
                selected = selected,
                onClick = { onSelected(index) },
                shape = MaterialTheme.shapes.medium,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                label = {
                    Text(
                        text = label,
                        fontSize = AppType.Body,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
            )
        }
    }
}

@Composable
internal fun ValueLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.XSmall),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label:",
            modifier = Modifier.weight(0.34f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = AppType.Supporting,
            lineHeight = AppType.ValueLabelLine,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        SelectionContainer(modifier = Modifier.weight(0.66f)) {
            Text(
                text = value,
                fontSize = AppType.Body,
                lineHeight = AppType.BodyLine,
            )
        }
    }
}

@Composable
internal fun StatusCard(
    modifier: Modifier,
    title: String,
    description: String,
    status: ReadStatus,
    detail: String,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    val (statusContainer, statusContent) = statusColors(status)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = AppSizes.CardElevation),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Small),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.XSmall)) {
                Text(title, fontSize = AppType.Standard, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = AppType.Supporting,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = statusContainer,
                contentColor = statusContent,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    Modifier.padding(
                        horizontal = AppSpacing.Default,
                        vertical = AppSpacing.Medium,
                    ),
                ) {
                    Text(
                        text = status.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppType.Status,
                    )
                    if (detail.isNotBlank()) {
                        Spacer(Modifier.height(AppSpacing.XSmall))
                        Text(text = detail, fontSize = AppType.Supporting, maxLines = 3)
                    }
                }
            }
            supportingContent?.invoke()
        }
    }
}

@Composable
internal fun EmptyMessage(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = AppType.Action)
}

@Composable
internal fun CountSummary(
    title: String,
    count: Int,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.XSmall, vertical = AppSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Default),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.XSmall),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = AppType.CardTitle,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = AppType.Supporting,
            )
        }
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = AppType.Count,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val ReadStatus.label: String
    get() = when (this) {
        ReadStatus.NOT_CHECKED -> "НЕ ПРОВЕРЕНО"
        ReadStatus.CHECKING -> "ПРОВЕРКА"
        ReadStatus.PARTIAL -> "ЧАСТИЧНО ДОСТУПЕН"
        ReadStatus.AVAILABLE -> "ДОСТУПЕН"
        ReadStatus.ERROR -> "ОШИБКА"
    }

@Composable
private fun statusColors(status: ReadStatus): Pair<Color, Color> = when (status) {
    ReadStatus.NOT_CHECKED ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    ReadStatus.CHECKING ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    ReadStatus.PARTIAL ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    ReadStatus.AVAILABLE ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    ReadStatus.ERROR ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

internal fun aggregateReadStatus(statuses: Iterable<ReadStatus>): ReadStatus {
    val values = statuses.toList()
    if (values.isEmpty() || values.all { it == ReadStatus.NOT_CHECKED }) return ReadStatus.NOT_CHECKED
    if (values.all { it == ReadStatus.AVAILABLE }) return ReadStatus.AVAILABLE
    if (values.any { it == ReadStatus.AVAILABLE || it == ReadStatus.PARTIAL }) return ReadStatus.PARTIAL
    if (values.any { it == ReadStatus.CHECKING }) return ReadStatus.CHECKING
    return ReadStatus.ERROR
}

/** Successful catalog metrics are shown in CountSummary, not repeated in the status card. */
internal fun statusAttentionDetail(status: ReadStatus, detail: String): String = when (status) {
    ReadStatus.PARTIAL, ReadStatus.ERROR -> detail
    ReadStatus.NOT_CHECKED, ReadStatus.CHECKING, ReadStatus.AVAILABLE -> ""
}
