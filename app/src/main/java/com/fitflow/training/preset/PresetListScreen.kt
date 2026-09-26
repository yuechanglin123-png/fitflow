package com.fitflow.training.preset

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitflow.training.data.WorkoutRepository
import kotlinx.coroutines.launch

@Composable
fun PresetListScreen(
    onBack: () -> Unit,
    onEditSlot: (Int) -> Unit,
    repository: WorkoutRepository? = null,
) {
    val context = LocalContext.current
    val activeRepository = remember(context, repository) { repository ?: WorkoutRepository.open(context) }
    var slots by remember { mutableStateOf((1..7).map { PresetSlot(it, null) }) }
    var clearSlot by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() { slots = activeRepository.listPresetSlots() }
    LaunchedEffect(activeRepository) { refresh() }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF8F5EC)).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("训练预设", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        slots.forEach { entry ->
            val preset = entry.preset
            Card(Modifier.fillMaxWidth().clickable { onEditSlot(entry.slot) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("预设 ${entry.slot}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (preset == null) Text("尚未设置") else {
                        Text(preset.name, style = MaterialTheme.typography.titleLarge)
                        Text("${preset.exercises.size} 个动作 · ${preset.exercises.sumOf { it.blocks.size }} 组")
                        Row { TextButton(onClick = { clearSlot = entry.slot }) { Text("清空预设") } }
                    }
                }
            }
        }
    }
    clearSlot?.let { slot -> AlertDialog(
        onDismissRequest = { clearSlot = null },
        title = { Text("清空预设？") },
        text = { Text("该槽位的名称、动作和小组参数都会被删除。") },
        confirmButton = { TextButton(onClick = { scope.launch { activeRepository.clearPreset(slot); refresh(); clearSlot = null } }) { Text("确认清空") } },
        dismissButton = { TextButton(onClick = { clearSlot = null }) { Text("取消") } },
    ) }
}
