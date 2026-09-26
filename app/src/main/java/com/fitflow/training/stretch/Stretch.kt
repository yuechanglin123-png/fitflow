package com.fitflow.training.stretch

import android.content.Context
import com.fitflow.training.catalog.BodyPart
import com.fitflow.training.catalog.CatalogRepository
import com.fitflow.training.session.SessionSnapshot
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Stretch(
    val id: String,
    val name: String,
    val bodyParts: Set<BodyPart>,
    val steps: String,
    val caution: String,
    val holdSeconds: Int,
    val sourceUrl: String,
    val checkedOn: String,
    val videoUrl: String,
    val illustrationName: String,
)

object StretchRecommender {
    fun load(context: Context): List<Stretch> {
        val items = Json.decodeFromString<List<Stretch>>(context.assets.open("stretches.json").bufferedReader().use { it.readText() })
        require(items.map { it.id }.distinct().size == items.size)
        items.forEach { require(it.id.isNotBlank() && it.name.isNotBlank() && it.steps.isNotBlank() && it.caution.isNotBlank() && it.holdSeconds > 0 && it.sourceUrl.startsWith("https://") && it.videoUrl.startsWith("https://")); LocalDate.parse(it.checkedOn) }
        return items
    }

    fun forSession(snapshot: SessionSnapshot, catalog: CatalogRepository, stretches: List<Stretch>): List<Stretch> {
        val completedParts = snapshot.plan.exercises.filter { exercise ->
            exercise.blocks.any { (snapshot.completedBlockSets[it.id] ?: 0) > 0 }
        }.flatMap { exercise -> catalog.all().firstOrNull { it.id == exercise.exerciseId }?.bodyParts.orEmpty() }.toSet()
        return stretches.filter { stretch -> stretch.bodyParts.any { it in completedParts } }.distinctBy { it.id }
    }
}
