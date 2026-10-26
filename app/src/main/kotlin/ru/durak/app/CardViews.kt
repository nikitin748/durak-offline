package ru.durak.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.durak.engine.Card
import ru.durak.engine.Suit

const val CARD_RATIO = 1.45f

fun suitColor(s: Suit) = if (s == Suit.HEARTS || s == Suit.DIAMONDS) Pal.Red else Pal.Ink

/** Pip positions (x, y in 0..1) for number cards, like a real deck. */
private val PIPS: Map<Int, List<Pair<Float, Float>>> = run {
    val col6 = listOf(0f to 0f, 0f to .5f, 0f to 1f, 1f to 0f, 1f to .5f, 1f to 1f)
    val col8 = listOf(0f to 0f, 0f to .333f, 0f to .667f, 0f to 1f, 1f to 0f, 1f to .333f, 1f to .667f, 1f to 1f)
    mapOf(
        6 to col6,
        7 to col6 + (.5f to .25f),
        8 to col6 + (.5f to .25f) + (.5f to .75f),
        9 to col8 + (.5f to .5f),
        10 to col8 + (.5f to .17f) + (.5f to .83f),
    )
}

private val COURT = mapOf(11 to "💂", 12 to "👸", 13 to "🤴")
private val COURT_BG = mapOf(
    11 to listOf(Color(0xFFFFF1C1), Color(0xFFF7C66B)),
    12 to listOf(Color(0xFFFFE3E8), Color(0xFFF59AAE)),
    13 to listOf(Color(0xFFE3EEFF), Color(0xFF8DB4F2)),
)

@Composable
fun CardFace(card: Card, width: Dp, modifier: Modifier = Modifier, dim: Boolean = false, glow: Float = 0f) {
    val h = width * CARD_RATIO
    val c = suitColor(card.suit)
    val shape = RoundedCornerShape(width * 0.1f)
    val w = width.value
    Box(
        modifier
            .size(width, h)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White, Pal.Paper)))
            .border(if (glow > 0f) 3.dp else 0.7.dp, if (glow > 0f) Pal.Gold.copy(alpha = glow) else Color(0xFFBDBDB5), shape)
    ) {
        // corner indexes
        CornerIndex(card, c, w, Modifier.align(Alignment.TopStart).padding(start = width * 0.05f, top = width * 0.03f))
        CornerIndex(card, c, w, Modifier.align(Alignment.BottomEnd).padding(end = width * 0.05f, bottom = width * 0.03f).rotate(180f))

        val inner = Modifier.fillMaxSize().padding(horizontal = width * 0.24f, vertical = h * 0.15f)
        when {
            card.rank == 14 -> Box(inner, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(width * 0.62f)) {
                    drawCircle(c.copy(alpha = 0.10f))
                    drawCircle(c.copy(alpha = 0.45f), style = Stroke(width = 2f))
                }
                Text(card.suit.symbol, color = c, fontSize = (w * 0.56f).sp)
            }
            card.rank >= 11 -> Box(
                Modifier.fillMaxSize().padding(horizontal = width * 0.18f, vertical = h * 0.15f).clip(RoundedCornerShape(width * 0.05f))
                    .background(Brush.verticalGradient(COURT_BG.getValue(card.rank)))
                    .border(1.5.dp, c.copy(alpha = 0.7f), RoundedCornerShape(width * 0.05f)),
            ) {
                Text(card.suit.symbol, color = c, fontSize = (w * 0.18f).sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp))
                Text(COURT.getValue(card.rank), fontSize = (w * 0.38f).sp, modifier = Modifier.align(Alignment.Center))
                Text(card.suit.symbol, color = c, fontSize = (w * 0.18f).sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp).rotate(180f))
            }
            else -> Box(inner) {
                for ((x, y) in PIPS.getValue(card.rank)) {
                    Text(
                        card.suit.symbol, color = c, fontSize = (w * 0.20f).sp,
                        modifier = Modifier.align(BiasAlignment(x * 2 - 1, y * 2 - 1)).then(if (y > 0.55f) Modifier.rotate(180f) else Modifier),
                    )
                }
            }
        }
        if (dim) Box(Modifier.matchParentSize().background(Color(0x40000000)))
    }
}

@Composable
private fun CornerIndex(card: Card, c: Color, w: Float, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            card.rankLabel, color = c, fontSize = (w * if (card.rank == 10) 0.22f else 0.26f).sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif, lineHeight = (w * 0.26f).sp,
        )
        Text(card.suit.symbol, color = c, fontSize = (w * 0.18f).sp, lineHeight = (w * 0.18f).sp)
    }
}

@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    val h = width * CARD_RATIO
    val shape = RoundedCornerShape(width * 0.1f)
    Box(modifier.size(width, h).shadow(2.dp, shape).clip(shape).background(Color.White).padding(width * 0.06f)) {
        Canvas(Modifier.matchParentSize().clip(RoundedCornerShape(width * 0.06f)).background(Color(0xFF2556A0))) {
            val step = size.width / 4f
            val line = Color(0x40FFFFFF)
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(line, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 1.5f)
                drawLine(line, Offset(x + size.height, 0f), Offset(x, size.height), strokeWidth = 1.5f)
                x += step
            }
            // central medallion
            val p = Path().apply {
                moveTo(size.width / 2, size.height * 0.3f); lineTo(size.width * 0.78f, size.height / 2)
                lineTo(size.width / 2, size.height * 0.7f); lineTo(size.width * 0.22f, size.height / 2); close()
            }
            drawPath(p, Color(0xFFC8102E))
            drawPath(p, Color.White, style = Stroke(width = 2f))
        }
    }
}

/**
 * Entrance animation: the card flies in from ([fromX], [fromY]) px with a little spin, then settles.
 * Runs once, when the composable first appears.
 */
@Composable
fun Pop(modifier: Modifier = Modifier, fromX: Float = 0f, fromY: Float = 0f, delayMs: Long = 0, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs)
        a.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 260f))
    }
    Box(
        modifier.graphicsLayer {
            val k = 1f - a.value
            translationX = fromX * k; translationY = fromY * k
            rotationZ = -18f * k
            val s = 0.75f + 0.25f * a.value
            scaleX = s; scaleY = s
            alpha = (a.value * 2.5f).coerceIn(0f, 1f)
        }
    ) { content() }
}
