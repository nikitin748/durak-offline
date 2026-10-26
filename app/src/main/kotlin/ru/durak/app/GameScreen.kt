package ru.durak.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import ru.durak.engine.*
import ru.durak.net.SeatInfo
import ru.durak.net.SeatKind

private fun handSorted(hand: List<Card>, trump: Suit) =
    hand.sortedWith(compareBy<Card> { if (it.suit == trump) 1 else 0 }.thenBy { it.suit.ordinal }.thenBy { it.rank })

@Composable
fun GameScreen(vm: AppVm) {
    val v = vm.view ?: return
    val me = v.seat
    val n = v.playerCount
    val others = (1 until n).map { (me + it) % n }
    val iAmDefender = me == v.defender
    val l = v.legal
    val hand = handSorted(v.myHand, v.trump)

    var selected by remember { mutableStateOf<Card?>(null) }   // card picked from hand (when it fits several targets)
    var dragCard by remember { mutableStateOf<Card?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var transferMode by remember { mutableStateOf(false) }
    val targets = remember { mutableStateMapOf<Int, Rect>() }  // attack cards on the table (root coords)
    val handRects = remember { mutableStateMapOf<Card, Rect>() }
    var tableZone by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(v.table.size, v.defender, v.turnNumber, v.table.count { it.defense != null }) { selected = null; transferMode = false }
    LaunchedEffect(v.table.size) { targets.keys.filter { it >= v.table.size }.forEach { targets.remove(it) } }
    LaunchedEffect(hand) { handRects.keys.filter { it !in hand }.forEach { handRects.remove(it) } }

    fun info(seat: Int): SeatInfo = vm.seats.getOrNull(seat) ?: SeatInfo("?", 0, SeatKind.BOT)
    fun targetsFor(c: Card?) = if (c == null || !iAmDefender) emptySet() else l.defend.filter { it.card == c }.map { it.index }.toSet()

    fun defendWith(c: Card, index: Int?): Boolean {
        val opts = l.defend.filter { it.card == c }
        val pick = (if (index != null) opts.firstOrNull { it.index == index } else null) ?: opts.singleOrNull()
        if (pick != null) { vm.act(Action.Defend(pick.index, pick.card)); selected = null; return true }
        return false
    }

    fun tapHand(c: Card) {
        if (v.gameOver) return
        if (iAmDefender) {
            if (c in l.transfer && (transferMode || l.defend.none { it.card == c })) { vm.act(Action.Transfer(c)); transferMode = false; return }
            val opts = l.defend.filter { it.card == c }
            when {
                opts.isEmpty() -> vm.flash(if (l.defend.isEmpty() && l.canTake) "Отбиться нечем — нажмите «Беру»" else "Эта карта не бьёт")
                opts.size == 1 -> defendWith(c, opts[0].index)
                else -> selected = if (selected == c) null else c
            }
        } else {
            if (c in l.attack) vm.act(Action.Attack(c))
            else vm.flash(
                when {
                    v.table.isEmpty() && me != v.attacker -> "Сейчас ходит ${info(v.attacker).name}"
                    v.table.isEmpty() -> "Ходите любой картой"
                    else -> "Подкинуть можно карту того же достоинства, что на столе"
                }
            )
        }
    }

    fun dropHand(c: Card, p: Offset) {
        android.util.Log.d("DurakUI", "drop $c at $p table=$tableZone targets=${targets.toMap()} defend=${l.defend}")
        if (v.gameOver) return
        val inTable = tableZone.contains(p)
        if (iAmDefender) {
            val hit = targets.entries
                .filter { (i, r) -> r.inflate(40f).contains(p) && l.defend.any { it.index == i && it.card == c } }
                .minByOrNull { (it.value.center - p).getDistance() }?.key
            if (hit != null) { defendWith(c, hit); return }
            if (!inTable) return
            if (c in l.transfer && (transferMode || l.defend.none { it.card == c })) { vm.act(Action.Transfer(c)); transferMode = false; return }
            if (!defendWith(c, null)) {
                if (l.defend.any { it.card == c }) selected = c else vm.flash("Эта карта не бьёт")
            }
        } else if (inTable) tapHand(c)
    }

    fun tapTable(p: Offset) {
        val c = selected ?: return
        val hit = targets.entries.firstOrNull { it.value.inflate(16f).contains(p) }?.key ?: return
        if (!defendWith(c, hit)) vm.flash("Этой картой эту не побить")
    }

    fun handHit(p: Offset): Card? = hand.lastOrNull { handRects[it]?.contains(p) == true }

    // One gesture handler for the whole table: taps and drags of hand cards, taps on table cards.
    val onTapHand by rememberUpdatedState(::tapHand)
    val onDrop by rememberUpdatedState(::dropHand)
    val onTapTable by rememberUpdatedState(::tapTable)
    val hitTest by rememberUpdatedState(::handHit)
    val gestures = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val card = hitTest(down.position)
            android.util.Log.d("DurakUI", "down at ${down.position} -> $card")
            if (card == null) {
                val up = waitForUpOrCancellation()
                if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) onTapTable(up.position)
                return@awaitEachGesture
            }
            down.consume()
            var dragging = false
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                val delta = ch.position - down.position
                if (!ch.pressed) {
                    android.util.Log.d("DurakUI", "up at ${ch.position} dragging=$dragging")
                    if (dragging) onDrop(card, ch.position) else onTapHand(card)
                    break
                }
                if (!dragging && delta.getDistance() > viewConfiguration.touchSlop) { dragging = true; dragCard = card }
                if (dragging) { dragOffset = delta; ch.consume() }
            }
            dragCard = null; dragOffset = Offset.Zero
        }
    }

    val glowSet = targetsFor(dragCard ?: selected)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "p")
    val density = LocalDensity.current
    fun originOf(seat: Int): Offset {
        if (seat == me || seat < 0) return Offset(0f, with(density) { 360.dp.toPx() })
        val j = others.indexOf(seat).coerceAtLeast(0)
        val k = others.size.coerceAtLeast(1)
        return Offset(((j + 0.5f) / k - 0.5f) * with(density) { 340.dp.toPx() }, -with(density) { 300.dp.toPx() })
    }

    Felt(Modifier.fillMaxSize().then(gestures)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // ---- top bar: menu, opponents, coins ----
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x44000000)).clickable { vm.leaveToMenu() }, contentAlignment = Alignment.Center) {
                    Text("✕", color = Color.White, fontSize = 16.sp)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    others.forEach { s -> Opponent(s, info(s), v, vm.stickers.firstOrNull { it.seat == s }, Modifier.weight(1f)) }
                }
                CoinChip(vm.coins, Modifier.padding(top = 4.dp))
            }
            // ---- table ----
            Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { tableZone = it.boundsInRoot() }) {
                TalonView(v, Modifier.align(Alignment.CenterStart).offset(x = (-14).dp))
                TableView(v, glowSet, pulse, { i, r -> targets[i] = r }, ::originOf, Modifier.align(Alignment.Center))
                Text(
                    hintText(v, me, ::info), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0x66000000)).padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Column(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 6.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (l.transfer.isNotEmpty()) ActionPill(if (transferMode) "Выберите карту" else "Перевести", Pal.Orange, "btn:transfer") { transferMode = !transferMode }
                    if (l.canTake) ActionPill("Беру", Color(0xFFD9534F), "btn:take") { vm.act(Action.Take) }
                    else if (l.canPass) ActionPill(if (v.defenderTaking) "Пас" else if (v.table.none { it.defense == null }) "Бито" else "Пас", Pal.Green, "btn:pass") { vm.act(Action.Pass) }
                }
                vm.toast?.let { Toast(it) }
            }
            // ---- my hand ----
            HandView(v, hand, selected, transferMode, dragCard, dragOffset) { c, r -> handRects[c] = r }
            // ---- bottom bar: me + stickers ----
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x88000000)))).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    PlayerAvatar(info(me), v, me, 48.dp)
                    vm.stickers.firstOrNull { it.seat == me }?.let { StickerBubble(it, Modifier.align(Alignment.TopCenter).offset(y = (-58).dp)) }
                }
                Spacer(Modifier.width(8.dp))
                Text(info(me).name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.widthIn(max = 72.dp))
                Spacer(Modifier.width(6.dp))
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(STICKERS) { i, e ->
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFFFFF)).clickable { vm.sendSticker(i) }, contentAlignment = Alignment.Center) {
                            Text(e, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
        if (v.gameOver) GameOver(v, ::info, vm)
    }
}

