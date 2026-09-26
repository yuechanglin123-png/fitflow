package com.fitflow.training.plan

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) =
        encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): BigDecimal =
        decoder.decodeString().toBigDecimal()
}

object DateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}

@Serializable
data class PlannedBlock(
    val id: String,
    @Serializable(with = DecimalSerializer::class) val weightKg: BigDecimal,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val note: String,
)

@Serializable
data class PlannedExercise(
    val id: String,
    val exerciseId: String?,
    val customName: String?,
    val blocks: List<PlannedBlock>,
    val exerciseRestSeconds: Int = 120,
)

@Serializable
data class WorkoutPlan(
    @Serializable(with = DateSerializer::class) val date: LocalDate,
    val exercises: List<PlannedExercise>,
)
