package com.fitflow.training.preset

import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.fitflow.training.catalog.CatalogRepository
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.plan.BlockEditor
import com.fitflow.training.plan.CompactNote
import com.fitflow.training.plan.NoteDetailDialog
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.SetEditor
import kotlinx.coroutines.launch

@Composable
fun PresetEditScreen(
    slot: Int,
    selectedExerciseId: String?,
    onBack: () -> Unit,
    onAddCatalog: () -> Unit,
    onExerciseConsumed: () -> Unit = {},
    repository: WorkoutRepository? = null,
) {
    val context = LocalContext.current
    val activeRepository = remember(context, repository) { repository ?: WorkoutRepository.open(context) }
    val catalog = remember { CatalogRepository.load(context) }
    val model = remember(activeRepository, slot) { PresetViewModel(activeRepository, slot) }
    val preset by model.preset.collectAsState()
    val scope = rememberCoroutineScope()
    var name by remember(slot) { mutableStateOf("预设 $slot") }
    var customDialog by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var editingParent by remember { mutableStateOf<String?>(null) }
    var editingBlock by remember { mutableStateOf<PlannedBlock?>(null) }
    var addingBlock by remember { mutableStateOf(false) }
    var restParent by remember { mutableStateOf<String?>(null) }
    var restText by remember { mutableStateOf("") }
    var noteDetail by remember { mutableStateOf<String?>(null) }
    var consumedExercise by remember(slot) { mutableStateOf<String?>(null) }

    LaunchedEffect(preset.name) { name = preset.name }
    LaunchedEffect(model, selectedExerciseId) {
        model.load()
        if (selectedExerciseId != null && consumedExercise != selectedExerciseId) {
            model.addExercise(selectedExerciseId)
            consumedExercise = selectedExerciseId
            onExerciseConsumed()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF8F5EC)).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("返回预设列表") }
        Text("编辑预设 $slot", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("预设名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        TextButton(onClick = { scope.launch { model.rename(name) } }, enabled = name.trim().length in 1..30) { Text("保存名称") }
        Row {
            TextButton(onClick = onAddCatalog) { Text("从动作库添加") }
            TextButton(onClick = { customDialog = true }) { Text("新增自定义动作") }
        }
        if (!PresetRules.validateImport(preset).valid) Text("预设内容尚不能导入", color = MaterialTheme.colorScheme.error)
        preset.exercises.forEachIndexed { index, exercise ->
            val title = exercise.customName ?: catalog.all().firstOrNull { it.id == exercise.exerciseId }?.name ?: "动作"
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${index + 1}. $title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = { scope.launch { model.moveExercise(exercise.id, index - 1) } }, enabled = index > 0) { Text("上移") }
                        TextButton(onClick = { scope.launch { model.moveExercise(exercise.id, index + 1) } }, enabled = index < preset.exercises.lastIndex) { Text("下移") }
                        TextButton(onClick = { scope.launch { model.deleteExercise(exercise.id) } }) { Text("删除动作") }
                    }
                    TextButton(onClick = { restParent = exercise.id; restText = exercise.exerciseRestSeconds.toString() }) { Text("动作间休息：${exercise.exerciseRestSeconds} 秒 · 修改") }
                    exercise.blocks.forEachIndexed { groupIndex, block ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("第 ${groupIndex + 1} 组", style = MaterialTheme.typography.titleMedium)
                                Text("${block.weightKg.stripTrailingZeros().toPlainString()} kg · ${block.reps} 次 · 休息 ${block.restSeconds} 秒")
                                if (block.note.isNotBlank()) CompactNote(block.note) { noteDetail = block.note }
                                Row {
                                    TextButton(onClick = { editingParent = exercise.id; editingBlock = block; addingBlock = false }) { Text("修改") }
                                    TextButton(onClick = { scope.launch { model.deleteBlock(exercise.id, block.id) } }) { Text("删除小卡") }
                                }
                            }
                        }
                    }
                    TextButton(onClick = { editingParent = exercise.id; editingBlock = null; addingBlock = true }) { Text("+ 添加小组") }
                }
            }
        }
    }

    if (customDialog) AlertDialog(
        onDismissRequest = { customDialog = false },
        title = { Text("新增自定义动作") },
        text = { OutlinedTextField(customName, { customName = it }, label = { Text("动作名称") }) },
        confirmButton = { TextButton(onClick = { scope.launch { model.addCustomExercise(customName); customName = ""; customDialog = false } }, enabled = customName.isNotBlank()) { Text("添加") } },
        dismissButton = { TextButton(onClick = { customDialog = false }) { Text("取消") } },
    )
    editingParent?.let { parentId ->
        val dismiss = { editingParent = null }
        val save: (PlannedBlock) -> Unit = { block -> scope.launch {
            if (addingBlock) model.addBlock(parentId, block) else model.editBlock(parentId, block)
            editingParent = null
        } }
        if (addingBlock) BlockEditor(null, dismiss, save) else SetEditor(requireNotNull(editingBlock), dismiss, save)
    }
    restParent?.let { parentId -> AlertDialog(
        onDismissRequest = { restParent = null },
        title = { Text("修改动作间休息") },
        text = { OutlinedTextField(restText, { restText = it }, label = { Text("动作间休息（秒）") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { scope.launch { model.editExerciseRest(parentId, restText.toInt()); restParent = null } }, enabled = restText.toIntOrNull() in 0..3600) { Text("保存") } },
        dismissButton = { TextButton(onClick = { restParent = null }) { Text("取消") } },
    ) }
    noteDetail?.let { NoteDetailDialog(it) { noteDetail = null } }
}