private fun hintText(v: GameView, me: Int, info: (Int) -> SeatInfo): String {
    if (v.gameOver) return "Игра окончена"
    val l = v.legal
    return when {
        me in v.finished -> "Вы вышли из игры"
        me == v.defender && v.defenderTaking -> "Вы берёте карты"
        me == v.defender && l.defend.isEmpty() && l.canTake -> "Нечем отбиться — нажмите «Беру»"
        me == v.defender && v.table.any { it.defense == null } -> "Отбивайтесь: перетащите карту на карту соперника"
        me == v.defender -> "Ждём, подкинут ли ещё"
        v.table.isEmpty() && me == v.attacker -> "Ваш ход: перетащите или нажмите карту"
        v.table.isEmpty() -> "Ходит ${info(v.attacker).name}"
        v.defenderTaking && l.attack.isNotEmpty() -> "${info(v.defender).name} берёт — можно подкинуть"
        l.attack.isNotEmpty() -> "Можно подкинуть"
        else -> "Отбивается ${info(v.defender).name}"
    }
}

@Composable
fun ActionPill(text: String, color: Color, tag: String = text, onClick: () -> Unit) {
    Box(
        Modifier.testTag(tag).clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.95f), color))).border(2.dp, Color(0x99FFFFFF), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp)
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
}

