package com.fitflow.training.stretch

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

@Composable
fun StretchDiagram(kind: String) {
    Canvas(Modifier.fillMaxWidth().height(125.dp).background(Color(0xFFE8F0E7), RoundedCornerShape(12.dp))) {
        val ink = Color(0xFF244842)
        fun p(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(ink, p(x,y), p(xx,yy), 6.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(ink, 10.dp.toPx(), p(.5f,.2f))
        line(.5f,.29f,.5f,.64f)
        when(kind) {
            "chest" -> { line(.5f,.4f,.24f,.4f); line(.5f,.4f,.76f,.4f) }
            "back" -> { line(.5f,.4f,.30f,.58f); line(.5f,.4f,.70f,.58f) }
            "shoulder" -> { line(.5f,.39f,.70f,.43f); line(.70f,.43f,.35f,.43f) }
            "arms" -> { line(.5f,.4f,.37f,.18f); line(.5f,.4f,.63f,.18f) }
            else -> { line(.5f,.4f,.35f,.53f); line(.5f,.4f,.65f,.53f) }
        }
        if (kind == "legs") { line(.5f,.64f,.38f,.80f); line(.38f,.80f,.44f,.63f); line(.5f,.64f,.65f,.86f) }
        else { line(.5f,.64f,.39f,.88f); line(.5f,.64f,.61f,.88f) }
    }
}
