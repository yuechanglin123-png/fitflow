package com.fitflow.training.plan

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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitflow.training.catalog.CatalogRepository
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.session.SessionReducer
import com.fitflow.training.session.Phase
import com.fitflow.training.reminder.RestReminder
import com.fitflow.training.reminder.WorkoutForegroundService
import com.fitflow.training.preset.ImportMode
import com.fitflow.training.preset.PresetPickerDialog
import com.fitflow.training.preset.PresetRules
import com.fitflow.training.preset.PresetSlot
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun PlanScreen(
    onBack: () -> Unit,
    onStart: () -> Unit,
    onAddCatalog: () -> Unit,
    selectedExerciseId: String? = null,
    repository: WorkoutRepository? = null,
) {
    val context = LocalContext.current
    val activeRepository = remember(context, repository) { repository ?: WorkoutRepository.open(context) }
    val model = remember(activeRepository) { PlanViewModel(activeRepository) }
    val catalog = remember { CatalogRepository.load(context) }
    val plan by model.plan.collectAsState()
    val scope = rememberCoroutineScope()
    var customDialog by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var editingParent by remember { mutableStateOf<String?>(null) }
    var editingBlock by remember { mutableStateOf<PlannedBlock?>(null) }
    var addingBlock by remember { mutableStateOf(false) }
    var selectionConsumed by rememberSaveable(selectedExerciseId) { mutableStateOf(false) }
    var pendingCatalogParent by remember { mutableStateOf<String?>(null) }
    var exerciseRestParent by remember { mutableStateOf<PlannedExercise?>(null) }
    var exerciseRestText by remember { mutableStateOf("") }
    var noteDetail by remember { mutableStateOf<String?>(null) }
    var presetSlots by remember { mutableStateOf<List<PresetSlot>>(emptyList()) }
    var pickerPurpose by remember { mutableStateOf<PresetPickerPurpose?>(null) }
    var importTarget by remember { mutableStateOf<PresetSlot?>(null) }
    var saveTarget by remember { mutableStateOf<PresetSlot?>(null) }
    var saveName by remember { mutableStateOf("") }
    var presetError by remember { mutableStateOf<String?>(null) }
    var presetBusy by remember { mutableStateOf(false) }

    LaunchedEffect(selectedExerciseId) {
        model.load()
        if (selectedExerciseId != null && !selectionConsumed) {
            editingParent = model.addExercise(selectedExerciseId)
            pendingCatalogParent = editingParent
            selectionConsumed = true
            editingBlock = null
            addingBlock = true
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF8F5EC))
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("今日计划", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text("${plan.date} · ${plan.exercises.size} 个动作")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !presetBusy, onClick = {
                scope.launch {
                    presetSlots = model.listPresetSlots()
                    pickerPurpose = PresetPickerPurpose.IMPORT
                }
            }) { Text("导入预设") }
            TextButton(
                enabled = !presetBusy && plan.exercises.isNotEmpty() && plan.exercises.all { it.blocks.isNotEmpty() },
                onClick = {
                    scope.launch {
                        presetSlots = model.listPresetSlots()
                        pickerPurpose = PresetPickerPurpose.SAVE
                    }
                },
            ) { Text("保存为预设") }
        }
        presetError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onAddCatalog) { Text("从动作库添加") }
            TextButton(onClick = { customDialog = true }) { Text("新增自定义动作") }
        }
        plan.exercises.forEachIndexed { index, exercise ->
            val title = exercise.customName ?: catalog.all().firstOrNull { it.id == exercise.exerciseId }?.name ?: "未知动作"
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${index + 1}. $title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { scope.launch { model.moveExercise(exercise.id, index - 1) } }, enabled = index > 0) { Text("上移") }
                        TextButton(onClick = { scope.launch { model.moveExercise(exercise.id, index + 1) } }, enabled = index < plan.exercises.lastIndex) { Text("下移") }
                        TextButton(onClick = { deleteId = exercise.id }) { Text("删除动作") }
                    }
                    TextButton(onClick = { exerciseRestParent = exercise; exerciseRestText = exercise.exerciseRestSeconds.toString() }) {
                        Text("动作间休息：${exercise.exerciseRestSeconds} 秒 · 修改")
                    }
                    exercise.blocks.forEachIndexed { groupIndex, block ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Button(onClick = { scope.launch {
            if (repository == null) com.fitflow.training.data.WorkoutRuntime.get(context).session.start(model.startWorkout())
            else activeRepository.saveSession(SessionReducer.start(model.startWorkout(), UUID.randomUUID().toString()))
            WorkoutForegroundService.start(context)
            onStart()
        } }, enabled = model.canStart(), modifier = Modifier.fillMaxWidth()) {
            Text("开始训练")
        }
    }

    if (customDialog) AlertDialog(
        onDismissRequest = { customDialog = false },
        title = { Text("新增自定义动作") },
        text = { OutlinedTextField(customName, { customName = it }, label = { Text("动作名称") }) },
        confirmButton = { TextButton(onClick = {
            scope.launch { model.addCustomExercise(customName); customName = ""; customDialog = false }
        }, enabled = customName.isNotBlank()) { Text("添加") } },
        dismissButton = { TextButton(onClick = { customDialog = false }) { Text("取消") } },
    )
    deleteId?.let { id -> AlertDialog(
        onDismissRequest = { deleteId = null },
        title = { Text("删除这个动作？") },
        text = { Text("动作及其计划小卡将从今日计划移除。") },
        confirmButton = { TextButton(onClick = { scope.launch { model.deleteExercise(id); deleteId = null } }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } },
    ) }
    editingParent?.let { parentId ->
        val onDismiss = {
                if (pendingCatalogParent == parentId) scope.launch { model.deleteExercise(parentId) }
                pendingCatalogParent = null
                editingParent = null
            }
        val onSave: (PlannedBlock) -> Unit = { block ->
                scope.launch {
                    if (addingBlock) model.addBlock(parentId, block) else model.editBlock(parentId, block)
                    pendingCatalogParent = null
                    editingParent = null
                }
            }
        if (addingBlock) BlockEditor(original = null, onDismiss = onDismiss, onSave = onSave)
        else SetEditor(original = requireNotNull(editingBlock), onDismiss = onDismiss, onSave = onSave)
    }
    exerciseRestParent?.let { exercise -> AlertDialog(
        onDismissRequest = { exerciseRestParent = null },
        title = { Text("修改动作间休息") },
        text = { OutlinedTextField(exerciseRestText, { exerciseRestText = it }, label = { Text("动作间休息（秒）") }, singleLine = true) },
        confirmButton = { TextButton(
            enabled = exerciseRestText.toIntOrNull() in 0..3600,
            onClick = { scope.launch { model.editExerciseRest(exercise.id, exerciseRestText.toInt()); exerciseRestParent = null } },
        ) { Text("保存") } },
        dismissButton = { TextButton(onClick = { exerciseRestParent = null }) { Text("取消") } },
    ) }
    noteDetail?.let { NoteDetailDialog(it) { noteDetail = null } }
    pickerPurpose?.let { purpose ->
        PresetPickerDialog(
            title = if (purpose == PresetPickerPurpose.IMPORT) "导入预设" else "保存为预设",
            slots = presetSlots,
            canSelect = { entry -> purpose == PresetPickerPurpose.SAVE || entry.preset?.let { PresetRules.validateImport(it).valid } == true },
            onSelect = { entry ->
                pickerPurpose = null
                if (purpose == PresetPickerPurpose.SAVE) {
                    saveTarget = entry
                    saveName = entry.preset?.name ?: "预设 ${entry.slot}"
                } else if (plan.exercises.isEmpty()) {
                    presetBusy = true
                    scope.launch {
                        try {
                            runCatching { model.importPreset(entry.slot, ImportMode.REPLACE) }
                                .onFailure { presetError = "导入失败：${it.message ?: "预设不可用"}" }
                        } finally { presetBusy = false }
                    }
                } else importTarget = entry
            },
            onDismiss = { pickerPurpose = null },
        )
    }
    importTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { importTarget = null },
            title = { Text("导入“${entry.preset?.name}”") },
            text = { Text("今日计划已有内容，请选择本次导入方式。") },
            confirmButton = {
                TextButton(enabled = !presetBusy, onClick = {
                    presetBusy = true
                    scope.launch {
                        try {
                            runCatching { model.importPreset(entry.slot, ImportMode.REPLACE) }
                                .onFailure { presetError = "导入失败：${it.message ?: "预设不可用"}" }
                            importTarget = null
                        } finally { presetBusy = false }
                    }
                }) { Text("覆盖今日计划") }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = !presetBusy, onClick = {
                        presetBusy = true
                        scope.launch {
                            try {
                                runCatching { model.importPreset(entry.slot, ImportMode.APPEND) }
                                    .onFailure { presetError = "导入失败：${it.message ?: "预设不可用"}" }
                                importTarget = null
                            } finally { presetBusy = false }
                        }
                    }) { Text("追加到末尾") }
                    TextButton(onClick = { importTarget = null }) { Text("取消") }
                }
            },
        )
    }
    saveTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { saveTarget = null },
            title = { Text(if (entry.preset == null) "保存到槽位 ${entry.slot}" else "覆盖槽位 ${entry.slot}？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry.preset != null) Text("原预设“${entry.preset.name}”将被完整替换。")
                    OutlinedTextField(saveName, { saveName = it }, label = { Text("预设名称") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !presetBusy && saveName.trim().length in 1..30,
                    onClick = {
                        presetBusy = true
                        scope.launch {
                            try {
                                runCatching { model.saveAsPreset(entry.slot, saveName) }
                                    .onFailure { presetError = "保存失败：${it.message ?: "计划不完整"}" }
                                saveTarget = null
                            } finally { presetBusy = false }
                        }
                    },
                ) { Text(if (entry.preset == null) "保存" else "确认覆盖") }
            },
            dismissButton = { TextButton(onClick = { saveTarget = null }) { Text("取消") } },
        )
    }
}

