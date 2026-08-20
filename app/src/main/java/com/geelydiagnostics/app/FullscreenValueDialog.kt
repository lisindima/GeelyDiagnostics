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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onDismiss: () -> Unit,
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
            onDismiss = onDismiss,
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
    onDismiss: () -> Unit,
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
                        Button(onClick = onDismiss) {
                            Text("Закрыть", fontSize = 19.sp)
                        }
                    }
                }
                item {
                    SelectionContainer {
                        Text(
                            text = value.display,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 58.sp,
                            lineHeight = 66.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
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
                            SelectionContainer {
                                Text(
                                    text = "raw  ${value.raw}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 22.sp,
                                    lineHeight = 28.sp,
                                )
                            }
                            Text(
                                text = title.lowercase(Locale.getDefault()),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 21.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${apiName.lowercase(Locale.ROOT)} · $idText",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                            )
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
