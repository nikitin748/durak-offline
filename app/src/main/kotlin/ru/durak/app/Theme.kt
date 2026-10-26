package ru.durak.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import ru.durak.offline.R

object Pal {
    val Felt = Color(0xFF486C9C)
    val FeltLine = Color(0x33FFFFFF)
    val Red = Color(0xFFD7141F)
    val FrameRed = Color(0xFFE2363F)
    val FrameGreen = Color(0xFF7AC943)
    val Ink = Color(0xFF111111)
    val Paper = Color(0xFFFDFCF7)
    val Gold = Color(0xFFFFC53D)
    val Orange = Color(0xFFFF9800)
    val Green = Color(0xFF43A047)
    val Panel = Color(0xCC1B2F4A)
    val ButtonBlue = Color(0xFF2E7BC4)
    val Bar = Color(0xFFF7F7F5)
    val BarText = Color(0xFF8A8A8A)
    val Plaid = Color(0xFF4FA57A)
}

val Script = FontFamily(Font(R.font.lobster))
val ScriptShadow = Shadow(Color(0x66000000), Offset(0f, 3f), 4f)
fun scriptStyle(size: Int, color: Color = Color.White) = TextStyle(fontFamily = Script, fontSize = size.sp, color = color, shadow = ScriptShadow)

/** Blue cloth table background (tiled texture), as in the original. */
@Composable
fun Felt(modifier: Modifier = Modifier.fillMaxSize(), content: @Composable BoxScope.() -> Unit) {
    val tile: ImageBitmap = ImageBitmap.imageResource(R.drawable.felt)
    val brush = remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
    Box(modifier.background(Pal.Felt).background(brush)) { content() }
}
