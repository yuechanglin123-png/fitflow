package com.fitflow.training.stretch

import android.content.Intent

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.fitflow.training.catalog.CatalogRepository
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.session.Phase
import com.fitflow.training.session.SessionSnapshot
import kotlinx.coroutines.launch

@Composable
fun FinishScreen(onCheckIn: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { WorkoutRepository.open(context) }
    val catalog = remember { CatalogRepository.load(context) }
    val stretches = remember { StretchRecommender.load(context) }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<SessionSnapshot?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { session = repository.latestSession() }
    val current = session
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF8F5EC)).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("训练总结", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (current?.phase != Phase.FINISHED) Text("训练尚未完成") else {
            Text("已完成 ${current.completedBlockSets.values.sum()} 组")
            Text("根据今天完成的部位推荐放松动作", style = MaterialTheme.typography.titleMedium)
            val recommended = StretchRecommender.forSession(current, catalog, stretches)
            if (recommended.isEmpty()) Text("没有可匹配的拉伸动作，可自行选择舒缓放松。")
            recommended.forEach { stretch ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(stretch.name, style = MaterialTheme.typography.titleLarge)
                        StretchDiagram(stretch.illustrationName)
                        Text("每侧约 ${stretch.holdSeconds} 秒")
                        Text(stretch.steps)
                        Text("注意：${stretch.caution}")
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(stretch.videoUrl))) }) { Text("查看对应动作视频 ↗") }
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(stretch.sourceUrl))) }) { Text("查看动作资料 ↗") }
                    }
                }
            }
            Button(onClick = {
                saving = true
                scope.launch {
                    try { repository.checkIn(current); saving = false; onCheckIn() }
                    catch (error: Exception) { saveError = error.message ?: "保存失败"; saving = false }
                }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("完成训练并打卡") }
            saveError?.let { Text("打卡失败：$it", color = MaterialTheme.colorScheme.error) }
        }
    }
}
