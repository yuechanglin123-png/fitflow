package com.fitflow.training.session

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitflow.training.catalog.CatalogRepository
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.reminder.RestReminder
import com.fitflow.training.reminder.WorkoutForegroundService
import com.fitflow.training.plan.SetEditor
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.CompactNote
import com.fitflow.training.plan.NoteDetailDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SessionScreen(onBack: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val reminder = remember { RestReminder(context) }
    val model = remember { com.fitflow.training.data.WorkoutRuntime.get(context).session }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val catalog = remember { CatalogRepository.load(context) }
    val state by model.session.collectAsState()
    val scope = rememberCoroutineScope()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editConfirm by remember { mutableStateOf<PlannedBlock?>(null) }
    var editingBlock by remember { mutableStateOf<PlannedBlock?>(null) }
    var deleteConfirm by remember { mutableStateOf<PlannedBlock?>(null) }
    var moveConfirm by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var exerciseRestEdit by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var noteDetail by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { model.resume() }
    LaunchedEffect(state?.phase) {
        when (state?.phase) {
            Phase.READY, Phase.RESTING -> WorkoutForegroundService.start(context)
            Phase.FINISHED -> WorkoutForegroundService.stop(context)
            null -> Unit
        }
    }
    LaunchedEffect(state?.phase) {
        while (state?.phase == Phase.RESTING) { nowMs = System.currentTimeMillis(); model.reconcile(); delay(500) }
    }

    val snapshot = state
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF8F5EC)).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("返回今日计划") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("训练中", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (snapshot != null && snapshot.phase != Phase.FINISHED) Button(
                onClick = { scope.launch { if (snapshot.isPaused) model.resumeTraining() else model.pause() } },
                modifier = Modifier.size(64.dp).testTag("pause-training-button").semantics {
                    contentDescription = if (snapshot.isPaused) "继续训练" else "暂停训练"
                },
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
            ) { PauseResumeIcon(isPaused = snapshot.isPaused) }
        }
        if (snapshot == null) {
            Text("尚未开始训练")
        } else {
            val current = snapshot.plan.exercises.flatMap { exercise -> exercise.blocks.map { exercise to it } }
                .firstOrNull { it.second.id == snapshot.currentBlockId }
            val currentActionIndex = snapshot.plan.exercises.indexOfFirst { it.id == current?.first?.id }
            val lastStartedIndex = snapshot.plan.exercises.indexOfLast { item ->
                item.blocks.any { (snapshot.completedBlockSets[it.id] ?: 0) > 0 }
            }
            Text("已完成 ${snapshot.completedBlockSets.values.sum()} 组")
            if (!reminder.capability().notifications || !reminder.capability().exactAlarms) Text("系统提醒权限未完全开启；请以应用内倒计时为准。", color = MaterialTheme.colorScheme.error)
            if (Build.VERSION.SDK_INT >= 33 && !reminder.capability().notifications) TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("开启通知") }
            when (snapshot.phase) {
                Phase.READY -> Text("当前组已就绪", style = MaterialTheme.typography.titleMedium)
                Phase.RESTING -> {
                    val remainingMillis = if (snapshot.isPaused) snapshot.pausedRestRemainingMillis ?: 0L
                        else ((snapshot.restEndsAtEpochMs ?: 0L) - nowMs).coerceAtLeast(0L)
                    val remaining = (remainingMillis + 999) / 1000
                    Text("${if (snapshot.restKind == RestKind.EXERCISE) "动作间休息" else "组间休息"}：${remaining} 秒", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { scope.launch { model.skipRest() } }, enabled = !snapshot.isPaused) { Text("跳过休息") }
                        TextButton(onClick = { scope.launch { model.extendRest(30) } }, enabled = !snapshot.isPaused) { Text("延长 30 秒") }
                    }
                }
                Phase.FINISHED -> Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("查看训练总结") }
            }
            if (snapshot.isPaused) Text("训练已暂停", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (snapshot.phase != Phase.FINISHED) com.fitflow.training.assistant.AssistantPanel(
                remember { com.fitflow.training.assistant.AssistantRuntime.get(context) }
            )
            current?.let { (exercise, planned) ->
                val groupIndex = exercise.blocks.indexOf(planned)
                val completed = snapshot.completedBlockSets[planned.id] ?: 0
                val laterActionExists = snapshot.plan.exercises.drop(currentActionIndex + 1).any { it.blocks.isNotEmpty() }
                val restDescription = when {
                    groupIndex < exercise.blocks.lastIndex -> "本组后休息 ${planned.restSeconds} 秒"
                    laterActionExists -> "动作结束后休息 ${exercise.exerciseRestSeconds} 秒"
                    else -> "完成后进入训练总结"
                }
                Card(Modifier.fillMaxWidth().testTag("current-set-card")) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("第 ${groupIndex + 1}/${exercise.blocks.size} 组", style = MaterialTheme.typography.titleLarge)
                        Text("${planned.weightKg.stripTrailingZeros().toPlainString()} kg · ${planned.reps} 次")
                        Text(restDescription)
                        if (planned.note.isNotBlank()) CompactNote(planned.note) { noteDetail = planned.note }
                        if (completed > 0) Text("已完成")
                        if (snapshot.phase == Phase.READY) Button(
                            onClick = { scope.launch { model.completeSet() } }, enabled = !snapshot.isPaused,
                        ) { Text("完成本组") }
                        if (snapshot.phase == Phase.RESTING) Text(if (snapshot.isPaused) "休息已暂停" else "休息中")
                        Row {
                            TextButton(onClick = { editConfirm = planned }, enabled = completed == 0) { Text("修改本组") }
                            TextButton(onClick = { deleteConfirm = planned }, enabled = completed == 0 && !snapshot.isPaused) { Text("删除本组") }
                        }
                    }
                }
            }
            actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("训练动作与小组", style = MaterialTheme.typography.titleMedium)
            snapshot.plan.exercises.forEachIndexed { index, item ->
                val title = item.customName ?: catalog.all().firstOrNull { it.id == item.exerciseId }?.name ?: "动作"
                if (index > 0) HorizontalDivider()
                Text(if (index > currentActionIndex && currentActionIndex >= 0) "下一动作：$title" else title,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("已完成 ${item.blocks.sumOf { snapshot.completedBlockSets[it.id] ?: 0 }} / ${item.blocks.size} 组 · 动作间休息 ${item.exerciseRestSeconds} 秒")
                if (item.blocks.any { (snapshot.completedBlockSets[it.id] ?: 0) == 0 } && index < snapshot.plan.exercises.lastIndex) {
                    TextButton(onClick = { exerciseRestEdit = item.id to item.exerciseRestSeconds }, enabled = !snapshot.isPaused) { Text("修改动作间休息") }
                }
                if (snapshot.phase != Phase.FINISHED && item.blocks.all { (snapshot.completedBlockSets[it.id] ?: 0) == 0 }) Row {
                    if (index > 0 && index - 1 > lastStartedIndex) TextButton(onClick = { moveConfirm = item.id to index - 1 }, enabled = !snapshot.isPaused) { Text("上移待练动作") }
                    if (index < snapshot.plan.exercises.lastIndex) TextButton(onClick = { moveConfirm = item.id to index + 1 }, enabled = !snapshot.isPaused) { Text("下移待练动作") }
                }
                item.blocks.forEachIndexed { groupIndex, planned ->
                    val completed = snapshot.completedBlockSets[planned.id] ?: 0
                    val isCurrent = snapshot.currentBlockId == planned.id && snapshot.phase != Phase.FINISHED
                    val laterActionExists = snapshot.plan.exercises.drop(index + 1).any { it.blocks.isNotEmpty() }
                    val restDescription = when {
                        groupIndex < item.blocks.lastIndex -> "本组后休息 ${planned.restSeconds} 秒"
                        laterActionExists -> "动作结束后休息 ${item.exerciseRestSeconds} 秒"
                        else -> "完成后进入训练总结"
                    }
                    Card(Modifier.fillMaxWidth().testTag("session-set-card-${planned.id}")) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("第 ${groupIndex + 1}/${item.blocks.size} 组", style = MaterialTheme.typography.titleLarge)
                            Text("${planned.weightKg.stripTrailingZeros().toPlainString()} kg · ${planned.reps} 次")
                            Text(restDescription)
                            if (planned.note.isNotBlank()) CompactNote(planned.note) { noteDetail = planned.note }
                            if (completed > 0) Text("已完成")
                            if (isCurrent && snapshot.phase == Phase.READY)
                                Button(onClick = { scope.launch { model.completeSet() } }, enabled = !snapshot.isPaused) { Text("完成本组") }
                            if (isCurrent && snapshot.phase == Phase.RESTING) Text("休息中")
                            Row {
                                val canEdit = completed == 0 && snapshot.phase != Phase.FINISHED
                                TextButton(onClick = { editConfirm = planned }, enabled = canEdit) { Text("修改本组") }
                                TextButton(onClick = { deleteConfirm = planned }, enabled = canEdit && !snapshot.isPaused) { Text("删除本组") }
                            }
                        }
                    }
                }
            }
        }
    }
    editConfirm?.let { planned -> AlertDialog(
        onDismissRequest = { editConfirm = null },
        title = { Text("修改剩余计划？") },
        text = { Text("已完成组数会保留；正在进行的休息时间不受本次修改影响。") },
        confirmButton = { TextButton(onClick = { editingBlock = planned; editConfirm = null }) { Text("继续修改") } },
        dismissButton = { TextButton(onClick = { editConfirm = null }) { Text("取消") } },
    ) }
    editingBlock?.let { planned -> SetEditor(
        original = planned,
        onDismiss = { editingBlock = null },
        onSave = { updated -> scope.launch {
            try { model.editUnfinishedBlock(updated); actionError = null; editingBlock = null }
            catch (error: Exception) { actionError = error.message ?: "修改失败" }
        } },
    ) }
    exerciseRestEdit?.let { (parentId, initialSeconds) ->
        var secondsText by remember(parentId) { mutableStateOf(initialSeconds.toString()) }
        val seconds = secondsText.toIntOrNull()
        AlertDialog(
            onDismissRequest = { exerciseRestEdit = null },
            title = { Text("修改动作间休息") },
            text = { OutlinedTextField(value = secondsText, onValueChange = { secondsText = it }, label = { Text("休息秒数（0–3600）") }) },
            confirmButton = { TextButton(
                enabled = seconds != null && seconds in 0..3600,
                onClick = { scope.launch {
                    try { model.editExerciseRest(parentId, requireNotNull(seconds)); actionError = null; exerciseRestEdit = null }
                    catch (error: Exception) { actionError = error.message ?: "修改失败" }
                } },
            ) { Text("保存") } },
            dismissButton = { TextButton(onClick = { exerciseRestEdit = null }) { Text("取消") } },
        )
    }
    moveConfirm?.let { (id, index) -> AlertDialog(
        onDismissRequest = { moveConfirm = null },
        title = { Text("调整待练顺序？") },
        text = { Text("已完成组数不会改变；当前休息结束后将按新的顺序继续。") },
        confirmButton = { TextButton(onClick = { scope.launch {
            try { model.moveUnfinishedExercise(id, index); actionError = null; moveConfirm = null }
            catch (error: Exception) { actionError = error.message ?: "排序失败" }
        } }) { Text("确认调整") } },
        dismissButton = { TextButton(onClick = { moveConfirm = null }) { Text("取消") } },
    ) }
    deleteConfirm?.let { planned -> AlertDialog(
        onDismissRequest = { deleteConfirm = null },
        title = { Text("删除未开始小卡？") },
        text = { Text("删除后训练顺序会更新，已经完成的组数保持不变。") },
        confirmButton = { TextButton(onClick = { scope.launch {
            try { model.deleteUnfinishedBlock(planned.id); actionError = null; deleteConfirm = null }
            catch (error: Exception) { actionError = error.message ?: "删除失败" }
        } }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { deleteConfirm = null }) { Text("取消") } },
    ) }
    noteDetail?.let { NoteDetailDialog(it) { noteDetail = null } }
}

@Composable
private fun PauseResumeIcon(isPaused: Boolean) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        if (isPaused) {
            val path = Path().apply {
                moveTo(size.width * 0.32f, size.height * 0.22f)
                lineTo(size.width * 0.76f, size.height * 0.5f)
                lineTo(size.width * 0.32f, size.height * 0.78f)
                close()
            }
            drawPath(path, color)
        } else {
            val barWidth = size.width * 0.2f
            val barHeight = size.height * 0.58f
            val top = size.height * 0.21f
            drawRoundRect(color, Offset(size.width * 0.23f, top), Size(barWidth, barHeight), CornerRadius(2.dp.toPx()))
            drawRoundRect(color, Offset(size.width * 0.57f, top), Size(barWidth, barHeight), CornerRadius(2.dp.toPx()))
        }
    }
}
