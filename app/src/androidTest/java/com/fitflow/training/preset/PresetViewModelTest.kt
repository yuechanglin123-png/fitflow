package com.fitflow.training.preset

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitflow.training.data.WorkoutDatabase
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.plan.PlannedBlock
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetViewModelTest {
    @Test fun editsCompletePresetAndPersistsEachGroupIndependently() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        try {
            val repository = WorkoutRepository(db)
            val model = PresetViewModel(repository, 3)
            model.load()
            model.rename(" 胸部训练 ")
            val parent = model.addExercise("bench")
            model.addBlock(parent, PlannedBlock("template", BigDecimal("40"), 3, 8, 90, "控制"))
            val second = model.preset.value.exercises.single().blocks[1]
            model.editBlock(parent, second.copy(weightKg = BigDecimal("45"), reps = 6))
            model.editExerciseRest(parent, 150)

            val reopened = PresetViewModel(repository, 3)
            reopened.load()
            val saved = reopened.preset.value
            assertEquals("胸部训练", saved.name)
            assertEquals(3, saved.exercises.single().blocks.size)
            assertEquals(listOf(BigDecimal("40"), BigDecimal("45"), BigDecimal("40")), saved.exercises.single().blocks.map { it.weightKg })
            assertEquals(150, saved.exercises.single().exerciseRestSeconds)
        } finally {
            db.close()
        }
    }
}