/** Rounded-square avatar with role mark (⚔ attacking, 🛡 defending). */
@Composable
fun PlayerAvatar(info: SeatInfo, v: GameView, seat: Int, size: Dp) {
    val ring = when {
        v.gameOver -> Color.White
        seat == v.defender -> Pal.Green
        seat == v.attacker -> Pal.Orange
        else -> Color.White
    }
    val i = info.avatar.mod(AVATARS.size)
    Box {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(Brush.verticalGradient(listOf(AVATAR_BG[i], AVATAR_BG[i].copy(alpha = 0.75f))))
                .border(if (ring == Color.White) 2.dp else 3.dp, ring, RoundedCornerShape(size * 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Text(AVATARS[i], fontSize = (size.value * 0.58f).sp) }
        val mark = when {
            v.gameOver -> null
            seat in v.finished -> "✅"
            seat == v.defender && v.defenderTaking -> "🫴"
            seat == v.defender -> "🛡"
            seat == v.attacker -> "⚔"
            seat in v.passed -> "✋"
            else -> null
        }
        if (mark != null) Box(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).size(22.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Text(mark, fontSize = 13.sp)
        }
    }
}

@Composable
fun Opponent(seat: Int, info: SeatInfo, v: GameView, sticker: StickerEvt?, modifier: Modifier = Modifier) {
    val count = v.handSizes[seat]
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            PlayerAvatar(info, v, seat, 46.dp)
            sticker?.let { StickerBubble(it, Modifier.align(Alignment.BottomCenter).offset(y = 64.dp).zIndex(5f)) }
        }
        Text(
            info.name, color = Color.White, fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x55000000)).padding(horizontal = 5.dp),
        )
        Box(Modifier.height(44.dp).padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
            if (count > 0) {
                val shown = minOf(count, 6)
                for (i in 0 until shown) {
                    val t = i - (shown - 1) / 2f
                    CardBack(24.dp, Modifier.offset(x = (t * 6).dp, y = (t * t * 0.6f).dp).rotate(t * 8f))
                }
                Badge("$count", Modifier.align(Alignment.BottomEnd).offset(x = 6.dp), Color(0xFF1F3A5F))
            }
        }
    }
}

@Composable
fun StickerBubble(s: StickerEvt, modifier: Modifier = Modifier) {
    key(s.stamp) {
        Pop(modifier, fromY = 40f) {
            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).border(2.dp, Pal.Gold, RoundedCornerShape(18.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(STICKERS[s.id.mod(STICKERS.size)], fontSize = 40.sp)
            }
        }
    }
}

