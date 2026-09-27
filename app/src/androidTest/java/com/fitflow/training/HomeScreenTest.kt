package com.fitflow.training

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun homeOnlyShowsStrengthTrainingModule() {
        composeRule.onNodeWithText("力量训练").assertExists()
        composeRule.onAllNodesWithText("徒手健身").assertCountEquals(0)
        composeRule.onAllNodesWithText("有氧运动").assertCountEquals(0)
    }

    @Test fun tutorialOpensBundledPdfAndReturnsHome() {
        composeRule.onNodeWithText("新手教程").performClick()
        composeRule.onNodeWithText("第 1 / 2 页").assertExists()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("从动作库建立今日计划", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("从动作库建立今日计划", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("7 个预设槽位", substring = true).assertExists()
        composeRule.onNodeWithText("放大").performClick()
        composeRule.onNodeWithText("已放大，可滚动查看").assertExists()
        composeRule.onNodeWithText("下一页").performClick()
        composeRule.onNodeWithText("第 2 / 2 页").assertExists()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("训练、助教和每日打卡", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("训练、助教和每日打卡", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("训练记录保存在本机", substring = true).assertExists()
        composeRule.onNodeWithText("返回首页").performClick()
        composeRule.onNodeWithText("力量训练").assertExists()
    }

    @Test fun homeCanScrollToCheckinsOnCompactScreens() {
        composeRule.onNodeWithText("查看每日打卡").performScrollTo().assertExists()
    }
}
