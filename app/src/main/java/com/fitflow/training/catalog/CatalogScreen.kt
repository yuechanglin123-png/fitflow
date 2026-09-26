package com.fitflow.training.catalog

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CatalogScreen(
    onAdd: (Exercise) -> Unit,
    onBack: () -> Unit,
    onOpenPlan: () -> Unit = {},
    onOpenPresets: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context) { CatalogRepository.load(context) }
    var query by remember { mutableStateOf("") }
    var bodyPart by remember { mutableStateOf<BodyPart?>(null) }
    var difficulty by remember { mutableStateOf<Difficulty?>(null) }
    var selected by remember { mutableStateOf<Exercise?>(null) }
    val visible = repository.search(query, bodyPart, difficulty)

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8F5EC))
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("返回") }
            Column {
                Text("力量动作库", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = onOpenPlan) { Text("今日计划") }
                    TextButton(onClick = onOpenPresets) { Text("训练预设") }
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索动作") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("训练部位", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = bodyPart == null, onClick = { bodyPart = null }, label = { Text("全部") })
            BodyPart.entries.forEach { part ->
                FilterChip(
                    selected = bodyPart == part,
                    onClick = { bodyPart = part },
                    label = { Text(part.label) },
                )
            }
        }
        Text("难度", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = difficulty == null, onClick = { difficulty = null }, label = { Text("全部") })
            Difficulty.entries.forEach { level ->
                FilterChip(
                    selected = difficulty == level,
                    onClick = { difficulty = level },
                    label = { Text(level.label) },
                )
            }
        }
        Text("找到 ${visible.size} 个动作", style = MaterialTheme.typography.bodyMedium)
        visible.forEach { exercise ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { selected = exercise },
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            exercise.bodyParts.joinToString(" · ") { it.label } + " · " + exercise.difficulty.label,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(
                        onClick = { onAdd(exercise) },
                        modifier = Modifier.semantics { contentDescription = "添加${exercise.name}" },
                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    selected?.let { exercise ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(exercise.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseDiagram(exercise.illustrationName)
                    Text("动作注意事项", fontWeight = FontWeight.Bold)
                    Text(exercise.caution)
                    Text("资料核对：${exercise.checkedOn}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(exercise.videoUrl)))
                    }) { Text("观看讲解视频 ↗") }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(exercise.sourceUrl)))
                    }) { Text("查看资料来源 ↗") }
                }
            },
            confirmButton = {
                Button(onClick = { onAdd(exercise); selected = null }) { Text("添加到今日计划") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("关闭") } },
        )
    }
}
