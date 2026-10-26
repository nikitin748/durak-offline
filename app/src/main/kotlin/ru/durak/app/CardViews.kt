package ru.durak.app

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.durak.engine.Card
import ru.durak.engine.Suit

const val CARD_RATIO = 1.42f

fun suitColor(s: Suit) = if (s == Suit.HEARTS || s == Suit.DIAMONDS) Pal.Red else Pal.Ink

@Composable
fun CardFace(card: Card, width: Dp, modifier: Modifier = Modifier, dim: Boolean = false, glow: Boolean = false) {
    val h = width * CARD_RATIO
    val c = suitColor(card.suit)
    val shape = RoundedCornerShape(width * 0.09f)
    Box(
        modifier
            .size(width, h)
            .shadow(3.dp, shape)
            .clip(shape)
            .background(Pal.Paper)
            .border(if (glow) 2.5.dp else 0.8.dp, if (glow) Pal.Gold else Color(0xFFB8B8B0), shape)
    ) {
        // corner index
        Column(Modifier.align(Alignment.TopStart).padding(start = width * 0.07f, top = width * 0.04f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(card.rankLabel, color = c, fontSize = (width.value * 0.30f).sp, fontWeight = FontWeight.Bold, lineHeight = (width.value * 0.32f).sp)
            Text(card.suit.symbol, color = c, fontSize = (width.value * 0.26f).sp, lineHeight = (width.value * 0.26f).sp)
        }
        // big center mark
        if (card.rank >= 11) {
            Box(
                Modifier.align(Alignment.Center).padding(top = width * 0.1f, start = width * 0.12f).size(width * 0.56f, h * 0.5f)
                    .border(1.2.dp, c.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .background(c.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (card.rank) { 11 -> "В"; 12 -> "Д"; 13 -> "К"; else -> "Т" },
                    color = c, fontSize = (width.value * 0.42f).sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                )
            }
            Text(card.suit.symbol, color = c, fontSize = (width.value * 0.34f).sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = width * 0.07f, bottom = width * 0.02f))
        } else {
            Text(card.suit.symbol, color = c, fontSize = (width.value * 0.62f).sp, modifier = Modifier.align(Alignment.Center).padding(top = width * 0.12f, start = width * 0.1f))
        }
        if (dim) Box(Modifier.matchParentSize().background(Color(0x33000000)))
    }
}

@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    val h = width * CARD_RATIO
    val shape = RoundedCornerShape(width * 0.09f)
    Box(modifier.size(width, h).shadow(2.dp, shape).clip(shape).background(Pal.Paper).padding(width * 0.06f)) {
        Canvas(Modifier.matchParentSize().clip(RoundedCornerShape(width * 0.05f)).background(Color(0xFF244E92))) {
            val step = size.width / 5f
            var y = 0f
            var row = 0
            while (y < size.height + step) {
                var x = if (row % 2 == 0) 0f else step / 2
                while (x < size.width + step) {
                    drawRect(Color(0x55FFFFFF), topLeft = Offset(x - 2f, y - 2f), size = Size(4f, 4f))
                    x += step
                }
                y += step / 2
                row++
            }
        }
    }
}

/** Little pop-in animation when a card first appears on the table. */
@Composable
fun Pop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, androidx.compose.animation.core.spring(0.55f, 380f)) }
    Box(modifier.graphicsLayer { scaleX = 0.6f + 0.4f * a.value; scaleY = 0.6f + 0.4f * a.value; alpha = a.value.coerceIn(0f, 1f) }) { content() }
}
