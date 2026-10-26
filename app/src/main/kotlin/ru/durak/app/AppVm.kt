package ru.durak.app

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import ru.durak.engine.*
import ru.durak.net.*

enum class Screen { MENU, HOST_LOBBY, FIND_ROOM, CLIENT_LOBBY, GAME }

data class StickerEvt(val seat: Int, val id: Int, val stamp: Long)

class AppVm(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("durak", Context.MODE_PRIVATE)

    var screen by mutableStateOf(Screen.MENU); private set
    var name by mutableStateOf(prefs.getString("name", "Игрок") ?: "Игрок"); private set
    var avatar by mutableIntStateOf(prefs.getInt("avatar", 0)); private set
    var coins by mutableIntStateOf(prefs.getInt("coins", 100)); private set

    var seats by mutableStateOf<List<SeatInfo>>(emptyList()); private set
    var rules by mutableStateOf(Rules()); private set
    var seatCount by mutableIntStateOf(4); private set
    var view by mutableStateOf<GameView?>(null); private set
    var mySeat by mutableIntStateOf(0); private set
    var toast by mutableStateOf<String?>(null); private set
    var stickers by mutableStateOf<List<StickerEvt>>(emptyList()); private set
    var rooms by mutableStateOf<List<RoomAd>>(emptyList()); private set
    var connecting by mutableStateOf(false); private set
    var isHost by mutableStateOf(false); private set
    var myAddresses by mutableStateOf<List<String>>(emptyList()); private set

    private var session: GameSession? = null
    private var tcpHost: TcpHost? = null
    private var conn: Connection? = null
    private var collectJob: Job? = null
    private var adJob: Job? = null
    private var listenJob: Job? = null
    private val seen = HashMap<String, Pair<RoomAd, Long>>()
    private var multicastLock: WifiManager.MulticastLock? = null
    private var coinsPaid = false

    fun setProfile(n: String, a: Int) {
        name = n.take(14); avatar = a
        prefs.edit().putString("name", name).putInt("avatar", avatar).apply()
    }

    private fun saveCoins() = prefs.edit().putInt("coins", coins).apply()

    private fun acquireMulticast() {
        runCatching {
            val wm = getApplication<Application>().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wm.createMulticastLock("durak").apply { setReferenceCounted(false); acquire() }
        }
    }

    // ---------- hosting ----------
    fun hostSolo(players: Int, transfer: Boolean, seed: Long? = null) {
        openHost(players, Rules(transfer = transfer), network = false, seed = seed)
        session?.start()
    }

    fun hostRoom() {
        openHost(seatCount, rules, network = true)
        screen = Screen.HOST_LOBBY
    }

    private fun openHost(count: Int, r: Rules, network: Boolean, seed: Long? = null) {
        closeAll()
        isHost = true
        seatCount = count; rules = r
        val s = GameSession(viewModelScope, roomName = "$name: Дурак", random = if (seed != null) kotlin.random.Random(seed) else kotlin.random.Random.Default)
        s.configure(count, r)
        session = s
        val local = LocalConnection(s, name, avatar)
        conn = local; mySeat = local.seat
        collect(local)
        if (network) {
            acquireMulticast()
            val th = TcpHost(s, viewModelScope)
            runCatching { th.start() }.onFailure { toast = "Не удалось открыть порт: ${it.message}" }
            tcpHost = th
            myAddresses = Discovery.localAddresses()
            adJob = Discovery.advertise(viewModelScope, {
                RoomAd(s.roomName, "", TCP_PORT, s.humanCount(), seats.size.coerceAtLeast(2))
            })
        }
    }

    fun changeSeatCount(n: Int) { seatCount = n; session?.configure(n, rules) }
    fun setTransfer(on: Boolean) { rules = rules.copy(transfer = on); session?.configure(seatCount, rules) }
    fun addBot() { session?.addBot() }
    fun removeBot(seat: Int) { session?.removeBot(seat) }
    fun startGame() { coinsPaid = false; session?.start() }

    fun playAgain() {
        val s = session ?: return
        coinsPaid = false
        s.backToLobby(); s.start()
    }

    // ---------- joining ----------
    fun openFinder() {
        closeAll()
        screen = Screen.FIND_ROOM
        acquireMulticast()
        listenJob = viewModelScope.launch {
            launch {
                Discovery.listen().collect { ad ->
                    seen["${ad.host}:${ad.port}"] = ad to System.currentTimeMillis()
                }
            }
            while (true) {
                val now = System.currentTimeMillis()
                rooms = seen.values.filter { now - it.second < 4000 }.map { it.first }.sortedBy { it.name }
                delay(700)
            }
        }
    }

    fun joinRoom(host: String, port: Int = TCP_PORT) {
        if (connecting) return
        connecting = true
        listenJob?.cancel()
        viewModelScope.launch {
            val c = TcpClient(viewModelScope)
            try {
                c.connect(host, port, ClientMsg.Join(name, avatar))
                conn = c; isHost = false
                collect(c)
            } catch (e: Exception) {
                toast = "Не удалось подключиться: ${e.message}"
                openFinder()
            } finally { connecting = false }
        }
    }

    // ---------- shared ----------
    private fun collect(c: Connection) {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            c.incoming.collect { m ->
                when (m) {
                    is ServerMsg.Welcome -> { mySeat = m.seat; if (!isHost) screen = Screen.CLIENT_LOBBY }
                    is ServerMsg.Lobby -> {
                        seats = m.seats; rules = m.rules; seatCount = m.seats.size
                        if (screen != Screen.GAME && !isHost) screen = Screen.CLIENT_LOBBY
                    }
                    is ServerMsg.State -> {
                        seats = m.seats
                        val prev = view
                        view = m.view
                        mySeat = m.view.seat
                        if (screen != Screen.GAME) { screen = Screen.GAME }
                        if (m.view.gameOver && (prev == null || !prev.gameOver)) payout(m.view)
                    }
                    is ServerMsg.Sticker -> addSticker(m.seat, m.id)
                    is ServerMsg.Error -> { android.util.Log.d("DurakUI", "server error ${m.text}"); flash(m.text) }
                    is ServerMsg.Closed -> { flash(m.reason); leaveToMenu() }
                }
            }
        }
    }

    private fun payout(v: GameView) {
        if (coinsPaid) return
        coinsPaid = true
        coins = if (v.loser == v.seat) (coins - 10).coerceAtLeast(0) else coins + 10
        saveCoins()
    }

    fun act(a: Action) { android.util.Log.d("DurakUI", "act $a conn=$conn"); conn?.send(ClientMsg.Act(a)) }
    fun sendSticker(id: Int) { conn?.send(ClientMsg.Sticker(id)) }

    private fun addSticker(seat: Int, id: Int) {
        val e = StickerEvt(seat, id, System.nanoTime())
        stickers = stickers.filter { it.seat != seat } + e
        viewModelScope.launch { delay(2600); stickers = stickers.filter { it !== e } }
    }

    fun flash(text: String) {
        toast = text
        viewModelScope.launch { delay(1600); if (toast == text) toast = null }
    }

    fun leaveToMenu() {
        closeAll()
        screen = Screen.MENU
    }

    private fun closeAll() {
        collectJob?.cancel(); adJob?.cancel(); listenJob?.cancel()
        runCatching { conn?.close() }
        tcpHost?.stop()
        multicastLock?.let { runCatching { it.release() } }
        conn = null; tcpHost = null; session = null; multicastLock = null
        view = null; seats = emptyList(); stickers = emptyList(); isHost = false; rooms = emptyList(); seen.clear()
    }

    override fun onCleared() { closeAll() }
}
