package com.fitflow.training.plan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitflow.training.data.WorkoutDatabase
import com.fitflow.training.data.WorkoutRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanPresetFlowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun saveImportAppendAndOverwriteConfirmationAreAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        val repository = WorkoutRepository(db)
        val date = LocalDate.now()
        runBlocking {
            repository.savePlan(WorkoutPlan(date, listOf(
                exercise("first", "杠铃卧推"),
                exercise("second", "坐姿划船"),
            )))
        }
        rule.setContent {
            PlanScreen(onBack = {}, onStart = {}, onAddCatalog = {}, repository = repository)
        }
        rule.onNodeWithText("2 个动作", substring = true).assertExists()

        rule.onNodeWithText("保存为预设").performClick()
        rule.onNodeWithText("槽位 1").performClick()
        rule.onNodeWithText("预设名称").performTextReplacement("上下肢计划")
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil(5_000) { runBlocking { repository.loadPreset(1)?.name == "上下肢计划" } }

        rule.onNodeWithText("导入预设").performClick()
        rule.onNodeWithText("槽位 1").performClick()
        rule.onNodeWithText("覆盖今日计划").assertExists()
        rule.onNodeWithText("追加到末尾").performClick()
        rule.onNodeWithText("4 个动作", substring = true).assertExists()

        rule.onNodeWithText("保存为预设").performClick()
        rule.onNodeWithText("槽位 1").performClick()
        rule.onNodeWithText("覆盖槽位 1？").assertExists()
        rule.onNodeWithText("确认覆盖").assertExists()
        db.close()
    }

    private fun exercise(id: String, name: String) = PlannedExercise(
        id = id,
        exerciseId = null,
        customName = name,
        blocks = listOf(PlannedBlock("$id-block", BigDecimal("20"), 1, 10, 60, "")),
        exerciseRestSeconds = 120,
    )
}
