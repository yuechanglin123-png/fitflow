package com.fitflow.training.plan

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitflow.training.data.WorkoutDatabase
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.preset.ImportMode
import com.fitflow.training.preset.TrainingPreset
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanPresetViewModelTest {
    @Test fun savesCurrentPlanAndImportsByReplaceOrAppendWithFreshIds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        val repository = WorkoutRepository(db)
        val date = LocalDate.of(2026, 9, 23)
        val model = PlanViewModel(repository, date)
        val sourceBlock = PlannedBlock("block", BigDecimal("45"), 1, 8, 90, "控制离心")
        repository.savePlan(WorkoutPlan(date, listOf(
            PlannedExercise("exercise", null, "自定义深蹲", listOf(sourceBlock), 150),
        )))
        model.load()

        model.saveAsPreset(4, "腿部日")
        val saved = repository.loadPreset(4)
        assertNotNull(saved)
        assertEquals("腿部日", saved?.name)
        assertNotEquals("exercise", saved?.exercises?.single()?.id)
        assertNotEquals("block", saved?.exercises?.single()?.blocks?.single()?.id)

        repository.savePreset(TrainingPreset(2, "推日", listOf(
            PlannedExercise("preset-exercise", "dumbbell_bench_press", null, listOf(
                PlannedBlock("preset-block", BigDecimal("20"), 1, 10, 60, ""),
            ), 120),
        )))
        model.importPreset(2, ImportMode.APPEND)
        assertEquals(2, model.plan.value.exercises.size)
        assertNotEquals("preset-exercise", model.plan.value.exercises.last().id)
        assertNotEquals("preset-block", model.plan.value.exercises.last().blocks.single().id)

        model.importPreset(2, ImportMode.REPLACE)
        assertEquals(listOf("dumbbell_bench_press"), model.plan.value.exercises.map { it.exerciseId })
        assertEquals(date, model.plan.value.date)

        coroutineScope {
            listOf(
                async { model.importPreset(2, ImportMode.APPEND) },
                async { model.importPreset(2, ImportMode.APPEND) },
            ).awaitAll()
        }
        assertEquals(3, model.plan.value.exercises.size)
        assertEquals(3, model.plan.value.exercises.map { it.id }.distinct().size)
        db.close()
    }
}
