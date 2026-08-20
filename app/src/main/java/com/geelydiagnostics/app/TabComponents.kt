package com.geelydiagnostics.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun <T> CatalogScreen(
    status: ReadStatus,
    detail: String,
    title: String,
    subtitle: String,
    totalCount: Int,
    supportedCount: Int,
    emptyText: String,
    rows: List<List<T>>,
    rowContent: @Composable (List<T>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            StatusCard(
                modifier = Modifier.fillMaxWidth(),
                title = title,
                description = subtitle,
                status = status,
                detail = detail,
            )
        }
        item { SectionTitle("Поддерживается: $supportedCount · Проверено: $totalCount") }
        if (rows.isEmpty()) {
            item { EmptyMessage(emptyText) }
        } else {
            items(rows) { row -> rowContent(row) }
        }
    }
}

@Composable
internal fun <T> TwoColumnRow(items: List<T>, content: @Composable (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items.forEach { item ->
            Box(modifier = Modifier.weight(1f)) { content(item) }
        }
        if (items.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun DataCard(
    title: String,
    apiName: String,
    id: Int,
    value: ApiValue,
    sourceLabel: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = value.display,
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = sourceLabel,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = "raw  ${value.raw}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title.lowercase(Locale.getDefault()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${apiName.lowercase(Locale.ROOT)} · id $id",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun ValueLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label:",
            modifier = Modifier.width(132.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(text = value, fontSize = 14.sp)
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
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = status.label,
                color = statusColor(status),
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = detail.displayText, fontSize = 13.sp, maxLines = 3)
            }
        }
    }
}

@Composable
internal fun EmptyMessage(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text = text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

internal val ApiSupportStatus.isVisibleAsSupported: Boolean
    get() = this == ApiSupportStatus.ACTIVE || this == ApiSupportStatus.NOT_ACTIVE

private val ReadStatus.label: String
    get() = when (this) {
        ReadStatus.NOT_CHECKED -> "НЕ ПРОВЕРЕНО"
        ReadStatus.CHECKING -> "ПРОВЕРКА"
        ReadStatus.AVAILABLE -> "ДОСТУПЕН"
        ReadStatus.ERROR -> "ОШИБКА"
    }

@Composable
private fun statusColor(status: ReadStatus): Color = when (status) {
    ReadStatus.NOT_CHECKED -> MaterialTheme.colorScheme.onSurfaceVariant
    ReadStatus.CHECKING -> MaterialTheme.colorScheme.tertiary
    ReadStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
    ReadStatus.ERROR -> MaterialTheme.colorScheme.error
}

private val String.displayText: String
    get() = when (this) {
        "CONNECTED" -> "Связь установлена"
        "AVAILABLE" -> "Сервис отвечает"
        "CREATED" -> "Объект автомобиля создан"
        else -> this
    }
