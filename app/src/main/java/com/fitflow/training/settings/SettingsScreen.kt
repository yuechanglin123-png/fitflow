package com.fitflow.training.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitflow.training.reminder.RestReminder

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val caps = RestReminder(context).capability()
    Column(Modifier.fillMaxSize().background(Color(0xFFF8F5EC)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Text("训练计划、训练进度和打卡记录仅存储在本机。卸载应用或清除应用数据会删除这些记录。")
        Text("通知权限：${if (caps.notifications) "已开启" else "未开启"}；精确闹钟：${if (caps.exactAlarms) "可用" else "不可用"}。权限未开启时，应用内倒计时仍以保存的截止时间计算，系统提醒可能延迟或不显示。")
        if (Build.VERSION.SDK_INT >= 31 && !caps.exactAlarms) TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }) { Text("开启精确提醒") }
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("打开系统通知设置") }
        Text("动作说明参考 NASM、ACE 与 NHS 的公开资料，均为本应用重新撰写；点击动作卡片可查看对应资料。视频在浏览器中打开。")
        Text("徒手健身和有氧运动模块将在后续版本开放。")
    }
}
