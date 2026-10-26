package ru.durak.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import ru.durak.offline.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/** Draws a suit symbol as a vector shape inside the square at [topLeft] with side [size]. */
fun DrawScope.drawSuit(suit: Suit, color: Color, topLeft: Offset, size: Float) {
    fun p(x: Float, y: Float) = Offset(topLeft.x + x * size, topLeft.y + y * size)
    when (suit) {
        Suit.DIAMONDS -> {
            val path = Path().apply {
                val a = p(.5f, 0f); moveTo(a.x, a.y)
                val b = p(.88f, .5f); val c = p(.5f, 1f); val d = p(.12f, .5f)
                quadraticTo(p(.68f, .3f).x, p(.68f, .3f).y, b.x, b.y)
                quadraticTo(p(.68f, .7f).x, p(.68f, .7f).y, c.x, c.y)
                quadraticTo(p(.32f, .7f).x, p(.32f, .7f).y, d.x, d.y)
                quadraticTo(p(.32f, .3f).x, p(.32f, .3f).y, a.x, a.y)
                close()
            }
            drawPath(path, color)
        }
        Suit.HEARTS -> {
            val path = Path().apply {
                val s = p(.5f, .95f); moveTo(s.x, s.y)
                val c1 = p(.02f, .6f); val c2 = p(-.04f, .12f); val e = p(.27f, .08f)
                cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
                val c3 = p(.42f, .05f); val c4 = p(.5f, .18f); val m = p(.5f, .26f)
                cubicTo(c3.x, c3.y, c4.x, c4.y, m.x, m.y)
                val c5 = p(.5f, .18f); val c6 = p(.58f, .05f); val e2 = p(.73f, .08f)
                cubicTo(c5.x, c5.y, c6.x, c6.y, e2.x, e2.y)
                val c7 = p(1.04f, .12f); val c8 = p(.98f, .6f)
                cubicTo(c7.x, c7.y, c8.x, c8.y, s.x, s.y)
                close()
            }
            drawPath(path, color)
        }
        Suit.SPADES -> {
            val path = Path().apply {
                val s = p(.5f, .03f); moveTo(s.x, s.y)
                val c1 = p(.98f, .4f); val c2 = p(1.02f, .78f); val e = p(.72f, .76f)
                cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
                val c3 = p(.6f, .75f); val c4 = p(.54f, .68f); val m = p(.53f, .64f)
                cubicTo(c3.x, c3.y, c4.x, c4.y, m.x, m.y)
                lineTo(p(.62f, .97f).x, p(.62f, .97f).y); lineTo(p(.38f, .97f).x, p(.38f, .97f).y)
                val m2 = p(.47f, .64f); lineTo(m2.x, m2.y)
                val c5 = p(.46f, .68f); val c6 = p(.4f, .75f); val e2 = p(.28f, .76f)
                cubicTo(c5.x, c5.y, c6.x, c6.y, e2.x, e2.y)
                val c7 = p(-.02f, .78f); val c8 = p(.02f, .4f)
                cubicTo(c7.x, c7.y, c8.x, c8.y, s.x, s.y)
                close()
            }
            drawPath(path, color)
        }
        Suit.CLUBS -> {
            drawCircle(color, size * .21f, p(.5f, .27f))
            drawCircle(color, size * .21f, p(.27f, .58f))
            drawCircle(color, size * .21f, p(.73f, .58f))
            drawCircle(color, size * .1f, p(.5f, .5f))
            val stem = Path().apply {
                moveTo(p(.5f, .5f).x, p(.5f, .5f).y); lineTo(p(.63f, .97f).x, p(.63f, .97f).y)
                lineTo(p(.37f, .97f).x, p(.37f, .97f).y); close()
            }
            drawPath(stem, color)
        }
    }
}

@Composable
fun SuitIcon(suit: Suit, size: Dp, modifier: Modifier = Modifier, color: Color = suitColor(suit)) {
    Canvas(modifier.size(size)) { drawSuit(suit, color, Offset.Zero, this.size.width) }
}

/** Pip centres (x, y in 0..1 of the pip area) for number cards. */
private val PIPS: Map<Int, List<Pair<Float, Float>>> = run {
    val col6 = listOf(0f to 0f, 0f to .5f, 0f to 1f, 1f to 0f, 1f to .5f, 1f to 1f)
    val col8 = listOf(0f to 0f, 0f to .333f, 0f to .667f, 0f to 1f, 1f to 0f, 1f to .333f, 1f to .667f, 1f to 1f)
    mapOf(
        2 to listOf(.5f to 0f, .5f to 1f),
        3 to listOf(.5f to 0f, .5f to .5f, .5f to 1f),
        4 to listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f),
        5 to listOf(0f to 0f, 1f to 0f, .5f to .5f, 0f to 1f, 1f to 1f),
        6 to col6,
        7 to col6 + (.5f to .25f),
        8 to col6 + (.5f to .25f) + (.5f to .75f),
        9 to col8 + (.5f to .5f),
        10 to col8 + (.5f to .17f) + (.5f to .83f),
    )
}

