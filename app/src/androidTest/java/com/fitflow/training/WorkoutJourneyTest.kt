package com.fitflow.training

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.plan.WorkoutPlan
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class WorkoutJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun catalogToManualCheckIn() {
        runBlocking {
            WorkoutRepository.open(rule.activity).savePlan(WorkoutPlan(LocalDate.now(), emptyList()))
        }
        rule.onNodeWithText("力量训练").performClick()
        rule.onNodeWithContentDescription("添加哑铃前蹲").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("重量（kg）").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("重量（kg）").performTextInput("20")
        rule.onNodeWithText("计划组数").performTextInput("1")
        rule.onNodeWithText("每组计划次数").performTextInput("8")
        rule.onNodeWithText("组间休息（秒）").performTextInput("0")
        rule.onNodeWithText("保存").performClick()
        rule.onNodeWithText("开始训练").performClick()
        Thread.sleep(1000)
        rule.onNode(
            hasText("完成本组") and hasAnyAncestor(hasTestTag("current-set-card")),
            useUnmergedTree = true,
        ).performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("查看训练总结").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("查看训练总结").performClick()
        rule.onNodeWithText("训练总结").assertExists()
        rule.onNodeWithText("完成训练并打卡").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("每日训练打卡").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("每日训练打卡").assertExists()
        rule.waitForIdle()
        Thread.sleep(1000)
    }
}
