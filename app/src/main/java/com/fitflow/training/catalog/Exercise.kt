package com.fitflow.training.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class BodyPart(val label: String) {
    CHEST("胸部"),
    BACK("背部"),
    SHOULDERS("肩部"),
    LEGS("腿部"),
    ARMS("手臂"),
    CORE("核心"),
}

@Serializable
enum class Difficulty(val label: String) {
    BEGINNER("初级"),
    INTERMEDIATE("进阶"),
    ADVANCED("高阶"),
}

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val bodyParts: Set<BodyPart>,
    val difficulty: Difficulty,
    val caution: String,
    val sourceUrl: String,
    val checkedOn: String,
    val videoUrl: String,
    val illustrationName: String? = null,
    val keywords: Set<String> = emptySet(),
)
