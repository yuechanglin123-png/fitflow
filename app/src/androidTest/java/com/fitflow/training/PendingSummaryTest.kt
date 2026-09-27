package com.fitflow.training

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.WorkoutPlan
import com.fitflow.training.session.Phase
import com.fitflow.training.session.SessionSnapshot
import com.fitflow.training.ui.HomeScreen
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class PendingSummaryTest {
    @get:Rule val rule = createComposeRule()

    @Test fun finishedButUncheckedSessionRemainsReachable(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plan = WorkoutPlan(LocalDate.parse("2035-01-01"), listOf(
            PlannedExercise("pending-parent", null, "测试", listOf(PlannedBlock("pending-block", BigDecimal.ZERO, 1, 8, 0, "")))
        ))
        WorkoutRepository.open(context).saveSession(SessionSnapshot(UUID.randomUUID().toString(), plan, mapOf("pending-block" to 1), Phase.FINISHED, null, null))
        rule.setContent { HomeScreen(onStrengthClick = {}, onResumeClick = {}, onOpenFinish = {}, onCheckinsClick = {}, onTutorialClick = {}, onSettingsClick = {}) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("查看未打卡的训练总结").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("查看未打卡的训练总结").assertExists()
        Unit
    }
}
