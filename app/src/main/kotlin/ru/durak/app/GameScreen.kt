package ru.durak.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.durak.engine.*
import ru.durak.net.SeatInfo
import ru.durak.net.SeatKind

private fun handSorted(hand: List<Card>, trump: Suit) =
    hand.sortedWith(compareBy<Card> { if (it.suit == trump) 1 else 0 }.thenBy { it.suit.ordinal }.thenBy { it.rank })

@Composable
fun GameScreen(vm: AppVm) {
    val v = vm.view ?: return
    var selectedTarget by remember { mutableStateOf<Int?>(null) }
    var transferMode by remember { mutableStateOf(false) }
    var stickerOpen by remember { mutableStateOf(false) }
    // Reset local selections whenever the table changes.
    LaunchedEffect(v.table.size, v.defender, v.turnNumber) { selectedTarget = null; transferMode = false }

    val me = v.seat
    val n = v.playerCount
    val others = (1 until n).map { (me + it) % n }
    val iAmDefender = me == v.defender
    val l = v.legal

    fun info(seat: Int): SeatInfo = vm.seats.getOrNull(seat) ?: SeatInfo("?", 0, SeatKind.BOT)

    fun tapHand(c: Card) {
        if (v.gameOver) return
        if (iAmDefender) {
            if (transferMode && c in l.transfer) { vm.act(Action.Transfer(c)); transferMode = false; return }
            val opts = l.defend.filter { it.card == c }
            val pick = opts.firstOrNull { it.index == selectedTarget } ?: opts.firstOrNull()
            if (pick != null) vm.act(Action.Defend(pick.index, pick.card)) else vm.flash("Эта карта не бьёт")
        } else {
            if (c in l.attack) vm.act(Action.Attack(c))
            else vm.flash(if (v.table.isEmpty()) (if (me == v.attacker) "Ходить можно любой картой" else "Сейчас ходит ${info(v.attacker).name}") else "Подкинуть можно только карту того же достоинства")
        }
    }

    Felt {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // ---- top: opponents + coins ----
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    others.forEach { s ->
                        Opponent(s, info(s), v, vm.stickers.firstOrNull { it.seat == s }, Modifier.weight(1f))
                    }
                }
                CoinChip(vm.coins, Modifier.padding(start = 4.dp, top = 2.dp))
            }
            // ---- middle: talon + table ----
            Box(Modifier.weight(1f).fillMaxWidth()) {
                TalonView(v, Modifier.align(Alignment.CenterStart).padding(start = 10.dp))
                TableView(v, selectedTarget, iAmDefender, { selectedTarget = if (selectedTarget == it) null else it }, Modifier.align(Alignment.Center))
                val hint = hintText(v, me, ::info)
                Text(hint, color = Color(0xE6FFFFFF), fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x55000000)).padding(horizontal = 10.dp, vertical = 3.dp))
                vm.toast?.let { Toast(it) }
            }
            // ---- bottom: my hand + controls ----
            HandView(v, handSorted(v.myHand, v.trump), transferMode, ::tapHand)
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    val st = vm.stickers.firstOrNull { it.seat == me }
                    Avatar(info(me).avatar, 46.dp, ring = roleRing(v, me))
                    st?.let { StickerBubble(it, Modifier.align(Alignment.TopCenter).offset(y = (-44).dp)) }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(info(me).name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 14.sp)
                    Text(roleLabel(v, me) ?: "", color = Pal.Gold, fontSize = 12.sp)
                }
                if (l.transfer.isNotEmpty()) {
                    ActionPill(if (transferMode) "Выберите карту" else "Перевести", Pal.Orange) { transferMode = !transferMode }
                    Spacer(Modifier.width(8.dp))
                }
                if (l.canTake) ActionPill("Беру", Color(0xFFD9534F)) { vm.act(Action.Take) }
                else if (l.canPass) ActionPill(if (v.table.none { it.defense == null }) "Бито" else "Пас", Pal.Green) { vm.act(Action.Pass) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color(0x55000000)).clickable { stickerOpen = !stickerOpen },
                    contentAlignment = Alignment.Center,
                ) { Text("😊", fontSize = 24.sp) }
            }
        }
        // sticker picker
        AnimatedVisibility(stickerOpen, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Pal.Panel).padding(10.dp)) {
                STICKERS.chunked(6).forEachIndexed { r, row ->
                    Row {
                        row.forEachIndexed { c, e ->
                            Text(e, fontSize = 30.sp, modifier = Modifier.clickable { vm.sendSticker(r * 6 + c); stickerOpen = false }.padding(6.dp))
                        }
                    }
                }
            }
        }
        if (v.gameOver) GameOver(v, ::info, vm)
    }
}

private fun roleLabel(v: GameView, seat: Int): String? = when {
    seat in v.finished -> "вышел"
    seat == v.defender && v.defenderTaking -> "берёт"
    seat == v.defender -> "отбивается"
    seat == v.attacker && v.table.isNotEmpty() -> "ходит"
    seat == v.attacker -> "ходит"
    seat in v.passed -> "пас"
    else -> null
}

private fun roleRing(v: GameView, seat: Int): Color? = when {
    v.gameOver -> null
    seat == v.defender -> Pal.Green
    seat == v.attacker -> Pal.Orange
    else -> null
}

