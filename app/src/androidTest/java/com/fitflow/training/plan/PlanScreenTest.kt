package com.fitflow.training.plan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.fitflow.training.data.WorkoutRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun plannedBlockCanBeSavedAndReopened() {
        val longNote = "保持核心稳定，肩胛骨收紧，动作全程控制速度。".repeat(8)
        runBlocking {
            WorkoutRepository.open(ApplicationProvider.getApplicationContext<Context>())
                .savePlan(WorkoutPlan(LocalDate.now(), emptyList()))
        }
        composeRule.setContent { PlanScreen(onBack = {}, onStart = {}, onAddCatalog = {}) }
        composeRule.onNodeWithText("新增自定义动作").performClick()
        composeRule.onNodeWithText("动作名称").performTextInput("测试动作")
        composeRule.onNodeWithText("添加").performClick()
        composeRule.onAllNodesWithText("+ 添加小组")[0].performClick()
        composeRule.onNodeWithText("重量（kg）").performTextInput("40")
        composeRule.onNodeWithText("计划组数").performTextInput("3")
        composeRule.onNodeWithText("每组计划次数").performTextInput("8")
        composeRule.onNodeWithText("组间休息（秒）").performTextInput("90")
        composeRule.onNodeWithText("备注").performTextInput(longNote)
        composeRule.onNodeWithText("保存").performClick()
        composeRule.onNodeWithText("第 1 组").assertExists()
        composeRule.onNodeWithText("第 2 组").assertExists()
        composeRule.onNodeWithText("第 3 组").assertExists()
        assertEquals(3, composeRule.onAllNodesWithText("40 kg · 8 次 · 休息 90 秒").fetchSemanticsNodes().size)
        assertEquals(3, composeRule.onAllNodesWithText("备注：$longNote").fetchSemanticsNodes().size)
        composeRule.onAllNodesWithText("备注：$longNote")[0].performClick()
        composeRule.onNodeWithText("完整备注").assertExists()
        composeRule.onNodeWithText(longNote).assertExists()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onAllNodesWithText("修改")[1].performScrollTo().performClick()
        composeRule.onNodeWithText("重量（kg）").performTextReplacement("45")
        composeRule.onNodeWithText("每组计划次数").performTextReplacement("6")
        composeRule.onNodeWithText("本组后休息（秒）").performTextReplacement("120")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.onNodeWithText("45 kg · 6 次 · 休息 120 秒").assertExists()
        assertEquals(2, composeRule.onAllNodesWithText("40 kg · 8 次 · 休息 90 秒").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("动作间休息：120 秒 · 修改").performClick()
        composeRule.onNodeWithText("动作间休息（秒）").performTextReplacement("150")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.onNodeWithText("动作间休息：150 秒 · 修改").assertExists()
    }
}
