package com.fitflow.training.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.catalog.CatalogRepository
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.SetEditor
import com.fitflow.training.plan.WorkoutPlan
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
internal fun CheckInEditor(
    date: LocalDate,
    initial: CheckIn?,
    repository: WorkoutRepository,
    catalog: CatalogRepository,
    onBack: () -> Unit,
    onSaved: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var plan by remember(date, initial) { mutableStateOf(initial?.planSnapshot ?: WorkoutPlan(date, emptyList())) }
    var category by remember(date, initial) { mutableStateOf(initial?.category ?: TrainingCategory.STRENGTH) }
    var completedBlockSets by remember(date, initial) {
        mutableStateOf<Map<String, Int>?>(initial?.completedBlockSets ?: if (initial == null) emptyMap() else null)
    }
    var enteredLegacyExercises by remember(date, initial) { mutableStateOf(emptySet<String>()) }
    var addingExercise by remember { mutableStateOf(false) }
    var exerciseName by remember { mutableStateOf("") }
    var editingBlock by remember { mutableStateOf<Pair<String, PlannedBlock>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(date, initial) {
        if (initial == null) {
            repository.loadPlan(date)?.let { saved ->
                if (plan.exercises.isEmpty()) plan = saved
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF8F5EC))
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("返回日历") }
        Text("补卡 · $date", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("训练类别", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TrainingCategory.entries.forEach { option ->
                FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option.label) })
            }
        }
        Text("今日训练计划", style = MaterialTheme.typography.titleMedium)
        if (plan.exercises.isEmpty()) Text("请先添加训练动作和训练小卡")
        plan.exercises.forEachIndexed { index, exercise ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val name = exercise.customName ?: catalog.all().firstOrNull { it.id == exercise.exerciseId }?.name ?: "动作"
                    Text("${index + 1}. $name", fontWeight = FontWeight.Bold)
                    val total = exercise.blocks.sumOf { it.sets }
                    val completed = exercise.blocks.sumOf { completedBlockSets?.get(it.id) ?: 0 }
                    OutlinedTextField(
                        value = if (completedBlockSets == null ||
                            (initial?.completedBlockSets == null && initial != null && exercise.id !in enteredLegacyExercises)
                        ) "" else completed.toString(),
                        onValueChange = { text ->
                            text.toIntOrNull()?.takeIf { it in 0..total }?.let { group ->
                                val blockIds = exercise.blocks.map { it.id }.toSet()
                                val replacement = CheckInRules.progressFromCompletedGroups(
                                    WorkoutPlan(date, listOf(exercise)), mapOf(exercise.id to group),
                                )
                                completedBlockSets = completedBlockSets.orEmpty().filterKeys { it !in blockIds } + replacement
                                enteredLegacyExercises = enteredLegacyExercises + exercise.id
                            }
                        },
                        label = { Text("已完成到第几组（共 $total 组）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = exercise.exerciseRestSeconds.toString(),
                        onValueChange = { text ->
                            text.toIntOrNull()?.takeIf { it in 0..3600 }?.let { seconds ->
                                plan = plan.copy(exercises = plan.exercises.map { if (it.id == exercise.id) it.copy(exerciseRestSeconds = seconds) else it })
                            }
                        },
                        label = { Text("动作间休息（秒）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    exercise.blocks.forEachIndexed { blockIndex, block ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("第 ${blockIndex + 1} 组 · ${block.weightKg.stripTrailingZeros().toPlainString()} kg · ${block.reps} 次 · 休息 ${block.restSeconds} 秒")
                                if (block.note.isNotBlank()) Text("备注：${block.note}")
                                Row {
                                    TextButton(onClick = { editingBlock = exercise.id to block }) { Text("修改小卡") }
                                    TextButton(onClick = {
                                        plan = plan.copy(exercises = plan.exercises.map {
                                            if (it.id == exercise.id) it.copy(blocks = it.blocks.filterNot { candidate -> candidate.id == block.id }) else it
                                        })
                                        completedBlockSets = completedBlockSets?.minus(block.id)
                                    }) { Text("删除小卡") }
                                }
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = {
                            editingBlock = exercise.id to PlannedBlock(UUID.randomUUID().toString(), BigDecimal.ZERO, 1, 1, 60, "")
                        }) { Text("添加训练小卡") }
                        TextButton(onClick = {
                            plan = plan.copy(exercises = plan.exercises.filterNot { it.id == exercise.id })
                            completedBlockSets = completedBlockSets?.filterKeys { id -> exercise.blocks.none { it.id == id } }
                            enteredLegacyExercises = enteredLegacyExercises - exercise.id
                        }) { Text("删除动作") }
                    }
                }
            }
        }
        TextButton(onClick = { exerciseName = ""; addingExercise = true }) { Text("添加训练动作") }
        val completedIds = if (completedBlockSets == null) initial?.completedExerciseIds.orEmpty()
            else plan.exercises.filter { exercise ->
                exercise.blocks.any { (completedBlockSets?.get(it.id) ?: 0) > 0 }
            }.mapNotNull { it.exerciseId }
        val entry = CheckIn(date, completedIds, plan, category, completedBlockSets)
        val allowUnknown = initial != null && initial.completedBlockSets == null && completedBlockSets == null
        val validation = CheckInRules.validateBackfill(entry, allowUnknownProgress = allowUnknown)
        val completeLegacyInput = initial?.completedBlockSets != null || initial == null ||
            completedBlockSets == null || plan.exercises.all { it.id in enteredLegacyExercises }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                saving = true
                scope.launch {
                    try {
                        repository.saveBackfill(entry)
                        onSaved()
                    } catch (failure: Exception) {
                        error = failure.message ?: "保存失败"
                    } finally { saving = false }
                }
            },
            enabled = validation.valid && completeLegacyInput && !saving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存打卡") }
        if (!validation.valid) Text(validation.errors.first(), color = MaterialTheme.colorScheme.error)
        else if (!completeLegacyInput) Text("请填写每个动作完成到第几组", color = MaterialTheme.colorScheme.error)
    }

    if (addingExercise) {
        AlertDialog(
            onDismissRequest = { addingExercise = false },
            title = { Text("添加训练动作") },
            text = { OutlinedTextField(exerciseName, { exerciseName = it }, label = { Text("动作名称") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = exerciseName.trim().isNotEmpty(), onClick = {
                    val exercise = PlannedExercise(UUID.randomUUID().toString(), null, exerciseName.trim(), emptyList())
                    plan = plan.copy(exercises = plan.exercises + exercise)
                    addingExercise = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { addingExercise = false }) { Text("取消") } },
        )
    }
    editingBlock?.let { (exerciseId, block) ->
        SetEditor(original = block, onDismiss = { editingBlock = null }, onSave = { changed ->
            plan = plan.copy(exercises = plan.exercises.map { exercise ->
                if (exercise.id != exerciseId) exercise else if (exercise.blocks.any { it.id == changed.id })
                    exercise.copy(blocks = exercise.blocks.map { if (it.id == changed.id) changed else it })
                else exercise.copy(blocks = exercise.blocks + changed)
            })
            editingBlock = null
        })
    }
}
