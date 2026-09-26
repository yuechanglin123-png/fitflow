package com.fitflow.training.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

private val Figure = Color(0xFF244842)
private val Equipment = Color(0xFFE97955)

@Composable
fun ExerciseDiagram(kind: String?) {
    Canvas(
        modifier = Modifier.fillMaxWidth().height(150.dp)
            .background(Color(0xFFE8F0E7), RoundedCornerShape(12.dp)),
    ) {
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        fun limb(x1: Float, y1: Float, x2: Float, y2: Float, equipment: Boolean = false) {
            drawLine(
                if (equipment) Equipment else Figure,
                point(x1, y1),
                point(x2, y2),
                strokeWidth = if (equipment) 8.dp.toPx() else 6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        fun head(x: Float, y: Float) {
            drawCircle(Figure, radius = 11.dp.toPx(), center = point(x, y))
        }
        when (kind) {
            "front_squat" -> {
                head(.50f, .18f); limb(.50f,.27f,.50f,.56f)
                limb(.50f,.36f,.34f,.43f); limb(.34f,.43f,.32f,.29f)
                limb(.50f,.36f,.66f,.43f); limb(.66f,.43f,.68f,.29f)
                limb(.50f,.56f,.38f,.70f); limb(.38f,.70f,.31f,.85f)
                limb(.50f,.56f,.62f,.70f); limb(.62f,.70f,.69f,.85f)
                limb(.27f,.29f,.37f,.29f,true); limb(.63f,.29f,.73f,.29f,true)
            }
            "barbell_squat" -> {
                head(.50f,.18f); limb(.50f,.28f,.50f,.56f)
                limb(.50f,.39f,.34f,.32f); limb(.50f,.39f,.66f,.32f)
                limb(.50f,.56f,.37f,.70f); limb(.37f,.70f,.30f,.86f)
                limb(.50f,.56f,.63f,.70f); limb(.63f,.70f,.70f,.86f)
                limb(.26f,.31f,.74f,.31f,true)
            }
            "bench_press" -> {
                limb(.18f,.78f,.78f,.78f,true)
                head(.28f,.60f); limb(.37f,.63f,.65f,.63f)
                limb(.55f,.63f,.61f,.78f); limb(.65f,.63f,.77f,.75f)
                limb(.43f,.62f,.44f,.37f); limb(.60f,.62f,.59f,.37f)
                limb(.33f,.35f,.69f,.35f,true)
            }
            "chest_fly" -> {
                limb(.18f,.78f,.80f,.78f,true)
                head(.29f,.59f); limb(.38f,.63f,.67f,.63f)
                limb(.54f,.63f,.59f,.78f); limb(.66f,.63f,.77f,.75f)
                limb(.43f,.62f,.33f,.39f); limb(.61f,.62f,.72f,.39f)
                limb(.28f,.36f,.35f,.36f,true); limb(.70f,.36f,.77f,.36f,true)
            }
            "machine_press" -> {
                head(.37f,.27f); limb(.39f,.37f,.41f,.66f)
                limb(.41f,.66f,.61f,.68f); limb(.61f,.68f,.67f,.83f)
                limb(.43f,.48f,.58f,.48f); limb(.58f,.48f,.73f,.44f)
                limb(.31f,.81f,.54f,.81f,true)
                limb(.30f,.35f,.30f,.81f,true); limb(.76f,.18f,.76f,.82f,true)
                limb(.73f,.42f,.76f,.42f,true)
            }
            "shoulder_press" -> {
                head(.50f,.24f); limb(.50f,.34f,.50f,.67f)
                limb(.50f,.67f,.38f,.87f); limb(.50f,.67f,.62f,.87f)
                limb(.50f,.42f,.35f,.38f); limb(.35f,.38f,.35f,.18f)
                limb(.50f,.42f,.65f,.38f); limb(.65f,.38f,.65f,.18f)
                limb(.29f,.16f,.40f,.16f,true); limb(.60f,.16f,.71f,.16f,true)
            }
            "leg_press" -> {
                head(.34f,.29f); limb(.38f,.37f,.50f,.59f)
                limb(.50f,.59f,.62f,.51f); limb(.62f,.51f,.74f,.43f)
                limb(.50f,.59f,.61f,.70f); limb(.61f,.70f,.73f,.62f)
                limb(.28f,.80f,.52f,.80f,true)
                limb(.28f,.41f,.28f,.80f,true)
                limb(.77f,.22f,.77f,.78f,true)
            }
            "lat_pulldown" -> {
                head(.50f,.31f); limb(.50f,.41f,.50f,.67f)
                limb(.50f,.67f,.38f,.85f); limb(.50f,.67f,.62f,.85f)
                limb(.50f,.42f,.34f,.35f); limb(.34f,.35f,.30f,.20f)
                limb(.50f,.42f,.66f,.35f); limb(.66f,.35f,.70f,.20f)
                limb(.25f,.19f,.75f,.19f,true)
                limb(.50f,.12f,.50f,.19f,true)
                limb(.32f,.74f,.68f,.74f,true)
            }
            "seated_row" -> {
                head(.38f,.28f); limb(.40f,.36f,.42f,.64f)
                limb(.42f,.64f,.60f,.67f); limb(.60f,.67f,.66f,.82f)
                limb(.43f,.49f,.58f,.55f); limb(.58f,.55f,.70f,.48f)
                limb(.72f,.20f,.72f,.79f,true); limb(.70f,.48f,.72f,.48f,true)
                limb(.32f,.82f,.54f,.82f,true)
            }
            "face_pull" -> {
                head(.47f,.22f); limb(.47f,.31f,.47f,.62f)
                limb(.47f,.62f,.35f,.88f); limb(.47f,.62f,.59f,.88f)
                limb(.47f,.40f,.33f,.36f); limb(.33f,.36f,.42f,.26f)
                limb(.47f,.40f,.62f,.36f); limb(.62f,.36f,.52f,.26f)
                limb(.43f,.26f,.57f,.26f,true)
            }
            "curl" -> {
                head(.50f,.20f); limb(.50f,.29f,.50f,.62f)
                limb(.50f,.62f,.38f,.88f); limb(.50f,.62f,.62f,.88f)
                limb(.50f,.39f,.36f,.50f); limb(.36f,.50f,.40f,.37f)
                limb(.50f,.39f,.64f,.50f); limb(.64f,.50f,.60f,.37f)
                limb(.38f,.37f,.62f,.37f,true)
            }
            "dead_bug" -> {
                limb(.17f,.78f,.81f,.78f,true)
                head(.35f,.59f); limb(.44f,.62f,.62f,.63f)
                limb(.49f,.62f,.36f,.34f); limb(.53f,.62f,.72f,.39f)
                limb(.60f,.62f,.68f,.76f); limb(.68f,.76f,.78f,.61f)
            }
            "hip_hinge" -> {
                head(.38f,.28f); limb(.42f,.35f,.59f,.55f)
                limb(.59f,.55f,.50f,.72f); limb(.50f,.72f,.45f,.89f)
                limb(.59f,.55f,.69f,.72f); limb(.69f,.72f,.72f,.89f)
                limb(.48f,.43f,.51f,.67f); limb(.59f,.47f,.62f,.67f)
                limb(.43f,.69f,.68f,.69f,true)
            }
            "single_leg_squat" -> {
                head(.45f,.18f); limb(.45f,.27f,.50f,.55f)
                limb(.50f,.38f,.31f,.42f); limb(.50f,.38f,.67f,.42f)
                limb(.50f,.55f,.58f,.70f); limb(.58f,.70f,.50f,.88f)
                limb(.50f,.55f,.34f,.63f); limb(.34f,.63f,.19f,.63f)
            }
            else -> {
                head(.50f,.20f); limb(.50f,.30f,.50f,.60f)
                limb(.50f,.40f,.34f,.52f); limb(.50f,.40f,.66f,.52f)
                limb(.50f,.60f,.40f,.88f); limb(.50f,.60f,.60f,.88f)
            }
        }
    }
}
