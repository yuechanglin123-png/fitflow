package com.fitflow.training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.session.Phase
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Ink = Color(0xFF172A29)
private val Mint = Color(0xFFDCF2DA)
private val Sand = Color(0xFFF8F5EC)

@Composable
fun HomeScreen(
    onStrengthClick: () -> Unit,
    onResumeClick: () -> Unit,
    onOpenFinish: () -> Unit,
    onCheckinsClick: () -> Unit,
    onTutorialClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { WorkoutRepository.open(context) }
    var canResume by remember { mutableStateOf(false) }
    var hasPendingSummary by remember { mutableStateOf(false) }
    var recentDates by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        val latest = repository.latestSession()
        val checked = repository.listCheckIns()
        canResume = latest?.phase?.let { it != Phase.FINISHED } ?: false
        hasPendingSummary = latest?.phase == Phase.FINISHED && checked.none { it.date == latest.plan.date }
        recentDates = checked.take(3).map { it.date.toString() }
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Sand).verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("健身助手", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Ink)
            TextButton(onClick = onSettingsClick) { Text("设置") }
        }
        Text("安排今天，完成今天。", style = MaterialTheme.typography.titleLarge, color = Ink)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("选择训练方式", style = MaterialTheme.typography.titleMedium, color = Ink)
            TextButton(onClick = onTutorialClick) { Text("新手教程") }
        }
        ModuleCard("力量训练", "规划动作 · 逐组计时 · 训练后拉伸", true, onStrengthClick)
        if (canResume) TextButton(onClick = onResumeClick) { Text("继续上次训练") }
        if (hasPendingSummary) TextButton(onClick = onOpenFinish) { Text("查看未打卡的训练总结") }
        ModuleCard("徒手健身", "即将开放", false, {})
        ModuleCard("有氧运动", "即将开放", false, {})
        if (recentDates.isNotEmpty()) Text("最近打卡：${recentDates.joinToString(" · ")}")
        TextButton(onClick = onCheckinsClick) { Text("查看每日打卡") }
    }
}

@Composable
private fun ModuleCard(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Ink else Mint,
            disabledContainerColor = Mint,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else Ink,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) Color(0xFFCCE2DD) else Color(0xFF53635F),
            )
        }
    }
}
