package com.fitflow.training.preset

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitflow.training.data.WorkoutDatabase
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetScreensTest {
    @get:Rule val rule = createComposeRule()

    @Test fun listAlwaysShowsSevenSlotsAndPresetSummary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        val repository = WorkoutRepository(db)
        runBlocking {
            repository.savePreset(TrainingPreset(2, "腿部训练", listOf(
                PlannedExercise("e", "squat", null, listOf(
                    PlannedBlock("b1", BigDecimal("30"), 1, 8, 60, ""),
                    PlannedBlock("b2", BigDecimal("35"), 1, 6, 90, ""),
                )),
            )))
        }
        rule.setContent { PresetListScreen(onBack = {}, onEditSlot = {}, repository = repository) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("尚未设置").fetchSemanticsNodes().size == 6 }
        (1..7).forEach { rule.onNodeWithText("预设 $it").assertExists() }
        rule.onAllNodesWithText("尚未设置").assertCountEquals(6)
        rule.onNodeWithText("腿部训练").assertExists()
        rule.onNodeWithText("1 个动作 · 2 组").assertExists()
        db.close()
    }

    @Test fun editorShowsImportValidationAndCatalogAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        val repository = WorkoutRepository(db)
        rule.setContent { PresetEditScreen(slot = 1, selectedExerciseId = null, onBack = {}, onAddCatalog = {}, repository = repository) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("预设内容尚不能导入").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("从动作库添加").performClick()
        db.close()
    }

    @Test fun returningFromCatalogKeepsExistingPresetBeforeAddingExercise() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        val repository = WorkoutRepository(db)
        runBlocking {
            repository.savePreset(TrainingPreset(3, "原有计划", listOf(
                PlannedExercise("old", null, "原有动作", listOf(
                    PlannedBlock("old-block", BigDecimal("10"), 1, 12, 45, ""),
                )),
            )))
        }
        rule.setContent {
            PresetEditScreen(
                slot = 3,
                selectedExerciseId = "dumbbell_front_squat",
                onBack = {},
                onAddCatalog = {},
                repository = repository,
            )
        }
        rule.waitUntil(5_000) {
            runBlocking { repository.loadPreset(3)?.exercises?.size == 2 }
        }
        val saved = runBlocking { repository.loadPreset(3) }
        assertEquals("原有计划", saved?.name)
        assertEquals(listOf("原有动作", null), saved?.exercises?.map { it.customName })
        db.close()
    }
}
