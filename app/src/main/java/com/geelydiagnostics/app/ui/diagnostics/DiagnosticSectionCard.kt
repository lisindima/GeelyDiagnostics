package com.geelydiagnostics.app.ui.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import com.geelydiagnostics.app.ui.theme.*

@Composable
internal fun DiagnosticSectionCard(
    title: String,
    subtitle: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Surface(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().semantics {
                stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто"
            },
            color = if (expanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                Modifier.padding(AppSpacing.CardContent),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Default),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.Small)) {
                    Text(title, fontSize = AppType.CardTitle, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, fontSize = AppType.Supporting)
                }
                Text(if (expanded) "−" else "+", fontSize = AppType.CardTitle)
            }
        }
        if (expanded) SelectionContainer {
            Column(
                Modifier.padding(AppSpacing.CardContent),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Technical),
                content = content,
            )
        }
    }
}

@Composable
internal fun DiagnosticDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.Default)) {
        Text(label, Modifier.weight(1f), fontSize = AppType.Supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), fontSize = AppType.BodyEmphasis, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun DiagnosticNote(text: String, isError: Boolean = false) = Text(
    text,
    fontSize = AppType.Supporting,
    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
)