@Composable
fun TalonView(v: GameView, modifier: Modifier = Modifier) {
    Box(modifier.size(84.dp, 110.dp)) {
        val tc = v.trumpCard
        if (tc != null) CardFace(tc, 50.dp, Modifier.align(Alignment.CenterStart).offset(x = 26.dp).rotate(90f))
        if (v.deckSize > 0) {
            val layers = minOf(4, (v.deckSize + 5) / 6)
            for (i in 0 until layers) CardBack(50.dp, Modifier.align(Alignment.CenterStart).offset(x = (i * 1.5f).dp, y = (-i * 1.5f).dp))
            Badge("${v.deckSize}", Modifier.align(Alignment.TopStart), Color(0xFF1F3A5F))
        } else {
            Box(Modifier.align(Alignment.Center).size(54.dp).clip(CircleShape).background(Color(0x55FFFFFF)), contentAlignment = Alignment.Center) {
                Text(v.trump.symbol, color = suitColor(v.trump), fontSize = 36.sp)
            }
        }
        if (v.discardCount > 0) Text("бито: ${v.discardCount}", color = Color(0xAAFFFFFF), fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
fun TableView(
    v: GameView, glow: Set<Int>, pulse: Float,
    onTargetBounds: (Int, Rect) -> Unit, originOf: (Int) -> Offset, modifier: Modifier = Modifier,
) {
    val cw = if (v.table.size > 3) 66.dp else 78.dp
    Column(modifier.padding(start = 56.dp, end = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        v.table.withIndex().toList().chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (i, p) ->
                    key(i, p.attack) {
                        val from = originOf(p.attackBy)
                        Box(Modifier.size(cw + 16.dp, cw * CARD_RATIO + 22.dp)) {
                            Pop(Modifier.align(Alignment.TopStart), fromX = from.x, fromY = from.y) {
                                CardFace(
                                    p.attack, cw,
                                    Modifier.testTag("attack:$i").onGloballyPositioned { onTargetBounds(i, it.boundsInRoot()) }.rotate(-3f),
                                    glow = if (i in glow && p.defense == null) pulse else 0f,
                                )
                            }
                            p.defense?.let { d ->
                                key(d) {
                                    val df = originOf(v.defender)
                                    Pop(Modifier.align(Alignment.BottomEnd), fromX = df.x, fromY = df.y) { CardFace(d, cw, Modifier.rotate(6f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HandView(
    v: GameView, cards: List<Card>, selected: Card?, transferMode: Boolean, dragCard: Card?, dragOffset: Offset,
    onBounds: (Card, Rect) -> Unit,
) {
    val l = v.legal
    val anyPlayable = l.attack.isNotEmpty() || l.defend.isNotEmpty() || l.transfer.isNotEmpty()
    BoxWithConstraints(Modifier.fillMaxWidth().height(158.dp)) {
        val cw = 86.dp
        val n = cards.size
        val avail = maxWidth - cw - 10.dp
        val step = if (n <= 1) 0.dp else minOf(cw * 0.62f, avail / (n - 1))
        val total = step * (n - 1) + cw
        val startX = (maxWidth - total) / 2
        cards.forEachIndexed { i, c ->
            key(c) {
                val t = if (n > 1) i / (n - 1f) - 0.5f else 0f
                val playable = c in l.attack || l.defend.any { it.card == c } || c in l.transfer
                val dragged = c == dragCard
                val ax by animateDpAsState(startX + step * i, label = "x")
                val ay by animateDpAsState(22.dp + (t * t * 24f).dp - (if (c == selected) 28.dp else if (playable) 12.dp else 0.dp), label = "y")
                Box(
                    Modifier.offset(x = ax, y = ay).zIndex(if (dragged) 10f else i.toFloat()).testTag("hand:$c")
                        .onGloballyPositioned { onBounds(c, it.boundsInRoot()) }
                ) {
                    Pop(fromX = -300f, fromY = -420f, delayMs = (i.coerceAtMost(8) * 70).toLong()) {
                        CardFace(
                            c, cw,
                            Modifier.graphicsLayer {
                                if (dragged) { translationX = dragOffset.x; translationY = dragOffset.y; scaleX = 1.1f; scaleY = 1.1f }
                                else rotationZ = t * (if (n > 8) 14f else 10f)
                                transformOrigin = TransformOrigin(0.5f, 1.6f)
                            },
                            dim = anyPlayable && !playable && !dragged,
                            glow = if (c == selected || (transferMode && c in l.transfer)) 1f else 0f,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.GameOver(v: GameView, info: (Int) -> SeatInfo, vm: AppVm) {
    val iLost = v.loser == v.seat
    Box(Modifier.matchParentSize().background(Color(0xAA000000)).pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }, contentAlignment = Alignment.Center) {
        Pop(fromY = 200f) {
            Column(Modifier.padding(24.dp).clip(RoundedCornerShape(22.dp)).background(Pal.Panel).border(2.dp, Pal.Gold, RoundedCornerShape(22.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v.loser == null) "🤝" else if (iLost) "🤡" else "🏆", fontSize = 64.sp)
                Text(
                    when { v.loser == null -> "Ничья!"; iLost -> "Вы дурак!"; else -> "Дурак — ${info(v.loser!!).name}" },
                    color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                )
                Text(if (iLost) "−10 монет" else if (v.loser == null) "" else "+10 монет", color = Pal.Gold, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                if (vm.isHost) BigButton("Играть ещё", { vm.playAgain() }, Modifier.fillMaxWidth(), Pal.Green)
                else Text("Ждём, пока хозяин начнёт новую игру…", color = Color(0xFFB9CCE0), fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                BigButton("В меню", { vm.leaveToMenu() }, Modifier.fillMaxWidth(), Color(0xFF52718F))
            }
        }
    }
}
