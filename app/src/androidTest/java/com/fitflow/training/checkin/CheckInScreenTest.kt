package com.fitflow.training.checkin

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.WorkoutPlan
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class CheckInScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun selectDate(date: LocalDate) {
        if (YearMonth.from(date) != YearMonth.now()) rule.onNodeWithText("上个月").performClick()
        rule.onNodeWithTag("calendar-day-$date").performClick()
    }

    @Test fun displaysEachCompletedSetAndExerciseRest() {
        val date = LocalDate.now().minusDays(1)
        val plan = WorkoutPlan(date, listOf(PlannedExercise(
            "checkin-action", null, "测试动作",
            listOf(PlannedBlock("checkin-set", BigDecimal("35"), 1, 8, 45, "稳定发力")),
            exerciseRestSeconds = 150,
        )))
        runBlocking {
            WorkoutRepository.open(ApplicationProvider.getApplicationContext<Context>())
                .saveCheckIn(CheckIn(date, emptyList(), plan))
        }
        rule.setContent { CheckInScreen(onBack = {}) }
        selectDate(date)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("第 1 组 · 35 kg · 8 次 · 休息 45 秒").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("动作间休息：150 秒").assertExists()
    }

    @Test fun pastDayCanOpenBackfillEditor() {
        val date = LocalDate.now().minusDays(2)
        rule.setContent { CheckInScreen(onBack = {}) }
        selectDate(date)
        rule.onNodeWithText("补卡这一天").performClick()
        rule.onNodeWithText("添加训练动作").assertExists()
        rule.onNodeWithText("有氧运动").assertExists()
    }

    @Test fun backfillSavesCustomActionSmallCardAndCompletedGroup() {
        val existing = runBlocking { WorkoutRepository.open(ApplicationProvider.getApplicationContext<Context>()).listCheckIns().map { it.date }.toSet() }
        val date = (4L..28L).map { LocalDate.now().minusDays(it) }.first { it !in existing }
        rule.setContent { CheckInScreen(onBack = {}) }
        selectDate(date)
        rule.onNodeWithText("补卡这一天").performClick()
        rule.onNodeWithText("徒手训练").performClick()
        rule.onNodeWithText("添加训练动作").performClick()
        rule.onNodeWithText("动作名称").performTextInput("引体向上")
        rule.onNodeWithText("添加").performClick()
        rule.onNodeWithText("添加训练小卡").performClick()
        rule.onNodeWithText("保存").performClick()
        rule.onNodeWithText("已完成到第几组（共 1 组）").performTextReplacement("1")
        rule.onNodeWithText("保存打卡").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("训练完成度：1 / 1 组（100%）").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("打卡类别：徒手训练").assertExists()
        rule.onNodeWithText("引体向上").assertExists()
    }

    @Test fun editingLegacyRecordPreservesUnknownProgress() {
        val date = LocalDate.now().minusDays(6)
        val plan = WorkoutPlan(date, listOf(PlannedExercise("old-action", null, "旧动作", listOf(
            PlannedBlock("old-set", BigDecimal("10"), 1, 8, 60, ""),
        ))))
        runBlocking { WorkoutRepository.open(ApplicationProvider.getApplicationContext<Context>()).saveCheckIn(CheckIn(date, listOf("old-library"), plan)) }
        rule.setContent { CheckInScreen(onBack = {}) }
        selectDate(date)
        rule.onNodeWithText("编辑打卡").performScrollTo().performClick()
        rule.onNodeWithText("徒手训练").performClick()
        rule.onNodeWithText("保存打卡").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("打卡类别：徒手训练").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("训练完成度：未记录").assertExists()
        val saved = runBlocking { WorkoutRepository.open(ApplicationProvider.getApplicationContext<Context>()).listCheckIns().first { it.date == date } }
        assertEquals(listOf("old-library"), saved.completedExerciseIds)
    }

    @Test fun deletingCompletedCardDoesNotCompleteNextCard() {
        val date = LocalDate.now().minusDays(7)
        val plan = WorkoutPlan(date, listOf(PlannedExercise("delete-action", null, "两组动作", listOf(
            PlannedBlock("completed-a", BigDecimal("10"), 1, 8, 60, ""),
            PlannedBlock("pending-b", BigDecimal("10"), 1, 8, 60, ""),
        ))))
        runBlocking { WorkoutRepository.open(ApplicationProvider.getApplicationContext<Context>()).saveCheckIn(CheckIn(date, emptyList(), plan, TrainingCategory.STRENGTH, mapOf("completed-a" to 1, "pending-b" to 0))) }
        rule.setContent { CheckInScreen(onBack = {}) }
        selectDate(date)
        rule.onNodeWithText("编辑打卡").performScrollTo().performClick()
        rule.onAllNodesWithText("删除小卡")[0].performClick()
        rule.onNodeWithText("保存打卡").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("训练完成度：0 / 1 组（0%）").fetchSemanticsNodes().isNotEmpty() }
    }
}