private fun courtRes(c: Card): Int = when (c.rank) {
    11 -> when (c.suit) { Suit.SPADES -> R.drawable.court_js; Suit.CLUBS -> R.drawable.court_jc; Suit.DIAMONDS -> R.drawable.court_jd; Suit.HEARTS -> R.drawable.court_jh }
    12 -> when (c.suit) { Suit.SPADES -> R.drawable.court_qs; Suit.CLUBS -> R.drawable.court_qc; Suit.DIAMONDS -> R.drawable.court_qd; Suit.HEARTS -> R.drawable.court_qh }
    else -> when (c.suit) { Suit.SPADES -> R.drawable.court_ks; Suit.CLUBS -> R.drawable.court_kc; Suit.DIAMONDS -> R.drawable.court_kd; Suit.HEARTS -> R.drawable.court_kh }
}

val Condensed = FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))

@Composable
fun CardFace(card: Card, width: Dp, modifier: Modifier = Modifier, dim: Boolean = false, glow: Float = 0f) {
    val h = width * CARD_RATIO
    val c = suitColor(card.suit)
    val shape = RoundedCornerShape(width * 0.07f)
    Box(
        modifier
            .size(width, h)
            .shadow(5.dp, shape)
            .clip(shape)
            .background(Color.White)
            .border(if (glow > 0f) 3.dp else 0.6.dp, if (glow > 0f) Pal.Gold.copy(alpha = glow) else Color(0xFFCFCFCA), shape)
    ) {
        when {
            card.rank in 11..13 -> Image(
                painterResource(courtRes(card)), contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().padding(start = width * 0.22f, top = width * 0.07f, end = width * 0.07f, bottom = width * 0.07f),
            )
            else -> Canvas(Modifier.fillMaxSize()) {
                val cw = size.width; val ch = size.height
                if (card.rank == 14) {
                    val s = cw * 0.5f
                    drawSuit(card.suit, c, Offset(cw * 0.56f - s / 2, ch * 0.55f - s / 2), s)
                } else {
                    val pip = cw * 0.2f
                    val left = cw * 0.42f; val right = cw * 0.8f; val top = ch * 0.17f; val bottom = ch * 0.86f
                    for ((x, y) in PIPS.getValue(card.rank)) {
                        val cx = left + (right - left) * x; val cy = top + (bottom - top) * y
                        drawSuit(card.suit, c, Offset(cx - pip / 2, cy - pip / 2), pip)
                    }
                }
            }
        }
        CornerIndex(card, c, width, Modifier.align(Alignment.TopStart).padding(start = width * 0.03f, top = width * 0.02f))
        if (dim) Box(Modifier.matchParentSize().background(Color(0x330A1A33)))
    }
}

@Composable
private fun CornerIndex(card: Card, c: Color, width: Dp, modifier: Modifier) {
    val w = width.value
    Column(modifier.width(width * 0.2f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            card.rankLabel, color = c, fontSize = (w * if (card.rank == 10) 0.25f else 0.32f).sp, fontFamily = Condensed,
            lineHeight = (w * 0.32f).sp, maxLines = 1, softWrap = false, letterSpacing = (-0.5).sp,
        )
        SuitIcon(card.suit, width * 0.16f)
    }
}

/** Green-and-white plaid back, as in the original. */
@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    val h = width * CARD_RATIO
    val shape = RoundedCornerShape(width * 0.07f)
    Box(modifier.size(width, h).shadow(2.dp, shape).clip(shape).background(Color.White).border(0.6.dp, Color(0xFFBDBDBD), shape).padding(width * 0.05f)) {
        Canvas(Modifier.matchParentSize().clip(RoundedCornerShape(width * 0.04f)).background(Color(0xFFF4FAF6))) {
            val unit = size.width / 5f
            val wide = Pal.Plaid.copy(alpha = 0.45f)
            val thin = Pal.Plaid.copy(alpha = 0.8f)
            var x = 0f
            while (x < size.width) {
                drawRect(wide, Offset(x, 0f), androidx.compose.ui.geometry.Size(unit * 0.38f, size.height))
                drawRect(thin, Offset(x + unit * 0.62f, 0f), androidx.compose.ui.geometry.Size(unit * 0.08f, size.height))
                x += unit
            }
            var y = 0f
            while (y < size.height) {
                drawRect(wide, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, unit * 0.38f))
                drawRect(thin, Offset(0f, y + unit * 0.62f), androidx.compose.ui.geometry.Size(size.width, unit * 0.08f))
                y += unit
            }
        }
    }
}

/** Entrance animation: flies in from ([fromX], [fromY]) px with a little spin, once, when first composed. */
@Composable
fun Pop(modifier: Modifier = Modifier, fromX: Float = 0f, fromY: Float = 0f, delayMs: Long = 0, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs)
        a.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 280f))
    }
    Box(
        modifier.graphicsLayer {
            val k = 1f - a.value
            translationX = fromX * k; translationY = fromY * k
            rotationZ = -16f * k
            val s = 0.8f + 0.2f * a.value
            scaleX = s; scaleY = s
            alpha = (a.value * 2.5f).coerceIn(0f, 1f)
        }
    ) { content() }
}
