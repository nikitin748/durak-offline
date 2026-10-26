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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
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

    val ui = remember(vm) { GameUi(vm) }
    var stickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(v.table.size, v.defender, v.turnNumber, v.table.count { it.defense != null }) { ui.selected = null; ui.transferMode = false }
    LaunchedEffect(v.table.size) { ui.targets.keys.filter { it >= v.table.size }.forEach { ui.targets.remove(it) } }

    fun info(seat: Int): SeatInfo = vm.seats.getOrNull(seat) ?: SeatInfo("?", 0, SeatKind.BOT)
    fun targetsFor(c: Card?) = if (c == null || !iAmDefender) emptySet() else l.defend.filter { it.card == c }.map { it.index }.toSet()

    // One gesture handler for the whole table: taps and drags of hand cards, taps on table cards.
    // It talks to [ui], which always reads the latest game state from the view model.
    val gestures = Modifier.pointerInput(ui) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val o = ui.feltOrigin
            val card = ui.handHit(down.position + o)
            if (card == null) {
                val up = waitForUpOrCancellation()
                if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) ui.tapTable(up.position + o)
                return@awaitEachGesture
            }
            down.consume()
            var dragging = false
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                val delta = ch.position - down.position
                if (!ch.pressed) {
                    if (dragging) ui.dropHand(card, ch.position + o) else ui.tapHand(card)
                    break
                }
                if (!dragging && delta.getDistance() > viewConfiguration.touchSlop) { dragging = true; ui.dragCard = card }
                if (dragging) { ui.dragOffset = delta; ch.consume() }
            }
            ui.dragCard = null; ui.dragOffset = Offset.Zero
        }
    }

    val glowSet = targetsFor(ui.dragCard ?: ui.selected)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "p")
    val density = LocalDensity.current
    fun originOf(seat: Int): Offset {
        if (seat == me || seat < 0) return Offset(0f, with(density) { 360.dp.toPx() })
        val j = others.indexOf(seat).coerceAtLeast(0)
        val k = others.size.coerceAtLeast(1)
        return Offset(((j + 0.5f) / k - 0.5f) * with(density) { 340.dp.toPx() }, -with(density) { 300.dp.toPx() })
    }

    Column(Modifier.fillMaxSize().background(Pal.Bar).systemBarsPadding()) {
        Felt(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { ui.feltOrigin = it.positionInRoot() }.then(gestures)) {
            Column(Modifier.fillMaxSize()) {
                // ---- top: mode icons + coins, then opponents ----
                Box(Modifier.fillMaxWidth().height(40.dp)) {
                    Icon(Icons.Filled.Close, null, tint = Color(0x99FFFFFF), modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(26.dp).clickable { vm.leaveToMenu() })
                    ModeIcons(v, Modifier.align(Alignment.Center))
                    CoinChip(vm.coins, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    others.forEach { s -> Opponent(s, info(s), v, vm.stickers.firstOrNull { it.seat == s }, Modifier.weight(1f)) }
                }
                // ---- table ----
                Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { ui.tableZone = it.boundsInRoot() }) {
                    TalonView(v, Modifier.align(Alignment.CenterStart))
                    DiscardView(v.discardCount, Modifier.align(Alignment.CenterEnd))
                    TableView(v, glowSet, pulse, { i, r -> ui.targets[i] = r }, { seat -> originOf(seat) }, Modifier.align(Alignment.Center))
                    Text(
                        hintText(v, me) { info(it) }, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color(0x55000000)).padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                    vm.toast?.let { Toast(it) }
                }
                // ---- my hand (partly hidden by the bottom bar, like the original) ----
                HandView(v, hand, ui.selected, ui.transferMode, ui.dragCard, ui.dragOffset) { ui.handRects = it }
            }
            // sticker picker
            if (stickerOpen) {
                Column(Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xF2FFFFFF)).padding(8.dp)) {
                    STICKERS.chunked(4).forEachIndexed { r, row ->
                        Row {
                            row.forEachIndexed { c, e ->
                                Text(e, fontSize = 32.sp, modifier = Modifier.clickable { vm.sendSticker(r * 4 + c); stickerOpen = false }.padding(6.dp))
                            }
                        }
                    }
                }
            }
            if (v.gameOver) GameOver(v, { info(it) }, vm)
        }
        // ---- white bottom bar: action button, my avatar, extras ----
        Row(Modifier.fillMaxWidth().height(84.dp).background(Pal.Bar).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                when {
                    l.canTake -> BarButton("Беру", "btn:take") { vm.act(Action.Take) }
                    l.canPass -> BarButton(if (v.defenderTaking) "Пас" else if (v.table.none { it.defense == null }) "Бито" else "Пас", "btn:pass") { vm.act(Action.Pass) }
                }
            }
            Box {
                PlayerAvatar(info(me), v, me, 68.dp, handCount = v.myHand.size)
                vm.stickers.firstOrNull { it.seat == me }?.let { StickerBubble(it, Modifier.align(Alignment.TopCenter).offset(y = (-70).dp)) }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (l.transfer.isNotEmpty()) {
                    SmallBarButton(if (ui.transferMode) "Выберите\nкарту" else "Перевести", Pal.Orange, "btn:transfer") { ui.transferMode = !ui.transferMode }
                    Spacer(Modifier.width(6.dp))
                }
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(10.dp)).clickable { stickerOpen = !stickerOpen },
                    contentAlignment = Alignment.Center,
                ) { Text("😊", fontSize = 30.sp) }
            }
        }
    }
}

