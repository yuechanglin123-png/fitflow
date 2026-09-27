package com.fitflow.training.session

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.WorkoutPlan
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingSetLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun trainingPageShowsCompactSetCardsInExerciseOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plan = WorkoutPlan(LocalDate.parse("2030-04-01"), listOf(
            PlannedExercise("a", null, "动作甲", listOf(
                PlannedBlock("a1", BigDecimal("20"), 1, 8, 30, ""),
                PlannedBlock("a2", BigDecimal("25"), 1, 6, 45, ""),
            ), exerciseRestSeconds = 120),
            PlannedExercise("b", null, "动作乙", listOf(
                PlannedBlock("b1", BigDecimal("30"), 1, 8, 35, ""),
                PlannedBlock("b2", BigDecimal("35"), 1, 6, 50, ""),
            )),
        ))
        runBlocking { WorkoutRepository.open(context).saveSession(SessionReducer.start(plan, "set-layout-ui")) }
        rule.setContent { SessionScreen(onBack = {}, onFinish = {}) }
        rule.onNodeWithTag("pause-training-button").assertExists()
        rule.onNodeWithTag("finish-training-button").assertExists()
        rule.onNodeWithText("结束训练").assertExists()
        rule.onNodeWithContentDescription("暂停训练").assertExists()
        rule.onNodeWithTag("current-set-card").assertExists()
        rule.onNodeWithText("下一动作：动作乙").performScrollTo().assertExists()
        val tags = rule.onAllNodes(
            hasTestTag("session-set-card-a1") or hasTestTag("session-set-card-a2") or
                hasTestTag("session-set-card-b1") or hasTestTag("session-set-card-b2")
        )
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertEquals(listOf("a1", "a2", "b1", "b2").map { "session-set-card-$it" }, tags)
        listOf("a1", "a2", "b1", "b2").forEach { id ->
            val card = rule.onNodeWithTag("session-set-card-$id")
            card.performScrollTo()
            val bounds = card.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.height < bounds.width)
        }
        rule.onAllNodesWithText("本组后休息 30 秒")[0].performScrollTo().assertExists()
        rule.onNodeWithText("动作结束后休息 120 秒").performScrollTo().assertExists()
    }

    @Test fun finishTrainingButtonFinishesSessionAndOpensSummary() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = WorkoutRepository.open(context)
        val plan = WorkoutPlan(LocalDate.parse("2030-04-02"), listOf(
            PlannedExercise("finish-action", null, "提前结束测试", listOf(
                PlannedBlock("finish-one", BigDecimal.ZERO, 1, 8, 60, ""),
                PlannedBlock("finish-two", BigDecimal.ZERO, 1, 8, 60, ""),
            )),
        ))
        runBlocking { repository.saveSession(SessionReducer.start(plan, "finish-training-ui")) }
        val summaryOpened = AtomicBoolean(false)
        rule.setContent { SessionScreen(onBack = {}, onFinish = { summaryOpened.set(true) }) }

        rule.onNodeWithTag("finish-training-button").performClick()

        rule.waitUntil(10_000) { summaryOpened.get() }
        val saved = runBlocking { repository.loadSession("finish-training-ui")!! }
        assertEquals(Phase.FINISHED, saved.phase)
        assertEquals(0, saved.completedBlockSets.values.sum())
    }
}
