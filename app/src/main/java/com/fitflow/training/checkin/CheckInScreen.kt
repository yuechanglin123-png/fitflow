package com.fitflow.training.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.fitflow.training.catalog.CatalogRepository
import com.fitflow.training.data.WorkoutRepository
import java.time.LocalDate
import java.time.YearMonth

private val Sand = Color(0xFFF8F5EC)

@Composable
fun CheckInScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { WorkoutRepository.open(context) }
    val catalog = remember(context) { CatalogRepository.load(context) }
    val today = LocalDate.now()
    var entries by remember { mutableStateOf(emptyList<CheckIn>()) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(Unit) { entries = repository.listCheckIns() }
    val selectedDate = LocalDate.parse(selectedText)
    val selectedEntry = entries.firstOrNull { it.date == selectedDate }
    val month = YearMonth.parse(monthText)

    if (editingDate != null) {
        CheckInEditor(
            date = requireNotNull(editingDate),
            initial = selectedEntry,
            repository = repository,
            catalog = catalog,
            onBack = { editingDate = null },
            onSaved = {
                entries = repository.listCheckIns()
                editingDate = null
            },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().background(Sand).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("每日训练打卡", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("上个月") }
            Text("${month.year} 年 ${month.monthValue} 月", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { monthText = month.plusMonths(1).toString() }) { Text("下个月") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(day, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val cellCount = ((offset + month.lengthOfMonth() + 6) / 7) * 7
        repeat(cellCount / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val number = week * 7 + weekday - offset + 1
                    if (number in 1..month.lengthOfMonth()) {
                        val date = month.atDay(number)
                        val checked = entries.any { it.date == date }
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                                .testTag("calendar-day-$date")
                                .clickable { selectedText = date.toString() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (checked) "$number•" else number.toString(),
                                color = if (date == selectedDate) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (date == selectedDate || checked) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    } else Box(Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
        Text(selectedDate.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (selectedEntry == null) {
            Text("该日暂无打卡")
            if (selectedDate.isBefore(today)) {
                TextButton(onClick = { editingDate = selectedDate }) { Text("补卡这一天") }
            }
        } else {
            CheckInDetails(selectedEntry, catalog)
            if (!selectedDate.isAfter(today)) {
                TextButton(onClick = { editingDate = selectedDate }) { Text("编辑打卡") }
            }
        }
    }
}

@Composable
private fun CheckInDetails(entry: CheckIn, catalog: CatalogRepository) {
    Text("打卡类别：${entry.category.label}")
    val percent = CheckInRules.completionPercent(entry)
    Text(if (percent == null) "训练完成度：未记录" else "训练完成度：${CheckInRules.completedSets(entry)} / ${CheckInRules.plannedSets(entry)} 组（$percent%）")
    entry.planSnapshot.exercises.forEach { exercise ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                val name = exercise.customName ?: catalog.all().firstOrNull { it.id == exercise.exerciseId }?.name ?: "动作"
                Text(name, fontWeight = FontWeight.Bold)
                if (entry.completedBlockSets != null) {
                    Text("已完成 ${exercise.blocks.sumOf { entry.completedBlockSets[it.id] ?: 0 }} / ${exercise.blocks.sumOf { it.sets }} 组")
                }
                exercise.blocks.forEachIndexed { index, block ->
                    Text("第 ${index + 1} 组 · ${block.weightKg.stripTrailingZeros().toPlainString()} kg · ${block.reps} 次 · 休息 ${block.restSeconds} 秒")
                    if (block.note.isNotBlank()) Text("备注：${block.note}")
                }
                Text("动作间休息：${exercise.exerciseRestSeconds} 秒")
            }
        }
    }
}
