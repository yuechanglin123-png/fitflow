package com.fitflow.training.stretch

import com.fitflow.training.catalog.BodyPart
import com.fitflow.training.catalog.CatalogRepository
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.WorkoutPlan
import com.fitflow.training.session.SessionReducer
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StretchRecommenderTest {
    @Test fun onlyCompletedCatalogBodyPartsAreSuggested() {
        val catalog = CatalogRepository.fromJson("""[{"id":"bench","name":"卧推","bodyParts":["CHEST"],"difficulty":"BEGINNER","caution":"稳住肩膀","sourceUrl":"https://example.org","checkedOn":"2026-09-22","videoUrl":"https://example.org/video"}]""")
        val plan = WorkoutPlan(LocalDate.parse("2026-09-22"), listOf(PlannedExercise("a", "bench", null, listOf(PlannedBlock("b", BigDecimal.ZERO, 1, 8, 0, "")))))
        val session = SessionReducer.completeSet(SessionReducer.start(plan, "s"), 0)
        val stretches = listOf(Stretch("chest", "胸部拉伸", setOf(BodyPart.CHEST), "动作", "注意", 20, "https://example.org", "2026-09-22", "https://example.org/video", "chest"))
        assertEquals(listOf("chest"), StretchRecommender.forSession(session, catalog, stretches).map { it.id })
    }
}
