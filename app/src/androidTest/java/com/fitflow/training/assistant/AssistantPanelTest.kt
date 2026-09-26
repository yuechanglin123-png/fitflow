package com.fitflow.training.assistant

import android.Manifest
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.fitflow.training.data.WorkoutRuntime
import com.fitflow.training.plan.*
import com.fitflow.training.reminder.WorkoutForegroundService
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AssistantPanelTest {
    @get:Rule val rule=createComposeRule()
    @Test fun nativeAssistantCanInitializeAndBeDisabledFromTrainingPanel() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.RECORD_AUDIO)
        if(android.os.Build.VERSION.SDK_INT>=33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.POST_NOTIFICATIONS)
        val runtime=AssistantRuntime.get(context)
        runBlocking { WorkoutRuntime.get(context).session.start(WorkoutPlan(LocalDate.now(),listOf(
            PlannedExercise("assistant-ui",null,"测试动作",listOf(PlannedBlock("assistant-ui-one",BigDecimal.ZERO,1,8,60,"")))))) }
        rule.setContent { MaterialTheme { AssistantPanel(runtime) } }
        rule.onNodeWithText("男声").performClick()
        assertEquals(VoiceChoice.MALE,runtime.settings.voice)
        rule.onNodeWithText("女声").performClick()
        rule.onNodeWithTag("assistant-switch").performClick()
        rule.waitUntil(120_000) { runtime.controller.state.value.stage in listOf(AssistantStage.WAITING,AssistantStage.ERROR) }
        assertEquals(runtime.controller.state.value.error,AssistantStage.WAITING,runtime.controller.state.value.stage)
        val notifications=context.getSystemService(android.app.NotificationManager::class.java).activeNotifications
        assertTrue(notifications.any { it.notification.actions?.any { action->action.title=="关闭助教" }==true })
        rule.onNodeWithTag("assistant-switch").performClick()
        rule.waitUntil(5000) { !runtime.controller.state.value.enabled }
        rule.onNodeWithText("已关闭").assertExists()
        rule.runOnIdle { WorkoutForegroundService.stop(context) }
    }
}
