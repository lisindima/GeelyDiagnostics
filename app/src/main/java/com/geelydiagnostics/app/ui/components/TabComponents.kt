package com.geelydiagnostics.app.ui.components

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import java.util.Locale

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
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                SourceBadges(
                    labels = sourceLabels + listOfNotNull(modeLabel),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(AppSizes.FavoriteButton)
                        .semantics {
                            contentDescription = if (isFavorite) {
                                "Удалить из избранного"
                            } else {
                                "Добавить в избранное"
                            }
                        }
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
                        fontWeight = FontWeight.ExtraBold,
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
                    fontWeight = FontWeight.ExtraBold,
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

            if (footerText != null) {
                Text(
                    text = footerText,
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
    Text(
        text = labels.distinct().joinToString(" · "),
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        fontSize = AppType.Label,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun CatalogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppSizes.SearchFieldMinHeight)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            )
            .border(
                width = AppSizes.Border,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(
                horizontal = AppSpacing.Default,
                vertical = AppSpacing.Medium,
            ),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = AppType.Body,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = AppType.Body,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
internal fun CatalogFilterRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(vertical = AppSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small),
    ) {
        itemsIndexed(labels) { index, label ->
            val selected = selectedIndex == index
            Surface(
                onClick = { onSelected(index) },
                modifier = Modifier.heightIn(min = AppSizes.FilterChipMinHeight),
                shape = MaterialTheme.shapes.medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    AppSizes.Border,
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
            ) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = AppSpacing.Default,
                        vertical = AppSpacing.Small,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = AppType.Body,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
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
internal fun DescriptionBlock(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Small)) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = AppType.Supporting,
            fontWeight = FontWeight.Bold,
        )
        SelectionContainer {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
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
    action: (@Composable () -> Unit)? = null,
) {
    val (statusContainer, statusContent) = statusColors(status)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = AppSizes.CardElevation),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.XSmall),
                ) {
                    Text(title, fontSize = AppType.CardTitle, fontWeight = FontWeight.ExtraBold)
                    Text(
                        description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = AppType.Supporting,
                    )
                }
                action?.invoke()
                Surface(
                    color = statusContainer,
                    contentColor = statusContent,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = status.label,
                        modifier = Modifier.padding(
                            horizontal = AppSpacing.Medium,
                            vertical = AppSpacing.Small,
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize = AppType.Label,
                        maxLines = 1,
                    )
                }
            }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = if (status == ReadStatus.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = AppType.Supporting,
                    maxLines = 3,
                )
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
                fontWeight = FontWeight.ExtraBold,
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
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
internal fun SourceStateBadge(label: String, status: ReadStatus, modifier: Modifier = Modifier) {
    val colors = when (status) {
        ReadStatus.AVAILABLE -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
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
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
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

internal fun sourceAttentionDetail(
    label: String,
    status: ReadStatus,
    detail: String,
): String? = statusAttentionDetail(status, detail)
    .ifBlank { null }
    ?.let { "$label: $it" }

private val ReadStatus.labelForSource: String
    get() = when (this) {
        ReadStatus.NOT_CHECKED -> "не проверено"
        ReadStatus.CHECKING -> "проверка"
        ReadStatus.PARTIAL -> "частично доступен"
        ReadStatus.AVAILABLE -> "доступен"
        ReadStatus.ERROR -> "ошибка"
    }
