package ru.durak.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.durak.engine.Deck
import ru.durak.engine.Suit
import ru.durak.net.SeatInfo
import ru.durak.net.SeatKind
import ru.durak.net.TCP_PORT

// ---------------------------------------------------------------- shared bits

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Pal.ButtonBlue, enabled: Boolean = true) {
    Box(
        modifier.clip(RoundedCornerShape(26.dp)).background(if (enabled) color else Color(0xFF6B7F94))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}

@Composable
fun CoinChip(coins: Int, modifier: Modifier = Modifier, dark: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("$coins", color = if (dark) Pal.Red else Color.White, fontSize = 26.sp, fontFamily = Condensed)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(26.dp, 18.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF9CCB6B)).border(1.dp, Color(0xFF5E9A3A), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(9.dp).clip(CircleShape).border(1.5.dp, Color(0xFF5E9A3A), CircleShape))
        }
    }
}

@Composable
fun BoxScope.Toast(text: String) {
    Box(Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xDD000000)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun TitleBar(title: String, onBack: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().background(Pal.Bar).padding(vertical = 12.dp)) {
        if (onBack != null) Icon(Icons.Filled.ArrowBack, null, tint = Pal.BarText, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(28.dp).clickable(onClick = onBack))
        Text(title, color = Pal.BarText, fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun SmallBtn(text: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(Pal.ButtonBlue).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- home with tabs

@Composable
fun HomeScreen(vm: AppVm) {
    Column(Modifier.fillMaxSize().background(Pal.Bar).systemBarsPadding()) {
        Felt(Modifier.weight(1f).fillMaxWidth()) {
            when (vm.tab) {
                Tab.PROFILE -> ProfileTab(vm)
                Tab.FIND -> FindTab(vm)
                Tab.RULES -> RulesTab()
                Tab.CREATE -> CreateTab(vm)
            }
            vm.toast?.let { Toast(it) }
        }
        TabBar(vm)
    }
}

@Composable
private fun TabBar(vm: AppVm) {
    Row(Modifier.fillMaxWidth().background(Pal.Bar).padding(top = 6.dp, bottom = 4.dp)) {
        TabItem("Профиль", Suit.CLUBS, Icons.Filled.Person, vm.tab == Tab.PROFILE, Modifier.weight(1f)) { vm.tab = Tab.PROFILE }
        TabItem("Найти игру", Suit.HEARTS, Icons.Filled.Search, vm.tab == Tab.FIND, Modifier.weight(1f)) { vm.tab = Tab.FIND }
        TabItem("Правила", Suit.SPADES, Icons.Filled.Info, vm.tab == Tab.RULES, Modifier.weight(1f)) { vm.tab = Tab.RULES }
        TabItem("Создать игру", Suit.DIAMONDS, Icons.Filled.Add, vm.tab == Tab.CREATE, Modifier.weight(1f).testTag("tab:create")) { vm.tab = Tab.CREATE }
    }
}

@Composable
private fun TabItem(label: String, suit: Suit, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shapeColor = when {
        suit == Suit.DIAMONDS -> if (selected) Pal.Red else Color(0xFFF08A8F)
        suit == Suit.HEARTS -> if (selected) Pal.Red else Color(0xFFF2A0A6)
        selected -> Color(0xFF3A3A3A)
        else -> Color(0xFFAAAAAA)
    }
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            SuitIcon(suit, 44.dp, color = shapeColor)
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp).offset(y = if (suit == Suit.HEARTS || suit == Suit.DIAMONDS) 0.dp else (-3).dp))
        }
        Text(label, color = if (selected) Pal.Red else Color(0xFF6E6E6E), fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun ProfileTab(vm: AppVm) {
    var editing by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        // white header like the original profile
        Column(Modifier.fillMaxWidth().background(Pal.Bar)) {
            Text(vm.name, color = Pal.BarText, fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE0E0E0)))
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { editing = true }) {
                    AvatarSquare(vm.avatar, 92.dp, null)
                    Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp).clip(CircleShape).background(Color(0x88000000)).padding(3.dp))
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    CoinChip(vm.coins, dark = true)
                    Text("игр: ${vm.statGames}  ·  дурак: ${vm.statFool}", color = Pal.BarText, fontSize = 13.sp)
                }
            }
        }
        // tile grid
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            MenuTile("Быстрая игра", Icons.Filled.PlayArrow, Modifier.fillMaxWidth().testTag("menu:quick")) { vm.quickGame() }
            TileRow(
                { MenuTile("Создать игру", Icons.Filled.Add, it) { vm.tab = Tab.CREATE } },
                { MenuTile("Найти игру", Icons.Filled.Search, it) { vm.tab = Tab.FIND } },
            )
            TileRow(
                { MenuTile("Статистика", Icons.Filled.Star, it, badge = if (vm.statGames > 0) "${vm.statGames - vm.statFool}/${vm.statGames}" else null) { vm.flash("Игр: ${vm.statGames}, побед: ${vm.statGames - vm.statFool - vm.statDraw}, ничьих: ${vm.statDraw}, дураком: ${vm.statFool}") } },
                { MenuTile("Настройки", Icons.Filled.Settings, it) { editing = true } },
            )
            TileRow(
                { MenuTile("Правила", Icons.Filled.Info, it) { vm.tab = Tab.RULES } },
                { MenuTile("Поделиться", Icons.Filled.Share, it) { vm.shareApk(ctx) } },
            )
        }
    }
    if (editing) ProfileEditor(vm) { editing = false }
}

