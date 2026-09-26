package com.fitflow.training

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun homeShowsThreeTrainingModules() {
        composeRule.onNodeWithText("力量训练").assertExists()
        composeRule.onNodeWithText("徒手健身").assertExists()
        composeRule.onNodeWithText("有氧运动").assertExists()
    }
}
