package com.fitflow.training.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.LegacyPlanNormalizer
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.WorkoutPlan
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {
    @Test fun planSurvivesDatabaseReopenWithBlockOrderAndNotes() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "plan-${UUID.randomUUID()}.db"
            val plan = WorkoutPlan(
                LocalDate.of(2026, 9, 22),
                listOf(
                    PlannedExercise("e1", "dumbbell_front_squat", null, listOf(
                        PlannedBlock("b1", BigDecimal("10.5"), 3, 8, 90, "第一档"),
                        PlannedBlock("b2", BigDecimal("15"), 2, 6, 120, "加重")
                    )),
                    PlannedExercise("e2", null, "自定义动作", listOf(
                        PlannedBlock("b3", BigDecimal.ZERO, 1, 12, 60, "徒手")
                    ))
                )
            )
            try {
                val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).build()
                try {
                    WorkoutRepository(db).savePlan(plan)
                } finally {
                    db.close()
                }
                val reopened = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).build()
                try {
                    val repository = WorkoutRepository(reopened)
                    assertEquals(LegacyPlanNormalizer.normalize(plan), repository.loadPlan(plan.date))
                    assertEquals(LegacyPlanNormalizer.normalize(plan), repository.loadPlan(plan.date))
                } finally {
                    reopened.close()
                }
            } finally {
                context.deleteDatabase(name)
            }
        }
    }
}