@Composable
private fun TileRow(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 10.dp).background(Pal.FeltLine))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            a(Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(Pal.FeltLine))
            b(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MenuTile(title: String, icon: ImageVector, modifier: Modifier, badge: String? = null, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
            if (badge != null) Text(" $badge", color = Color.White, fontSize = 20.sp)
        }
        Text(title, style = scriptStyle(30))
    }
}

@Composable
fun AvatarSquare(index: Int, size: Dp, frame: Color?, modifier: Modifier = Modifier) {
    val i = index.mod(AVATARS.size)
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.12f)).background(frame ?: Color.Transparent).padding(if (frame != null) size * 0.06f else 0.dp)
            .clip(RoundedCornerShape(size * 0.08f)).background(AVATAR_BG[i]),
        contentAlignment = Alignment.Center,
    ) { Text(AVATARS[i], fontSize = (size.value * 0.58f).sp) }
}

@Composable
private fun ProfileEditor(vm: AppVm, onClose: () -> Unit) {
    var name by remember { mutableStateOf(vm.name) }
    var avatar by remember { mutableIntStateOf(vm.avatar) }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(20.dp).clip(RoundedCornerShape(18.dp)).background(Pal.Bar).padding(18.dp)) {
            Text("Ваш профиль", color = Pal.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = name, onValueChange = { name = it.take(14) }, singleLine = true, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            AVATARS.indices.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { i -> AvatarSquare(i, 58.dp, if (i == avatar) Pal.FrameRed else null, Modifier.padding(4.dp).clickable { avatar = i }) }
                }
            }
            Spacer(Modifier.height(12.dp))
            BigButton("Сохранить", { vm.setProfile(name.ifBlank { "Игрок" }, avatar); onClose() }, Modifier.fillMaxWidth(), Pal.Red)
        }
    }
}

// ---------------------------------------------------------------- create game (like the original)

