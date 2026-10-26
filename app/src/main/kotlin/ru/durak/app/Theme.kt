package ru.durak.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Pal {
    val FeltLight = Color(0xFF3A74AE)
    val FeltDark = Color(0xFF1F4C7C)
    val FeltDot = Color(0x14FFFFFF)
    val Red = Color(0xFFC8102E)
    val Ink = Color(0xFF1B1B1F)
    val Paper = Color(0xFFFDFCF7)
    val Gold = Color(0xFFFFC53D)
    val Orange = Color(0xFFFF9800)
    val Green = Color(0xFF43A047)
    val Panel = Color(0xCC12263F)
    val ButtonBlue = Color(0xFF2E7BC4)
}

/** Blue felt with the faint diamond lattice of the original table. */
@Composable
fun Felt(modifier: Modifier = Modifier.fillMaxSize(), content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.background(Brush.radialGradient(listOf(Pal.FeltLight, Pal.FeltDark), radius = 1400f))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 26f
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else step / 2
                while (x < size.width) {
                    drawCircle(Pal.FeltDot, radius = 2.2f, center = Offset(x, y))
                    x += step
                }
                y += step / 2
                row++
            }
        }
        content()
    }
}
