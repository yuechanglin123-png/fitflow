package com.fitflow.training.ui

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetNavigationRestorationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun catalogBackStackReturnsToSamePresetAfterStateRestoration() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { AppNav() }

        rule.onNodeWithText("力量训练").performClick()
        rule.onNodeWithText("训练预设").performClick()
        rule.onNodeWithText("预设 3").performScrollTo().performClick()
        rule.onNodeWithText("从动作库添加").performClick()

        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("返回").performClick()
        rule.onNodeWithText("编辑预设 3").assertExists()
    }
}