@Composable
private fun CreateTab(vm: AppVm) {
    val maxP = Deck.maxPlayers(vm.setDeck)
    Column(Modifier.fillMaxSize()) {
        TitleBar("Создать игру")
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ваша ставка", style = scriptStyle(28), modifier = Modifier.weight(1f))
                CoinChip(vm.setStake)
            }
            val stakes = listOf(10, 50, 100, 500, 1000, 5000)
            Slider(
                value = stakes.indexOf(vm.setStake).coerceAtLeast(0).toFloat(), onValueChange = { vm.setStake = stakes[it.toInt().coerceIn(0, stakes.size - 1)] },
                valueRange = 0f..(stakes.size - 1).toFloat(), steps = stakes.size - 2,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0x88FFFFFF), inactiveTrackColor = Color(0x33FFFFFF), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { stakes.forEach { Text(if (it >= 1000) "${it / 1000}K" else "$it", color = Color(0xCCFFFFFF), fontSize = 13.sp) } }

            Heading("Игроки")
            Segmented((2..6).map { "$it" }, vm.setPlayers - 2, enabled = { it + 2 <= maxP }) { vm.setPlayers = it + 2 }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1.4f)) {
                    Heading("Колода")
                    Segmented(listOf("24", "36", "52"), listOf(24, 36, 52).indexOf(vm.setDeck)) {
                        vm.setDeck = listOf(24, 36, 52)[it]; vm.setPlayers = vm.setPlayers.coerceAtMost(Deck.maxPlayers(vm.setDeck))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Heading("Скорость")
                    Segmented(listOf("▶", "▶▶"), if (vm.setFast) 1 else 0) { vm.setFast = it == 1 }
                }
            }
            Heading("Режимы игры")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeTile("подкидной", Icons.Filled.ArrowForward, "переводной", Icons.Filled.Refresh, !vm.setTransfer, Modifier.weight(1f)) { vm.setTransfer = !it }
                ModeTile("соседи", Icons.Filled.KeyboardArrowRight, "все", Icons.Filled.Add, vm.setNeighbors, Modifier.weight(1f)) { vm.setNeighbors = it }
                ModeTile("с людьми", Icons.Filled.Person, "только боты", Icons.Filled.Face, !vm.setBotsOnly, Modifier.weight(1f).testTag("mode:bots")) { vm.setBotsOnly = !it }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (vm.setBotsOnly) "Игра на этом телефоне против ботов."
                else "Вы создаёте комнату. Друзья в той же Wi‑Fi сети или на вашей точке доступа нажимают «Найти игру». Пустые места займут боты.",
                color = Color(0xDDFFFFFF), fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.align(Alignment.End).clip(RoundedCornerShape(26.dp)).background(Color(0x40FFFFFF)).clickable { vm.createGame() }
                    .padding(horizontal = 26.dp, vertical = 8.dp).testTag("btn:create"),
            ) { Text("Создать  ▶", style = scriptStyle(30)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = scriptStyle(30), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun Segmented(items: List<String>, selected: Int, enabled: (Int) -> Boolean = { true }, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(Color(0x33FFFFFF))) {
        items.forEachIndexed { i, label ->
            val sel = i == selected
            val en = enabled(i)
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(25.dp)).background(if (sel) Color.White else Color.Transparent)
                    .clickable(enabled = en) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = scriptStyle(28, if (sel) Pal.Red else if (en) Color.White else Color(0x55FFFFFF)).copy(shadow = null))
                    if (sel) Icon(Icons.Filled.Check, null, tint = Pal.Red, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun ModeTile(top: String, topIcon: ImageVector, bottom: String, bottomIcon: ImageVector, topSelected: Boolean, modifier: Modifier, onSelect: (Boolean) -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x33FFFFFF))) {
        ModeHalf(top, topIcon, topSelected) { onSelect(true) }
        ModeHalf(bottom, bottomIcon, !topSelected) { onSelect(false) }
    }
}

@Composable
private fun ModeHalf(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) Color.White else Color.Transparent).clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (selected) Color(0xFF3E6596) else Color.White, modifier = Modifier.size(40.dp))
            Text(label, color = if (selected) Pal.Red else Color.White, fontSize = 13.sp, maxLines = 1)
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = Pal.Red, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp))
    }
}

// ---------------------------------------------------------------- find a room

@Composable
private fun FindTab(vm: AppVm) {
    var ip by remember { mutableStateOf("") }
    DisposableEffect(Unit) { vm.startListening(); onDispose { vm.stopListening() } }
    Column(Modifier.fillMaxSize()) {
        TitleBar("Найти игру")
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Открытые комнаты", style = scriptStyle(28))
            Text("Подключитесь к той же Wi‑Fi сети или к точке доступа хозяина комнаты. Интернет не нужен.", color = Color(0xDDFFFFFF), fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            if (vm.rooms.isEmpty()) Text(if (vm.connecting) "Подключаемся…" else "Ищем комнаты рядом…", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(vertical = 12.dp))
            vm.rooms.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF2FFFFFF)).clickable { vm.joinRoom(r.host, r.port) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SuitIcon(Suit.HEARTS, 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, color = Pal.Ink, fontWeight = FontWeight.Bold)
                        Text("игроков: ${r.players} из ${r.seats}", color = Pal.BarText, fontSize = 12.sp)
                    }
                    Text("Войти", color = Pal.Red, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("По адресу", style = scriptStyle(26))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it.trim() }, singleLine = true, label = { Text("например 192.168.43.1") }, modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color.White, unfocusedLabelColor = Color(0xBBFFFFFF), focusedBorderColor = Color.White, unfocusedBorderColor = Color(0x88FFFFFF)),
                )
                Spacer(Modifier.width(8.dp))
                BigButton("ОК", { if (ip.isNotBlank()) vm.joinRoom(ip, TCP_PORT) }, enabled = ip.isNotBlank(), color = Pal.Red)
            }
        }
    }
}

// ---------------------------------------------------------------- rules