@Composable
private fun BarButton(text: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.testTag(tag).height(64.dp).widthIn(min = 130.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
            .border(1.dp, Color(0xFFD5D5D5), RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Pal.Red, fontSize = 38.sp, fontFamily = Condensed) }
}

@Composable
private fun SmallBarButton(text: String, color: Color, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.testTag(tag).height(56.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.5.dp, color, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}

/** Small white icons on top: game mode at a glance, like the original. */
@Composable
private fun ModeIcons(v: GameView, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val tint = Color(0xCCFFFFFF)
        Icon(if (v.rules.transfer) Icons.Filled.Refresh else Icons.Filled.ArrowForward, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(if (v.rules.neighborsOnly) "соседи" else "все", color = tint, fontSize = 13.sp)
        Text("${v.rules.deckSize}", color = tint, fontSize = 15.sp, fontFamily = Condensed)
        SuitIcon(v.trump, 18.dp, color = if (v.trump == Suit.HEARTS || v.trump == Suit.DIAMONDS) Color(0xFFFF8A8A) else Color.White)
    }
}

private fun hintText(v: GameView, me: Int, info: (Int) -> SeatInfo): String {
    if (v.gameOver) return "Игра окончена"
    val l = v.legal
    return when {
        me in v.finished -> "Вы вышли из игры"
        me == v.defender && v.defenderTaking -> "Вы берёте карты"
        me == v.defender && l.defend.isEmpty() && l.canTake -> "Нечем отбиться — нажмите «Беру»"
        me == v.defender && v.table.any { it.defense == null } -> "Отбивайтесь: положите карту на карту соперника"
        me == v.defender -> "Ждём, подкинут ли ещё"
        v.table.isEmpty() && me == v.attacker -> "Ваш ход"
        v.table.isEmpty() -> "Ходит ${info(v.attacker).name}"
        v.defenderTaking && l.attack.isNotEmpty() -> "${info(v.defender).name} берёт — можно подкинуть"
        l.attack.isNotEmpty() -> "Можно подкинуть"
        else -> "Отбивается ${info(v.defender).name}"
    }
}

/** Avatar in a rounded frame: green when the game waits for this player, red otherwise; name and card count on it. */
@Composable
fun PlayerAvatar(info: SeatInfo, v: GameView, seat: Int, size: Dp, handCount: Int? = null) {
    val waiting = !v.gameOver && seat in v.waitingFor
    Box(Modifier.size(size)) {
        AvatarSquare(info.avatar, size, if (waiting) Pal.FrameGreen else Pal.FrameRed)
        Text(
            info.name, color = Color.White, fontSize = (size.value * 0.2f).sp, maxLines = 1, fontWeight = FontWeight.Bold,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(1f, 1f), 3f)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.06f),
        )
        if (handCount != null) Text("$handCount", color = Color.White, fontSize = (size.value * 0.17f).sp, fontFamily = Condensed,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(1f, 1f), 3f)),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = size * 0.06f, end = size * 0.12f))
        val mark = when {
            v.gameOver -> null
            seat in v.finished -> "✅"
            seat == v.defender && v.defenderTaking -> "🫴"
            seat == v.defender -> "🛡"
            seat == v.attacker -> "⚔"
            else -> null
        }
        if (mark != null) Box(Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-6).dp).size(22.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Text(mark, fontSize = 12.sp)
        }
    }
}

