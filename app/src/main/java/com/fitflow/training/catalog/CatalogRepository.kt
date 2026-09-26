package com.fitflow.training.catalog

import android.content.Context
import java.time.LocalDate
import kotlinx.serialization.json.Json

class CatalogRepository private constructor(private val exercises: List<Exercise>) {
    fun all(): List<Exercise> = exercises

    fun search(
        query: String,
        bodyPart: BodyPart? = null,
        difficulty: Difficulty? = null,
    ): List<Exercise> {
        val normalized = query.trim().lowercase()
        return exercises.filter { exercise ->
            (normalized.isEmpty() || exercise.name.lowercase().contains(normalized) ||
                exercise.keywords.any { it.lowercase().contains(normalized) }) &&
                (bodyPart == null || bodyPart in exercise.bodyParts) &&
                (difficulty == null || exercise.difficulty == difficulty)
        }
    }

    companion object {
        fun load(context: Context): CatalogRepository =
            fromJson(context.assets.open("catalog.json").bufferedReader().use { it.readText() })

        fun fromJson(json: String): CatalogRepository {
            val items = Json.decodeFromString<List<Exercise>>(json)
            require(items.map { it.id }.distinct().size == items.size) { "Duplicate exercise ID" }
            items.forEach { item ->
                require(item.id.isNotBlank() && item.name.isNotBlank()) { "Missing exercise identity" }
                require(item.bodyParts.isNotEmpty() && item.caution.isNotBlank()) { "Missing exercise guidance" }
                require(item.sourceUrl.startsWith("https://")) { "Invalid source URL" }
                require(item.videoUrl.startsWith("https://")) { "Invalid video URL" }
                LocalDate.parse(item.checkedOn)
            }
            return CatalogRepository(items)
        }
    }
}
