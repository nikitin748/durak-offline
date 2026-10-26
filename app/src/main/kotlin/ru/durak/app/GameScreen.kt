package ru.durak.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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

    var selected by remember { mutableStateOf<Card?>(null) }   // card picked from hand (when it fits several targets)
    var dragging by remember { mutableStateOf<Card?>(null) }
    var transferMode by remember { mutableStateOf(false) }
    val targets = remember { mutableStateMapOf<Int, Rect>() }  // attack cards on the table, in root coordinates
    var tableZone by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(v.table.size, v.defender, v.turnNumber, v.table.count { it.defense != null }) { selected = null; transferMode = false }
    LaunchedEffect(v.table.size) { targets.keys.filter { it >= v.table.size }.forEach { targets.remove(it) } }

    fun info(seat: Int): SeatInfo = vm.seats.getOrNull(seat) ?: SeatInfo("?", 0, SeatKind.BOT)
    fun targetsFor(c: Card?) = if (c == null || !iAmDefender) emptySet() else l.defend.filter { it.card == c }.map { it.index }.toSet()

    fun defendWith(c: Card, index: Int?): Boolean {
        val opts = l.defend.filter { it.card == c }
        val pick = (if (index != null) opts.firstOrNull { it.index == index } else null) ?: opts.singleOrNull()
        if (pick != null) { vm.act(Action.Defend(pick.index, pick.card)); selected = null; return true }
        return false
    }

    // Tap on a card in hand.
    fun tapHand(c: Card) {
        if (v.gameOver) return
        if (iAmDefender) {
            if (c in l.transfer && (transferMode || l.defend.none { it.card == c })) { vm.act(Action.Transfer(c)); transferMode = false; return }
            val opts = l.defend.filter { it.card == c }
            when {
                opts.isEmpty() -> vm.flash(if (l.defend.isEmpty() && l.canTake) "Отбиться нечем — нажмите «Беру»" else "Эта карта не бьёт")
                opts.size == 1 -> defendWith(c, opts[0].index)
                else -> { selected = if (selected == c) null else c; vm.flash("Нажмите на карту, которую бьёте") }
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

    // Drop of a dragged card at [p] (root coordinates).
    fun dropHand(c: Card, p: Offset) {
        if (v.gameOver) return
        val inTable = tableZone.contains(p)
        if (iAmDefender) {
            val hit = targets.entries.firstOrNull { (i, r) -> r.inflate(28f).contains(p) && l.defend.any { it.index == i && it.card == c } }?.key
            if (hit != null) { defendWith(c, hit); return }
            if (!inTable) return
            if (c in l.transfer && (transferMode || l.defend.none { it.card == c })) { vm.act(Action.Transfer(c)); transferMode = false; return }
            if (!defendWith(c, null)) {
                if (l.defend.any { it.card == c }) { selected = c; vm.flash("Положите карту на ту, которую бьёте") } else vm.flash("Эта карта не бьёт")
            }
        } else if (inTable) tapHand(c)
    }

    fun tapTarget(i: Int) {
        val c = selected ?: return
        if (!defendWith(c, i)) vm.flash("Этой картой эту не побить")
    }

    val glowSet = targetsFor(dragging ?: selected)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "p")
    val density = LocalDensity.current
    // Where cards fly in from: my seat is at the bottom, opponents along the top.
    fun originOf(seat: Int): Offset {
        if (seat == me || seat < 0) return Offset(0f, with(density) { 360.dp.toPx() })
        val j = others.indexOf(seat).coerceAtLeast(0)
        val k = others.size.coerceAtLeast(1)
        return Offset(((j + 0.5f) / k - 0.5f) * with(density) { 340.dp.toPx() }, -with(density) { 300.dp.toPx() })
    }

    Felt {
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
                TalonView(v, Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
                TableView(
                    v, glowSet, pulse, iAmDefender && selected != null, ::tapTarget,
                    { i, r -> targets[i] = r }, ::originOf, Modifier.align(Alignment.Center),
                )
                Text(
                    hintText(v, me, ::info), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0x66000000)).padding(horizontal = 12.dp, vertical = 4.dp),
                )
                // action buttons, bottom right like the original
                Column(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 6.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (l.transfer.isNotEmpty()) ActionPill(if (transferMode) "Выберите карту" else "Перевести", Pal.Orange) { transferMode = !transferMode }
                    if (l.canTake) ActionPill("Беру", Color(0xFFD9534F)) { vm.act(Action.Take) }
                    else if (l.canPass) ActionPill(if (v.defenderTaking) "Пас" else if (v.table.none { it.defense == null }) "Бито" else "Пас", Pal.Green) { vm.act(Action.Pass) }
                }
                vm.toast?.let { Toast(it) }
            }
            // ---- my hand ----
            HandView(
                v, handSorted(v.myHand, v.trump), selected, transferMode, ::tapHand, ::dropHand,
                onDragChange = { dragging = it },
            )
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
fun ActionPill(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.95f), color))).border(2.dp, Color(0x99FFFFFF), RoundedCornerShape(24.dp))
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
    v: GameView, glow: Set<Int>, pulse: Float, tapTargets: Boolean, onTapTarget: (Int) -> Unit,
    onTargetBounds: (Int, Rect) -> Unit, originOf: (Int) -> Offset, modifier: Modifier = Modifier,
) {
    val cw = if (v.table.size > 3) 56.dp else 64.dp
    Column(modifier.padding(start = 76.dp, end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        v.table.withIndex().toList().chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (i, p) ->
                    key(i, p.attack) {
                        val from = originOf(p.attackBy)
                        Box(Modifier.size(cw + 18.dp, cw * CARD_RATIO + 20.dp)) {
                            Pop(Modifier.align(Alignment.TopStart), fromX = from.x, fromY = from.y) {
                                CardFace(
                                    p.attack, cw,
                                    Modifier
                                        .onGloballyPositioned { onTargetBounds(i, it.boundsInRoot()) }
                                        .rotate(-4f)
                                        .then(if (tapTargets && p.defense == null) Modifier.pointerInput(i) { detectTapGestures { onTapTarget(i) } } else Modifier),
                                    glow = if (i in glow && p.defense == null) pulse else 0f,
                                )
                            }
                            p.defense?.let { d ->
                                key(d) {
                                    val df = originOf(v.defender)
                                    Pop(Modifier.align(Alignment.BottomEnd), fromX = df.x, fromY = df.y) { CardFace(d, cw, Modifier.rotate(7f)) }
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
    v: GameView, cards: List<Card>, selected: Card?, transferMode: Boolean,
    onTap: (Card) -> Unit, onDrop: (Card, Offset) -> Unit, onDragChange: (Card?) -> Unit,
) {
    val l = v.legal
    val anyPlayable = l.attack.isNotEmpty() || l.defend.isNotEmpty() || l.transfer.isNotEmpty()
    BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp)) {
        val cw = 74.dp
        val n = cards.size
        val avail = maxWidth - cw - 12.dp
        val step = if (n <= 1) 0.dp else minOf(cw * 0.66f, avail / (n - 1))
        val total = step * (n - 1) + cw
        val startX = (maxWidth - total) / 2
        cards.forEachIndexed { i, c ->
            key(c) {
                val t = if (n > 1) i / (n - 1f) - 0.5f else 0f
                val playable = c in l.attack || l.defend.any { it.card == c } || c in l.transfer
                HandCard(
                    c, cw, x = startX + step * i, y = 24.dp + (t * t * 30f).dp - (if (c == selected) 26.dp else if (playable) 10.dp else 0.dp),
                    rot = t * (if (n > 8) 22f else 16f), dim = anyPlayable && !playable,
                    glow = if (c == selected || (transferMode && c in l.transfer)) 1f else 0f,
                    enterDelay = (i.coerceAtMost(8) * 70).toLong(),
                    onTap = onTap, onDrop = onDrop, onDragChange = onDragChange,
                )
            }
        }
    }
}

@Composable
private fun HandCard(
    c: Card, cw: Dp, x: Dp, y: Dp, rot: Float, dim: Boolean, glow: Float, enterDelay: Long,
    onTap: (Card) -> Unit, onDrop: (Card, Offset) -> Unit, onDragChange: (Card?) -> Unit,
) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val tap by rememberUpdatedState(onTap)
    val drop by rememberUpdatedState(onDrop)
    val dragCb by rememberUpdatedState(onDragChange)
    val ax by animateDpAsState(x, label = "x")
    val ay by animateDpAsState(y, label = "y")
    Box(
        Modifier
            .offset(x = ax, y = ay)
            .zIndex(if (dragging) 10f else 0f)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer {
                translationX = drag.x; translationY = drag.y
                rotationZ = if (dragging) 0f else rot
                transformOrigin = TransformOrigin(0.5f, 2.4f)
                val s = if (dragging) 1.08f else 1f
                scaleX = s; scaleY = s
            }
            .pointerInput(c) { detectTapGestures { tap(c) } }
            .pointerInput(c) {
                detectDragGestures(
                    onDragStart = { dragging = true; dragCb(c) },
                    onDrag = { change, amount -> change.consume(); drag += amount },
                    onDragEnd = {
                        val p = bounds.center + drag
                        dragging = false; drag = Offset.Zero; dragCb(null)
                        drop(c, p)
                    },
                    onDragCancel = { dragging = false; drag = Offset.Zero; dragCb(null) },
                )
            }
    ) {
        Pop(fromX = -300f, fromY = -420f, delayMs = enterDelay) {
            CardFace(c, cw, dim = dim && !dragging, glow = glow)
        }
    }
}

@Composable
fun BoxScope.GameOver(v: GameView, info: (Int) -> SeatInfo, vm: AppVm) {
    val iLost = v.loser == v.seat
    Box(Modifier.matchParentSize().background(Color(0xAA000000)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
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
