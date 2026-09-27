package com.fitflow.training.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fitflow.training.reminder.WorkoutForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AssistantPanel(runtime:AssistantRuntime) {
    val context=LocalContext.current
    val state by runtime.controller.state.collectAsState()
    var voice by remember { mutableStateOf(runtime.settings.voice) }
    var city by remember { mutableStateOf(runtime.settings.city) }
    var showCities by remember { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf(false) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        permissionError=!allowed
        if(allowed) WorkoutForegroundService.enableAssistant(context)
    }
    Card(Modifier.fillMaxWidth().testTag("assistant-panel")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("AI 训练助教",style=MaterialTheme.typography.titleMedium)
                Switch(checked=state.enabled,onCheckedChange={ enabled ->
                    if(!enabled) { runtime.controller.disable(); WorkoutForegroundService.disableAssistant(context) }
                    else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) WorkoutForegroundService.enableAssistant(context)
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                },modifier=Modifier.testTag("assistant-switch"))
            }
            Text(state.stage.label)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                VoiceChoice.entries.forEach { value ->
                    FilterChip(selected=voice==value,onClick={voice=value;runtime.settings.voice=value},label={Text(if(value==VoiceChoice.MALE) "男声" else "女声")})
                }
                TextButton(onClick={showCities=true}) { Text(city?.name?:"设置天气城市") }
            }
            Text("先说“你好教练”，唤醒后 5 秒内说指令，也可以在回应时说。语音中出现“完成”“暂停”“休息”或“跳过”时会执行相应训练操作；一句话包含多个操作词时会请你重说。未说指令会自动退出唤醒。也可以询问时间、天气和训练进度。",style=MaterialTheme.typography.bodySmall)
            Text("开启期间麦克风持续采集；可以在“我在，请说指令”时说指令。其他播报期间暂不处理指令，播报后恢复等待唤醒。关闭助教或结束训练后停止采集。识别与播报在本机运行；天气查询需要联网。",style=MaterialTheme.typography.bodySmall)
            if(state.heard.isNotBlank()) Text("听到：${state.heard}",style=MaterialTheme.typography.bodySmall)
            if(state.reply.isNotBlank()) Text("助教：${state.reply}",style=MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            if(permissionError) Text("麦克风权限未开启。请在系统设置中允许麦克风后再开启助教。",color=MaterialTheme.colorScheme.error)
        }
    }
    if(showCities) CityDialog(runtime.weather,onDismiss={showCities=false},onSelect={city=it;runtime.settings.city=it;showCities=false})
}

@Composable
private fun CityDialog(weather:WeatherRepository,onDismiss:()->Unit,onSelect:(WeatherCity)->Unit) {
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<WeatherCity>()) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest=onDismiss,title={Text("默认天气城市")},text={
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value=query,onValueChange={query=it.take(40)},label={Text("城市名称，如北京")},singleLine=true)
            Button(enabled=query.isNotBlank()&&!loading,onClick={scope.launch {
                loading=true; message="";matches=emptyList()
                try { matches=weather.searchCities(query); if(matches.isEmpty()) message="未找到城市，请换用完整名称" }
                catch(e:CancellationException) { throw e }
                catch(e:Exception) { message="查询失败，请检查网络后重试" }
                finally { loading=false }
            }}) { Text(if(loading) "查询中…" else "搜索城市") }
            matches.forEach { result -> TextButton(onClick={onSelect(result)}) { Text("${result.name} · ${result.region}") } }
            if(message.isNotBlank()) Text(message)
            Text("天气数据：Open-Meteo（CC BY 4.0）；城市数据：GeoNames。",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("关闭")}})
}
