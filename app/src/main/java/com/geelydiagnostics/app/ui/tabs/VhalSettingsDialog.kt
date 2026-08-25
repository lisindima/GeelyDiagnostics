package com.geelydiagnostics.app.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.geelydiagnostics.app.ui.theme.AppSizes
import com.geelydiagnostics.app.ui.theme.AppSpacing
import com.geelydiagnostics.app.ui.theme.AppType
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend

@Composable
internal fun VhalSettingsDialog(
    selectedProfile: VehicleProfile,
    selectedBackend: VhalGatewayBackend,
    decodedCount: Int,
    totalCount: Int,
    onProfileSelected: (VehicleProfile) -> Unit,
    onBackendSelected: (VhalGatewayBackend) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.XLarge)
                .widthIn(max = AppSizes.SettingsDialogMaxWidth),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = AppSizes.DialogElevation,
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.XLarge),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Large),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Настройки VHAL",
                            fontSize = AppType.DialogTitle,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Расшифровка и транспорт применяются ко всему каталогу данных",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = AppType.Supporting,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics { contentDescription = "Закрыть" },
                    ) {
                        Text("×", fontSize = AppType.DialogClose)
                    }
                }

                ProfileSetting(
                    selected = selectedProfile,
                    decodedCount = decodedCount,
                    totalCount = totalCount,
                    onSelected = onProfileSelected,
                )

                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Small)) {
                    Text(
                        text = "ТРАНСПОРТ VHAL · ВРЕМЕННО ДЛЯ СРАВНЕНИЯ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = AppType.Label,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small),
                    ) {
                        VhalGatewayBackend.entries.forEach { backend ->
                            BackendChoice(
                                backend = backend,
                                selected = backend == selectedBackend,
                                onClick = { onBackendSelected(backend) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Готово", fontSize = AppType.Action)
                }
            }
        }
    }
}

@Composable
private fun ProfileSetting(
    selected: VehicleProfile,
    decodedCount: Int,
    totalCount: Int,
    onSelected: (VehicleProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Small)) {
        Text(
            text = "ПРОФИЛЬ РАСШИФРОВКИ",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = AppType.Label,
            fontWeight = FontWeight.Bold,
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "${selected.key} · ${selected.vehicle}",
                        fontSize = AppType.BodyEmphasis,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (selected == VehicleProfile.RAW) {
                            "Без профильной расшифровки"
                        } else {
                            "Расшифровано $decodedCount из $totalCount VHAL-значений"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = AppType.Technical,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                VehicleProfile.entries.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(profile.key, fontWeight = FontWeight.Bold)
                                Text(profile.vehicle, fontSize = AppType.Technical)
                            }
                        },
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
private fun BackendChoice(
    backend: VhalGatewayBackend,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        label = {
            Column(Modifier.padding(vertical = AppSpacing.XSmall)) {
                Text(
                    text = backend.title,
                    fontSize = AppType.Body,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = backend.description,
                    fontSize = AppType.Technical,
                    maxLines = 2,
                )
            }
        },
    )
}