@Composable
fun Opponent(seat: Int, info: SeatInfo, v: GameView, sticker: StickerEvt?, modifier: Modifier = Modifier) {
    val count = v.handSizes[seat]
    Box(modifier.height(104.dp), contentAlignment = Alignment.TopCenter) {
        // fanned backs behind/below the avatar
        if (count > 0) {
            val shown = minOf(count, 6)
            for (i in 0 until shown) {
                val t = i - (shown - 1) / 2f
                CardBack(34.dp, Modifier.offset(x = (t * 9).dp, y = 44.dp + (t * t * 0.8f).dp).rotate(t * 9f))
            }
        }
        PlayerAvatar(info, v, seat, 64.dp, handCount = count)
        if (seat in v.passed && !v.gameOver) Text("пас", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(6.dp)).background(Color(0x66000000)).padding(horizontal = 6.dp))
        sticker?.let { StickerBubble(it, Modifier.align(Alignment.BottomCenter).offset(y = 30.dp).zIndex(5f)) }
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

/** Talon at the left edge: trump card under the plaid deck, big count, as in the original. */
@Composable
fun TalonView(v: GameView, modifier: Modifier = Modifier) {
    Box(modifier.size(96.dp, 130.dp)) {
        val tc = v.trumpCard
        if (tc != null) CardFace(tc, 64.dp, Modifier.align(Alignment.CenterStart).offset(x = (-8).dp, y = 22.dp).rotate(-80f))
        if (v.deckSize > 1) CardBack(64.dp, Modifier.align(Alignment.CenterStart).offset(x = (-40).dp).rotate(4f))
        if (v.deckSize > 0) Text("${v.deckSize}", style = TextStyle(color = Color.White, fontSize = 40.sp, fontFamily = Condensed, shadow = Shadow(Color.Black, Offset(2f, 3f), 6f)), modifier = Modifier.align(Alignment.CenterStart).padding(start = 34.dp))
        else Box(Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(46.dp).clip(CircleShape).background(Color(0x55FFFFFF)), contentAlignment = Alignment.Center) {
            SuitIcon(v.trump, 28.dp)
        }
    }
}

/** Beaten cards ("бито") pile at the right edge. */
@Composable
fun DiscardView(count: Int, modifier: Modifier = Modifier) {
    if (count == 0) return
    Box(modifier.size(70.dp, 150.dp)) {
        val layers = minOf(4, 1 + count / 8)
        for (i in 0 until layers) CardBack(64.dp, Modifier.align(Alignment.CenterStart).offset(x = 26.dp, y = (i * 6 - 10).dp).rotate(-20f + i * 13f))
    }
}

@Composable
fun TableView(
    v: GameView, glow: Set<Int>, pulse: Float,
    onTargetBounds: (Int, Rect) -> Unit, originOf: (Int) -> Offset, modifier: Modifier = Modifier,
) {
    val cw = if (v.table.size > 3) 68.dp else 80.dp
    Column(modifier.padding(start = 40.dp, end = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        v.table.withIndex().toList().chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { (i, p) ->
                    key(i, p.attack) {
                        val from = originOf(p.attackBy)
                        Box(Modifier.size(cw + 22.dp, cw * CARD_RATIO + 22.dp)) {
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
                                    Pop(Modifier.align(Alignment.BottomEnd), fromX = df.x, fromY = df.y) { CardFace(d, cw, Modifier.rotate(4f)) }
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
    onLayout: (List<Pair<Card, Rect>>) -> Unit,
) {
    val l = v.legal
    val anyPlayable = l.attack.isNotEmpty() || l.defend.isNotEmpty() || l.transfer.isNotEmpty()
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(132.dp).onGloballyPositioned { origin = it.positionInRoot() }) {
        val cw = if (cards.size > 9) 90.dp else 104.dp
        val n = cards.size
        val avail = maxWidth - cw - 40.dp
        val step = if (n <= 1) 0.dp else minOf(cw * 0.6f, avail / (n - 1))
        val total = step * (n - 1) + cw
        val startX = (maxWidth - total) / 2
        fun baseY(i: Int, lifted: Boolean): Dp {
            val t = if (n > 1) i / (n - 1f) - 0.5f else 0f
            return 18.dp + (t * t * 20f).dp - (if (lifted) 16.dp else 0.dp)
        }
        // Hit areas of the visible card parts (for the table-wide gesture handler).
        val rects = with(density) {
            cards.mapIndexed { i, c ->
                val x = (startX + step * i).toPx() + origin.x
                val y = (baseY(i, true)).toPx() + origin.y
                c to Rect(x, y, x + cw.toPx(), y + (132.dp + 40.dp).toPx())
            }
        }
        val reportLayout by rememberUpdatedState(onLayout)
        LaunchedEffect(rects) { reportLayout(rects) }
        cards.forEachIndexed { i, c ->
            key(c) {
                val t = if (n > 1) i / (n - 1f) - 0.5f else 0f
                val playable = c in l.attack || l.defend.any { it.card == c } || c in l.transfer
                val dragged = c == dragCard
                val ax by animateDpAsState(startX + step * i, label = "x")
                val ay by animateDpAsState(baseY(i, playable) - (if (c == selected) 22.dp else 0.dp), label = "y")
                Box(Modifier.offset(x = ax, y = ay).zIndex(if (dragged) 10f else i.toFloat()).testTag("hand:$c")) {
                    Pop(fromX = -300f, fromY = -420f, delayMs = (i.coerceAtMost(8) * 70).toLong()) {
                        CardFace(
                            c, cw,
                            Modifier.graphicsLayer {
                                if (dragged) { translationX = dragOffset.x; translationY = dragOffset.y; scaleX = 0.8f; scaleY = 0.8f }
                                else rotationZ = t * (if (n > 8) 8f else 6f)
                                transformOrigin = TransformOrigin(0.5f, 1.8f)
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
            Column(Modifier.padding(24.dp).clip(RoundedCornerShape(22.dp)).background(Pal.Felt).border(2.dp, Color.White, RoundedCornerShape(22.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v.loser == null) "🤝" else if (iLost) "🤡" else "🏆", fontSize = 64.sp)
                Text(
                    when { v.loser == null -> "Ничья!"; iLost -> "Вы дурак!"; else -> "Дурак — ${info(v.loser!!).name}" },
                    style = scriptStyle(34), textAlign = TextAlign.Center,
                )
                Text(if (v.loser == null) "" else if (iLost) "−${vm.stake}" else "+${vm.stake}", color = Pal.Gold, fontSize = 22.sp, fontFamily = Condensed)
                Spacer(Modifier.height(16.dp))
                if (vm.isHost) BigButton("Играть ещё", { vm.playAgain() }, Modifier.fillMaxWidth(), Pal.Red)
                else Text("Ждём, пока хозяин начнёт новую игру…", color = Color(0xDDFFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                BigButton("В меню", { vm.leaveToMenu() }, Modifier.fillMaxWidth(), Color(0x55FFFFFF))
            }
        }
    }
}

/** Interaction state of the game table. Handlers read the current state from [vm] at call time (never a stale snapshot). */
class GameUi(private val vm: AppVm) {
    var selected by mutableStateOf<Card?>(null)
    var dragCard by mutableStateOf<Card?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)
    var transferMode by mutableStateOf(false)
    val targets = mutableStateMapOf<Int, Rect>()
    var handRects: List<Pair<Card, Rect>> = emptyList()
    var tableZone: Rect = Rect.Zero
    /** Root position of the felt; gesture positions are local to it. */
    var feltOrigin: Offset = Offset.Zero

    private fun name(seat: Int) = vm.seats.getOrNull(seat)?.name ?: "?"

    fun handHit(p: Offset): Card? = handRects.lastOrNull { it.second.contains(p) }?.first

    private fun defendWith(v: GameView, c: Card, index: Int?): Boolean {
        val opts = v.legal.defend.filter { it.card == c }
        val pick = (if (index != null) opts.firstOrNull { it.index == index } else null) ?: opts.singleOrNull()
        if (pick != null) { vm.act(Action.Defend(pick.index, pick.card)); selected = null; return true }
        return false
    }

    fun tapHand(c: Card) {
        val v = vm.view ?: return
        if (v.gameOver) return
        val l = v.legal
        if (v.seat == v.defender) {
            if (c in l.transfer && (transferMode || l.defend.none { it.card == c })) { vm.act(Action.Transfer(c)); transferMode = false; return }
            val opts = l.defend.filter { it.card == c }
            when {
                opts.isEmpty() -> vm.flash(if (l.defend.isEmpty() && l.canTake) "Отбиться нечем — нажмите «Беру»" else "Эта карта не бьёт")
                opts.size == 1 -> defendWith(v, c, opts[0].index)
                else -> selected = if (selected == c) null else c
            }
        } else {
            if (c in l.attack) vm.act(Action.Attack(c))
            else vm.flash(
                when {
                    v.table.isEmpty() && v.seat != v.attacker -> "Сейчас ходит ${name(v.attacker)}"
                    v.table.isEmpty() -> "Ходите любой картой"
                    else -> "Подкинуть можно карту того же достоинства, что на столе"
                }
            )
        }
    }

    fun dropHand(c: Card, p: Offset) {
        val v = vm.view ?: return
        if (v.gameOver) return
        val l = v.legal
        val inTable = tableZone.contains(p)
        if (v.seat == v.defender) {
            val hit = targets.entries
                .filter { (i, r) -> r.inflate(40f).contains(p) && l.defend.any { it.index == i && it.card == c } }
                .minByOrNull { (it.value.center - p).getDistance() }?.key
            if (hit != null) { defendWith(v, c, hit); return }
            if (!inTable) return
            if (c in l.transfer && (transferMode || l.defend.none { it.card == c })) { vm.act(Action.Transfer(c)); transferMode = false; return }
            if (!defendWith(v, c, null)) {
                if (l.defend.any { it.card == c }) selected = c else vm.flash("Эта карта не бьёт")
            }
        } else if (inTable) tapHand(c)
    }

    fun tapTable(p: Offset) {
        val v = vm.view ?: return
        val c = selected ?: return
        val hit = targets.entries.firstOrNull { it.value.inflate(16f).contains(p) }?.key ?: return
        if (!defendWith(v, c, hit)) vm.flash("Этой картой эту не побить")
    }
}
