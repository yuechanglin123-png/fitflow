package com.fitflow.training.catalog

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun legDirectoryCanAddFrontSquat() {
        var addedId: String? = null
        composeRule.setContent {
            CatalogScreen(onAdd = { addedId = it.id }, onBack = {})
        }
        composeRule.onNodeWithText("腿部").performClick()
        composeRule.onNodeWithText("哑铃前蹲").assertExists()
        composeRule.onNodeWithContentDescription("添加哑铃前蹲").performClick()
        composeRule.runOnIdle { assertEquals("dumbbell_front_squat", addedId) }
    }

    @Test fun keywordSearchFindsMachineSquat() {
        composeRule.setContent { CatalogScreen(onAdd = {}, onBack = {}) }
        composeRule.onNodeWithText("搜索动作").performTextInput("smith")
        composeRule.onNodeWithText("找到 1 个动作").assertExists()
        composeRule.onNodeWithText("史密斯机深蹲").assertExists()
    }
}
