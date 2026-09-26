package com.fitflow.training.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

class CatalogRepositoryTest {
    private val sample = """
        [
          {"id":"barbell_squat","name":"Barbell Squat 深蹲","bodyParts":["LEGS"],"difficulty":"INTERMEDIATE",
           "caution":"控制下蹲速度","sourceUrl":"https://example.org/squat","checkedOn":"2026-09-22",
           "videoUrl":"https://example.org/squat-video","illustrationName":"squat","keywords":["深蹲","squat","自由重量"]},
          {"id":"plank","name":"平板支撑","bodyParts":["CORE"],"difficulty":"BEGINNER",
           "caution":"避免塌腰","sourceUrl":"https://example.org/plank","checkedOn":"2026-09-22",
           "videoUrl":"https://example.org/plank-video","illustrationName":"plank"}
        ]
    """.trimIndent()

    @Test fun searchMatchesNameAndFiltersByPartAndDifficulty() {
        val repo = CatalogRepository.fromJson(sample)
        assertEquals(
            listOf("barbell_squat"),
            repo.search("  squat ", BodyPart.LEGS, Difficulty.INTERMEDIATE).map { it.id }
        )
        assertEquals(
            listOf("plank"),
            repo.search("平板", BodyPart.CORE, Difficulty.BEGINNER).map { it.id }
        )
    }

    @Test fun rejectsMissingVideoLink() {
        val invalid = sample.replace("https://example.org/plank-video", "")
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRepository.fromJson(invalid)
        }
    }

    @Test fun rejectsDuplicateIds() {
        val invalid = sample.replace("\"id\":\"plank\"", "\"id\":\"barbell_squat\"")
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRepository.fromJson(invalid)
        }
    }

    @Test fun searchMatchesKeywordsIgnoringCaseAndKeepsFilters() {
        val repo = CatalogRepository.fromJson(sample)
        assertEquals(listOf("barbell_squat"), repo.search("  SQUAT  ").map { it.id })
        assertEquals(listOf("barbell_squat"), repo.search("自由重量", BodyPart.LEGS).map { it.id })
        assertTrue(repo.search("自由重量", BodyPart.CHEST).isEmpty())
        assertTrue(repo.search("squat", BodyPart.LEGS, Difficulty.BEGINNER).isEmpty())
    }

    @Test fun shippedCatalogContainsTwentyDistinctClassicMovements() {
        val repo = CatalogRepository.fromJson(File("src/main/assets/catalog.json").readText())
        assertEquals(20, repo.all().size)
        assertEquals(20, repo.all().map { it.id }.distinct().size)
        assertTrue(
            repo.all().map { it.id }.toSet().containsAll(setOf(
                "barbell_back_squat", "barbell_deadlift", "barbell_bench_press",
                "incline_barbell_bench_press", "decline_barbell_bench_press",
                "dumbbell_shoulder_press", "dumbbell_fly",
                "smith_machine_squat", "chest_press_machine", "shoulder_press_machine",
                "leg_press", "preacher_curl_machine", "lat_pulldown",
            )),
        )
        assertEquals(listOf("smith_machine_squat"), repo.search("smith").map { it.id })
    }
}
