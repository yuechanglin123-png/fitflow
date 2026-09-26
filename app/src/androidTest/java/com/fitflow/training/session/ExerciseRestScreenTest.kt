package com.fitflow.training.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.WorkoutPlan
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseRestScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsExerciseRestInsteadOfSetRest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plan = WorkoutPlan(LocalDate.parse("2030-03-01"), listOf(
            PlannedExercise("a", null, "动作甲", listOf(PlannedBlock("a1", BigDecimal.ZERO, 1, 8, 10, "")), 120),
            PlannedExercise("b", null, "动作乙", listOf(PlannedBlock("b1", BigDecimal.ZERO, 1, 8, 10, ""))),
        ))
        val session = SessionReducer.completeSet(SessionReducer.start(plan, "exercise-rest-ui"), System.currentTimeMillis())
        runBlocking { WorkoutRepository.open(context).saveSession(session) }
        rule.setContent { SessionScreen(onBack = {}, onFinish = {}) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("动作间休息", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("动作间休息", substring = true)[0].assertExists()
        rule.onNodeWithText("本组后休息 10 秒", substring = true).assertDoesNotExist()
        rule.onAllNodesWithText("动作结束后休息 120 秒", substring = true)[0].assertExists()
    }
}