@Composable
private fun RulesTab() {
    Column(Modifier.fillMaxSize()) {
        TitleBar("Правила")
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            listOf(
                "Цель" to "Избавиться от всех карт. Кто остался с картами последним — дурак.",
                "Раздача" to "Каждому по 6 карт. Нижняя карта колоды — козырь, она лежит под колодой открытой.",
                "Ход" to "Первым ходит тот, у кого младший козырь. Ходить можно любой картой, подкидывать — картами того же достоинства, что уже лежат на столе. Всего не больше 6 карт за ход (в первый ход 5) и не больше, чем карт у отбивающегося.",
                "Отбой" to "Карту бьют старшей картой той же масти или любым козырем (козырь — только старшим козырем). Перетащите карту на карту соперника или просто нажмите на неё.",
                "Беру / Бито" to "Не можете отбиться — «Беру», и вы пропускаете ход. Если всё отбито и подкинуть нечего — «Бито», карты уходят в сброс, и ходите вы.",
                "Переводной" to "Пока вы ничего не отбили, можно положить карту того же достоинства и перевести ход следующему игроку.",
                "Соседи / все" to "«Соседи»: подкидывать могут только игроки слева и справа от отбивающегося. «Все»: все игроки.",
                "Игра без интернета" to "Один телефон создаёт игру («Создать игру»). Остальные подключаются к той же Wi‑Fi сети или к точке доступа этого телефона (её можно включить и без мобильного интернета) и нажимают «Найти игру».",
            ).forEach { (h, t) ->
                Text(h, style = scriptStyle(26))
                Text(t, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- lobbies

@Composable
fun SeatRow(s: SeatInfo, me: Boolean, onRemoveBot: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (s.kind == SeatKind.EMPTY) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) { Text("?", color = Color.White, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Ждём игрока…", color = Color(0xCCFFFFFF))
        } else {
            AvatarSquare(s.avatar, 46.dp, if (me) Pal.FrameGreen else Pal.FrameRed)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name + if (me) " (вы)" else "", color = Color.White, fontWeight = FontWeight.Bold)
                Text(if (s.kind == SeatKind.BOT) "бот" else "игрок", color = Color(0xCCFFFFFF), fontSize = 12.sp)
            }
            if (s.kind == SeatKind.BOT && onRemoveBot != null) Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.clickable(onClick = onRemoveBot).padding(6.dp))
        }
    }
}

@Composable
fun HostLobbyScreen(vm: AppVm) {
    Column(Modifier.fillMaxSize().background(Pal.Bar).systemBarsPadding()) {
        TitleBar("Ваша комната") { vm.leaveToMenu() }
        Felt(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("Ждём друзей", style = scriptStyle(30))
                Text("Друзья нажимают «Найти игру» и видят вашу комнату.", color = Color(0xDDFFFFFF), fontSize = 14.sp)
                if (vm.myAddresses.isNotEmpty())
                    Text("Если комната не видна, адрес для ввода вручную: ${vm.myAddresses.joinToString(" или ")}", color = Pal.Gold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Мест: ${vm.seatCount}", style = scriptStyle(26), modifier = Modifier.weight(1f))
                    SmallBtn("−") { if (vm.seatCount > 2) vm.changeSeatCount(vm.seatCount - 1) }
                    Spacer(Modifier.width(8.dp))
                    SmallBtn("+") { if (vm.seatCount < Deck.maxPlayers(vm.rules.deckSize)) vm.changeSeatCount(vm.seatCount + 1) }
                }
                Spacer(Modifier.height(6.dp))
                vm.seats.forEachIndexed { i, s -> SeatRow(s, i == vm.mySeat, if (i != vm.mySeat) ({ vm.removeBot(i) }) else null) }
                Spacer(Modifier.height(6.dp))
                if (vm.seats.any { it.kind == SeatKind.EMPTY }) BigButton("+ Добавить бота", { vm.addBot() }, Modifier.fillMaxWidth(), Color(0x55FFFFFF))
                Spacer(Modifier.height(10.dp))
                Text("Пустые места при старте займут боты.", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                BigButton("Начать игру", { vm.startGame() }, Modifier.fillMaxWidth(), Pal.Red)
            }
            vm.toast?.let { Toast(it) }
        }
    }
}

@Composable
fun ClientLobbyScreen(vm: AppVm) {
    Column(Modifier.fillMaxSize().background(Pal.Bar).systemBarsPadding()) {
        TitleBar("Комната") { vm.leaveToMenu() }
        Felt(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Ждём начала игры", style = scriptStyle(30))
                Spacer(Modifier.height(8.dp))
                vm.seats.forEachIndexed { i, s -> SeatRow(s, i == vm.mySeat, null) }
                Spacer(Modifier.height(8.dp))
                Text(
                    listOf(if (vm.rules.transfer) "переводной" else "подкидной", if (vm.rules.neighborsOnly) "соседи" else "все", "колода ${vm.rules.deckSize}").joinToString(" · "),
                    color = Color(0xDDFFFFFF), fontSize = 14.sp,
                )
            }
            vm.toast?.let { Toast(it) }
        }
    }
}
