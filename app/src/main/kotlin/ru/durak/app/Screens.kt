package ru.durak.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.durak.engine.Suit
import ru.durak.net.SeatInfo
import ru.durak.net.SeatKind
import ru.durak.net.TCP_PORT

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Pal.ButtonBlue, enabled: Boolean = true) {
    Box(
        modifier.clip(RoundedCornerShape(26.dp)).background(if (enabled) color else Color(0xFF6B7F94))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Pal.Panel).padding(16.dp), content = content)
}

@Composable
fun CoinChip(coins: Int, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x66000000)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🪙", fontSize = 14.sp); Spacer(Modifier.width(4.dp))
        Text("$coins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun MenuScreen(vm: AppVm) {
    var name by remember { mutableStateOf(vm.name) }
    var players by remember { mutableIntStateOf(3) }
    var transfer by remember { mutableStateOf(false) }
    Felt {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { CoinChip(vm.coins) }
            Spacer(Modifier.height(8.dp))
            Text("ДУРАК", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black)
            Text("без интернета", color = Pal.Gold, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(18.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text("Ваш профиль", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(14); vm.setProfile(name.ifBlank { "Игрок" }, vm.avatar) },
                    singleLine = true, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Pal.Gold, unfocusedLabelColor = Color.LightGray),
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AVATARS.indices.toList()) { i ->
                        Avatar(i, 48.dp, ring = if (i == vm.avatar) Pal.Gold else null, modifier = Modifier.clickable { vm.setProfile(name.ifBlank { "Игрок" }, i) })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text("Игра с ботами", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Игроков: $players", color = Color.White, modifier = Modifier.weight(1f))
                    SmallBtn("−") { if (players > 2) players-- }
                    Spacer(Modifier.width(8.dp))
                    SmallBtn("+") { if (players < 6) players++ }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Переводной", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = transfer, onCheckedChange = { transfer = it })
                }
                Spacer(Modifier.height(6.dp))
                BigButton("Играть с ботами", { vm.hostSolo(players, transfer) }, Modifier.fillMaxWidth(), Pal.Green)
            }
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text("Игра по Wi‑Fi / точке доступа", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Интернет не нужен: достаточно, чтобы все телефоны были в одной сети (например, один раздаёт точку доступа).", color = Color(0xFFB9CCE0), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                BigButton("Создать комнату", { vm.hostRoom() }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                BigButton("Найти комнату", { vm.openFinder() }, Modifier.fillMaxWidth(), Pal.Orange)
            }
        }
        vm.toast?.let { Toast(it) }
    }
}

@Composable
fun SmallBtn(text: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(Pal.ButtonBlue).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BoxScope.Toast(text: String) {
    Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp)).background(Color(0xDD000000)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, color = Color.White, fontSize = 15.sp)
    }
}

@Composable
fun SeatRow(i: Int, s: SeatInfo, me: Boolean, onRemoveBot: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (s.kind == SeatKind.EMPTY) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) { Text("?", color = Color.White, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Ждём игрока…", color = Color(0xFFB9CCE0))
        } else {
            Avatar(s.avatar, 44.dp, ring = if (me) Pal.Gold else null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name + if (me) " (вы)" else "", color = Color.White, fontWeight = FontWeight.Bold)
                Text(if (s.kind == SeatKind.BOT) "бот" else "игрок", color = Color(0xFFB9CCE0), fontSize = 12.sp)
            }
            if (s.kind == SeatKind.BOT && onRemoveBot != null) Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onRemoveBot).padding(8.dp))
        }
    }
}

@Composable
fun HostLobbyScreen(vm: AppVm) {
    Felt {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Ваша комната", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text("Друзья нажимают «Найти комнату» и видят её в списке.", color = Color(0xFFB9CCE0), fontSize = 14.sp)
            if (vm.myAddresses.isNotEmpty())
                Text("Если комната не находится, введите адрес вручную: ${vm.myAddresses.joinToString(" или ")}", color = Pal.Gold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Мест: ${vm.seatCount}", color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    SmallBtn("−") { if (vm.seatCount > 2) vm.changeSeatCount(vm.seatCount - 1) }
                    Spacer(Modifier.width(8.dp))
                    SmallBtn("+") { if (vm.seatCount < 6) vm.changeSeatCount(vm.seatCount + 1) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Переводной", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = vm.rules.transfer, onCheckedChange = { vm.setTransfer(it) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                vm.seats.forEachIndexed { i, s -> SeatRow(i, s, i == vm.mySeat, if (i != vm.mySeat) ({ vm.removeBot(i) }) else null) }
                Spacer(Modifier.height(6.dp))
                if (vm.seats.any { it.kind == SeatKind.EMPTY }) BigButton("+ Добавить бота", { vm.addBot() }, Modifier.fillMaxWidth(), Color(0xFF52718F))
            }
            Spacer(Modifier.height(16.dp))
            Text("Пустые места при старте займут боты.", color = Color(0xFFB9CCE0), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            BigButton("Начать игру", { vm.startGame() }, Modifier.fillMaxWidth(), Pal.Green)
            Spacer(Modifier.height(8.dp))
            BigButton("Назад", { vm.leaveToMenu() }, Modifier.fillMaxWidth(), Color(0xFF52718F))
        }
        vm.toast?.let { Toast(it) }
    }
}

@Composable
fun FinderScreen(vm: AppVm) {
    var ip by remember { mutableStateOf("") }
    Felt {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Поиск комнат", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Подключитесь к той же Wi‑Fi сети или точке доступа, что и хозяин комнаты.", color = Color(0xFFB9CCE0), fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth()) {
                if (vm.rooms.isEmpty()) Text(if (vm.connecting) "Подключаемся…" else "Ищем комнаты…", color = Color.White)
                vm.rooms.forEach { r ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.joinRoom(r.host, r.port) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🃏", fontSize = 24.sp); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(r.name, color = Color.White, fontWeight = FontWeight.Bold); Text("${r.players} из ${r.seats} игроков", color = Color(0xFFB9CCE0), fontSize = 12.sp) }
                        Text("Войти ›", color = Pal.Gold, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text("Или введите адрес хозяина", color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it.trim() }, singleLine = true, label = { Text("например 192.168.43.1") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Pal.Gold, unfocusedLabelColor = Color.LightGray),
                )
                Spacer(Modifier.height(8.dp))
                BigButton("Подключиться", { if (ip.isNotBlank()) vm.joinRoom(ip, TCP_PORT) }, Modifier.fillMaxWidth(), enabled = ip.isNotBlank())
            }
            Spacer(Modifier.height(16.dp))
            BigButton("Назад", { vm.leaveToMenu() }, Modifier.fillMaxWidth(), Color(0xFF52718F))
        }
        vm.toast?.let { Toast(it) }
    }
}

@Composable
fun ClientLobbyScreen(vm: AppVm) {
    Felt {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
            Text("Комната", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Ждём, пока хозяин начнёт игру…", color = Pal.Gold, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth()) {
                vm.seats.forEachIndexed { i, s -> SeatRow(i, s, i == vm.mySeat, null) }
                Spacer(Modifier.height(6.dp))
                Text(if (vm.rules.transfer) "Правила: переводной" else "Правила: подкидной", color = Color(0xFFB9CCE0), fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            BigButton("Выйти", { vm.leaveToMenu() }, Modifier.fillMaxWidth(), Color(0xFF52718F))
        }
        vm.toast?.let { Toast(it) }
    }
}
