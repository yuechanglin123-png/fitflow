package com.fitflow.training.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.preset.TrainingPreset
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetRepositoryTest {
    private fun preset(slot: Int, name: String) = TrainingPreset(slot, name, listOf(
        PlannedExercise("e-$slot", "bench", null, listOf(
            PlannedBlock("b-$slot", BigDecimal("20"), 1, 8, 60, ""),
        )),
    ))

    @Test fun repositoryAlwaysReturnsSevenOrderedSlotsAndPersistsValues() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        try {
            val repository = WorkoutRepository(db)
            repository.savePreset(preset(2, "腿部"))
            repository.savePreset(preset(7, "全身"))

            val slots = repository.listPresetSlots()
            assertEquals((1..7).toList(), slots.map { it.slot })
            assertEquals(listOf(2, 7), slots.filter { it.preset != null }.map { it.slot })
            assertEquals("腿部", repository.loadPreset(2)?.name)

            repository.clearPreset(2)
            assertNull(repository.loadPreset(2))
            assertEquals("全身", repository.loadPreset(7)?.name)
        } finally {
            db.close()
        }
    }
}
