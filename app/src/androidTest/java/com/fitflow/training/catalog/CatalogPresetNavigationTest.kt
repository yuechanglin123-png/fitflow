package com.fitflow.training.catalog

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogPresetNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun catalogOffersPresetEntryBesideTodayPlan() {
        var opened = false
        rule.setContent {
            CatalogScreen(
                onAdd = {},
                onBack = {},
                onOpenPlan = {},
                onOpenPresets = { opened = true },
            )
        }

        rule.onNodeWithText("今日计划").assertExists()
        rule.onNodeWithText("训练预设").performClick()
        assertTrue(opened)
    }
}