private fun hintText(v: GameView, me: Int, info: (Int) -> SeatInfo): String {
    if (v.gameOver) return ""
    val l = v.legal
    return when {
        me == v.defender && v.defenderTaking -> "Вы берёте карты…"
        me == v.defender && v.table.any { it.defense == null } -> "Отбейтесь или возьмите"
        me == v.defender -> "Ждём подкидывающих"
        v.table.isEmpty() && me == v.attacker -> "Ваш ход"
        v.table.isEmpty() -> "Ходит ${info(v.attacker).name}"
        l.attack.isNotEmpty() -> "Можно подкинуть"
        else -> "Ждём ${info(v.defender).name}"
    }
}

@Composable
fun ActionPill(text: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(22.dp)).background(color).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp)) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun Opponent(seat: Int, info: SeatInfo, v: GameView, sticker: StickerEvt?, modifier: Modifier = Modifier) {
    val count = v.handSizes[seat]
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Avatar(info.avatar, 46.dp, ring = roleRing(v, seat))
            if (seat in v.finished) Text("✓", color = Pal.Green, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
            sticker?.let { StickerBubble(it, Modifier.align(Alignment.BottomCenter).offset(y = 40.dp)) }
        }
        Text(info.name, color = Color.White, fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Medium)
        Box(Modifier.height(54.dp), contentAlignment = Alignment.TopCenter) {
            if (count > 0) {
                val shown = minOf(count, 5)
                for (i in 0 until shown) {
                    CardBack(26.dp, Modifier.offset(x = ((i - (shown - 1) / 2f) * 7).dp).rotate((i - (shown - 1) / 2f) * 6f))
                }
                Badge("$count", Modifier.align(Alignment.BottomEnd))
            }
        }
        roleLabel(v, seat)?.let { Text(it, color = Pal.Gold, fontSize = 10.sp) }
    }
}

@Composable
fun StickerBubble(s: StickerEvt, modifier: Modifier = Modifier) {
    Pop(modifier) {
        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(STICKERS[s.id.mod(STICKERS.size)], fontSize = 34.sp)
        }
    }
}

@Composable
fun TalonView(v: GameView, modifier: Modifier = Modifier) {
    Box(modifier.size(78.dp, 110.dp)) {
        val tc = v.trumpCard
        if (tc != null) CardFace(tc, 52.dp, Modifier.align(Alignment.CenterStart).offset(x = 22.dp).rotate(90f))
        if (v.deckSize > 0) {
            CardBack(52.dp, Modifier.align(Alignment.CenterStart))
            Badge("${v.deckSize}", Modifier.align(Alignment.TopStart).offset(x = 2.dp, y = 2.dp), Color(0xFF244E92))
        } else {
            // talon is gone: just show the trump suit
            Text(v.trump.symbol, color = suitColor(v.trump).let { if (it == Pal.Ink) Color.White else Color(0xFFFF6B6B) }, fontSize = 46.sp, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun TableView(v: GameView, selected: Int?, iAmDefender: Boolean, onTapAttack: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cw = 58.dp
    Column(modifier.padding(start = 70.dp, end = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        v.table.withIndex().toList().chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (i, p) ->
                    key(i, p.attack) {
                        Box(Modifier.size(cw + 18.dp, cw * CARD_RATIO + 12.dp)) {
                            Pop(Modifier.align(Alignment.TopStart)) {
                                CardFace(
                                    p.attack, cw,
                                    Modifier.then(if (iAmDefender && p.defense == null) Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTapAttack(i) } else Modifier),
                                    glow = selected == i && p.defense == null,
                                )
                            }
                            p.defense?.let { d ->
                                key(d) { Pop(Modifier.align(Alignment.BottomEnd)) { CardFace(d, cw) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HandView(v: GameView, cards: List<Card>, transferMode: Boolean, onTap: (Card) -> Unit) {
    val l = v.legal
    BoxWithConstraints(Modifier.fillMaxWidth().height(138.dp)) {
        val cw = 68.dp
        val n = cards.size
        val avail = maxWidth - cw - 16.dp
        val step = if (n <= 1) 0.dp else minOf(cw * 0.72f, avail / (n - 1))
        val total = step * (n - 1) + cw
        val startX = (maxWidth - total) / 2
        cards.forEachIndexed { i, c ->
            key(c) {
                val t = if (n > 1) i / (n - 1f) - 0.5f else 0f
                val playable = c in l.attack || l.defend.any { it.card == c } || (transferMode && c in l.transfer)
                val lift = if (playable) (-12).dp else 0.dp
                CardFace(
                    c, cw,
                    Modifier
                        .offset(x = startX + step * i, y = 22.dp + (t * t * 26f).dp + lift)
                        .graphicsLayer { rotationZ = t * 16f; transformOrigin = TransformOrigin(0.5f, 2.2f) }
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTap(c) },
                    dim = !playable && (l.attack.isNotEmpty() || l.defend.isNotEmpty() || l.transfer.isNotEmpty()),
                    glow = transferMode && c in l.transfer,
                )
            }
        }
    }
}

@Composable
fun BoxScope.GameOver(v: GameView, info: (Int) -> SeatInfo, vm: AppVm) {
    val iLost = v.loser == v.seat
    Box(Modifier.matchParentSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.clip(RoundedCornerShape(22.dp)).background(Pal.Panel).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (v.loser == null) "🤝" else if (iLost) "🤡" else "🏆", fontSize = 60.sp)
            Text(
                when { v.loser == null -> "Ничья!"; iLost -> "Вы дурак!"; else -> "Дурак — ${info(v.loser!!).name}" },
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
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
