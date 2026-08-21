package com.geelydiagnostics.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.max

private val LEADING_NUMBER = Regex("""^\s*([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))""")

internal fun ApiValue.chartNumber(): Double? = LEADING_NUMBER
    .find(display)
    ?.groupValues
    ?.get(1)
    ?.replace(',', '.')
    ?.toDoubleOrNull()
    ?.takeIf(Double::isFinite)

@Composable
internal fun SensorHistoryChart(
    samples: List<SensorSample>,
    isLive: Boolean,
) {
    val finiteSamples = samples.filter { it.value.isFinite() }
    val values = finiteSamples.map(SensorSample::value)
    val minimum = values.minOrNull()
    val maximum = values.maxOrNull()
    val duration = finiteSamples.durationMillis()
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "График",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${finiteSamples.size} точек · ${formatDuration(duration)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                )
            }

            if (finiteSamples.isNotEmpty() && minimum != null && maximum != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "min ${formatChartValue(minimum)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "max ${formatChartValue(maximum)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) {
                    val inset = 12.dp.toPx()
                    val graphWidth = max(1f, size.width - inset * 2f)
                    val graphHeight = max(1f, size.height - inset * 2f)
                    val rawRange = maximum - minimum
                    val padding = if (rawRange == 0.0) {
                        max(abs(maximum) * 0.05, 1.0)
                    } else {
                        rawRange * 0.08
                    }
                    val graphMinimum = minimum - padding
                    val graphMaximum = maximum + padding
                    val valueRange = graphMaximum - graphMinimum
                    val startTime = finiteSamples.first().timestampMillis
                    val timeRange = max(1L, finiteSamples.last().timestampMillis - startTime)

                    repeat(5) { index ->
                        val y = inset + graphHeight * index / 4f
                        drawLine(
                            color = gridColor,
                            start = Offset(inset, y),
                            end = Offset(size.width - inset, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    fun point(sample: SensorSample): Offset {
                        val x = inset + graphWidth *
                            ((sample.timestampMillis - startTime).toFloat() / timeRange.toFloat())
                        val normalized = ((sample.value - graphMinimum) / valueRange).toFloat()
                        val y = inset + graphHeight * (1f - normalized)
                        return Offset(x, y)
                    }

                    if (finiteSamples.size == 1) {
                        drawCircle(
                            color = lineColor,
                            radius = 7.dp.toPx(),
                            center = Offset(size.width / 2f, size.height / 2f),
                        )
                    } else {
                        val path = Path().apply {
                            finiteSamples.forEachIndexed { index, sample ->
                                val point = point(sample)
                                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(
                                width = 5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 6.dp.toPx(),
                            center = point(finiteSamples.last()),
                        )
                    }
                }
            }

            if (finiteSamples.size < 2) {
                Text(
                    text = if (isLive) {
                        "Ожидаю новые значения по подписке — линия появится после второго измерения."
                    } else {
                        "Для графика нужны новые измерения; сейчас это значение обновляется вручную."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
            } else {
                Text(
                    text = "История текущего сканирования · последние 2 минуты",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

private fun List<SensorSample>.durationMillis(): Long =
    if (size < 2) 0L else (last().timestampMillis - first().timestampMillis).coerceAtLeast(0L)

private fun formatDuration(durationMillis: Long): String {
    val seconds = durationMillis / 1_000L
    return if (seconds < 60L) "$seconds сек" else "${seconds / 60L} мин ${seconds % 60L} сек"
}

private fun formatChartValue(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
