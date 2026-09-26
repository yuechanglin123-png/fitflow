package com.fitflow.training.preset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PresetPickerDialog(
    title: String,
    slots: List<PresetSlot>,
    canSelect: (PresetSlot) -> Boolean = { true },
    onSelect: (PresetSlot) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                slots.forEach { entry ->
                    val preset = entry.preset
                    TextButton(
                        onClick = { onSelect(entry) },
                        enabled = canSelect(entry),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("槽位 ${entry.slot}", fontWeight = FontWeight.Bold)
                            Text(preset?.let { "${it.name} · ${it.exercises.size} 个动作" } ?: "尚未设置")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