private enum class PresetPickerPurpose { IMPORT, SAVE }

@Composable
fun SetEditor(original: PlannedBlock, onDismiss: () -> Unit, onSave: (PlannedBlock) -> Unit) {
    var weight by remember(original) { mutableStateOf(original.weightKg.toPlainString()) }
    var reps by remember(original) { mutableStateOf(original.reps.toString()) }
    var rest by remember(original) { mutableStateOf(original.restSeconds.toString()) }
    var note by remember(original) { mutableStateOf(original.note) }
    val candidate = runCatching { original.copy(weightKg = BigDecimal(weight), reps = reps.toInt(), restSeconds = rest.toInt(), note = note, sets = 1) }.getOrNull()
    val valid = candidate != null && PlanRules.validate(candidate).valid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改本组参数") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(weight, { weight = it }, label = { Text("重量（kg）") }, singleLine = true)
            OutlinedTextField(reps, { reps = it }, label = { Text("每组计划次数") }, singleLine = true)
            OutlinedTextField(rest, { rest = it }, label = { Text("本组后休息（秒）") }, singleLine = true)
            OutlinedTextField(note, { note = it }, label = { Text("备注") })
        } },
        confirmButton = { TextButton(onClick = { candidate?.let(onSave) }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun BlockEditor(original: PlannedBlock?, onDismiss: () -> Unit, onSave: (PlannedBlock) -> Unit) {
    var weight by remember(original) { mutableStateOf(original?.weightKg?.toPlainString() ?: "") }
    var sets by remember(original) { mutableStateOf(original?.sets?.toString() ?: "") }
    var reps by remember(original) { mutableStateOf(original?.reps?.toString() ?: "") }
    var rest by remember(original) { mutableStateOf(original?.restSeconds?.toString() ?: "") }
    var note by remember(original) { mutableStateOf(original?.note ?: "") }
    val candidate = runCatching {
        PlannedBlock(original?.id ?: UUID.randomUUID().toString(), BigDecimal(weight), sets.toInt(), reps.toInt(), rest.toInt(), note)
    }.getOrNull()
    val valid = candidate != null && PlanRules.validate(candidate).valid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "添加计划小卡" else "修改计划小卡") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(weight, { weight = it }, label = { Text("重量（kg）") }, singleLine = true)
                OutlinedTextField(sets, { sets = it }, label = { Text("计划组数") }, singleLine = true)
                OutlinedTextField(reps, { reps = it }, label = { Text("每组计划次数") }, singleLine = true)
                OutlinedTextField(rest, { rest = it }, label = { Text("组间休息（秒）") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("备注") })
                if (candidate != null && !valid) Text(PlanRules.validate(candidate).errors.values.first(), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { candidate?.let(onSave) }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
