package com.geelydiagnostics.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@Composable
internal fun FullscreenValueDialog(
    title: String,
    apiName: String,
    idText: String,
    value: ApiValue,
    sourceLabel: String,
    modeLabel: String,
    isFavorite: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    onDismiss: () -> Unit,
    chart: (@Composable () -> Unit)? = null,
    details: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        FullscreenValueScreen(
            title = title,
            apiName = apiName,
            idText = idText,
            value = value,
            sourceLabel = sourceLabel,
            modeLabel = modeLabel,
            isFavorite = isFavorite,
            onFavoriteToggle = onFavoriteToggle,
            onDismiss = onDismiss,
            chart = chart,
            details = details,
        )
    }
}

/** Directly renderable content used by both the dialog and Android Studio Preview. */
@Composable
internal fun FullscreenValueScreen(
    title: String,
    apiName: String,
    idText: String,
    value: ApiValue,
    sourceLabel: String,
    modeLabel: String,
    isFavorite: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    onDismiss: () -> Unit,
    chart: (@Composable () -> Unit)? = null,
    details: @Composable () -> Unit,
) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = sourceLabel,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = modeLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.width(20.dp))
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
                                fontSize = 30.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onDismiss) {
                            Text("Закрыть", fontSize = 19.sp)
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = title,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = 26.sp,
                                    lineHeight = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${apiName.lowercase(Locale.ROOT)} · $idText",
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            SelectionContainer {
                                Text(
                                    text = value.display,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 28.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 58.sp,
                                    lineHeight = 66.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
                if (chart != null) {
                    item { chart() }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "ИСХОДНОЕ ЗНАЧЕНИЕ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            SelectionContainer {
                                Text(
                                    text = "raw  ${value.raw}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 22.sp,
                                    lineHeight = 28.sp,
                                )
                            }
                            details()
                        }
                    }
                }
                item {
                    Button(onClick = onDismiss) {
                        Text("Вернуться к списку", fontSize = 20.sp)
                    }
                }
            }
        }
}
