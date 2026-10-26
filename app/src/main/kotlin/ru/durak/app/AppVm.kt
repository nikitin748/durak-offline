package ru.durak.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import ru.durak.engine.*
import ru.durak.net.*
import java.io.File

enum class Screen { HOME, HOST_LOBBY, CLIENT_LOBBY, GAME }
enum class Tab { PROFILE, FIND, RULES, CREATE }

data class StickerEvt(val seat: Int, val id: Int, val stamp: Long)

class AppVm(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("durak", Context.MODE_PRIVATE)

    var screen by mutableStateOf(Screen.HOME); private set
    var tab by mutableStateOf(Tab.PROFILE)
    var name by mutableStateOf(prefs.getString("name", "Игрок") ?: "Игрок"); private set
    var avatar by mutableIntStateOf(prefs.getInt("avatar", 0)); private set
    var coins by mutableIntStateOf(prefs.getInt("coins", 1000)); private set

    // "Создать игру" settings (remembered between launches)
    var setPlayers by mutableIntStateOf(prefs.getInt("players", 2))
    var setDeck by mutableIntStateOf(prefs.getInt("deck", 36))
    var setTransfer by mutableStateOf(prefs.getBoolean("transfer", false))
    var setNeighbors by mutableStateOf(prefs.getBoolean("neighbors", false))
    var setFast by mutableStateOf(prefs.getBoolean("fast", false))
    var setStake by mutableIntStateOf(prefs.getInt("stake", 100))
    var setBotsOnly by mutableStateOf(prefs.getBoolean("botsOnly", false))

    // statistics
    var statGames by mutableIntStateOf(prefs.getInt("games", 0)); private set
    var statFool by mutableIntStateOf(prefs.getInt("fool", 0)); private set
    var statDraw by mutableIntStateOf(prefs.getInt("draw", 0)); private set

    var seats by mutableStateOf<List<SeatInfo>>(emptyList()); private set
    var rules by mutableStateOf(Rules()); private set
    var seatCount by mutableIntStateOf(2); private set
    var view by mutableStateOf<GameView?>(null); private set
    var mySeat by mutableIntStateOf(0); private set
    var toast by mutableStateOf<String?>(null); private set
    var stickers by mutableStateOf<List<StickerEvt>>(emptyList()); private set
    var rooms by mutableStateOf<List<RoomAd>>(emptyList()); private set
    var connecting by mutableStateOf(false); private set
    var isHost by mutableStateOf(false); private set
    var myAddresses by mutableStateOf<List<String>>(emptyList()); private set
    var stake by mutableIntStateOf(100); private set

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

    fun saveSettings() {
        setPlayers = setPlayers.coerceAtMost(Deck.maxPlayers(setDeck))
        prefs.edit().putInt("players", setPlayers).putInt("deck", setDeck).putBoolean("transfer", setTransfer)
            .putBoolean("neighbors", setNeighbors).putBoolean("fast", setFast).putInt("stake", setStake)
            .putBoolean("botsOnly", setBotsOnly).apply()
    }

    private fun settingsRules() = Rules(transfer = setTransfer, deckSize = setDeck, neighborsOnly = setNeighbors)
    private fun botDelay() = if (setFast) 350L..700L else 700L..1400L

    private fun acquireMulticast() {
        runCatching {
            val wm = getApplication<Application>().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wm.createMulticastLock("durak").apply { setReferenceCounted(false); acquire() }
        }
    }

    // ---------- hosting ----------
    /** "Быстрая игра": bots, saved settings. */
    fun quickGame() = hostSolo(setPlayers.coerceAtLeast(2), setTransfer)

    fun hostSolo(players: Int, transfer: Boolean, seed: Long? = null) {
        val r = settingsRules().copy(transfer = transfer)
        openHost(players.coerceAtMost(Deck.maxPlayers(r.deckSize)), r, network = false, seed = seed)
        session?.start()
    }

    /** "Создать" on the create-game screen. */
    fun createGame() {
        saveSettings()
        if (setBotsOnly) { hostSolo(setPlayers, setTransfer); return }
        openHost(setPlayers, settingsRules(), network = true)
        screen = Screen.HOST_LOBBY
    }

    private fun openHost(count: Int, r: Rules, network: Boolean, seed: Long? = null) {
        closeAll()
        isHost = true
        seatCount = count; rules = r; stake = setStake
        val s = GameSession(viewModelScope, roomName = "$name: Дурак", random = if (seed != null) kotlin.random.Random(seed) else kotlin.random.Random.Default)
        s.botDelayMs = botDelay()
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
    fun setTransferRule(on: Boolean) { rules = rules.copy(transfer = on); session?.configure(seatCount, rules) }
    fun addBot() { session?.addBot() }
    fun removeBot(seat: Int) { session?.removeBot(seat) }
    fun startGame() { coinsPaid = false; session?.start() }

    fun playAgain() {
        val s = session ?: return
        coinsPaid = false
        s.backToLobby(); s.start()
    }

    // ---------- joining ----------
    fun startListening() {
        if (listenJob?.isActive == true) return
        acquireMulticast()
        listenJob = viewModelScope.launch {
            launch { Discovery.listen().collect { ad -> seen["${ad.host}:${ad.port}"] = ad to System.currentTimeMillis() } }
            while (true) {
                val now = System.currentTimeMillis()
                rooms = seen.values.filter { now - it.second < 4000 }.map { it.first }.sortedBy { it.name }
                delay(700)
            }
        }
    }

    fun stopListening() { listenJob?.cancel(); listenJob = null }

    fun joinRoom(host: String, port: Int = TCP_PORT) {
        if (connecting) return
        connecting = true
        stopListening()
        viewModelScope.launch {
            val c = TcpClient(viewModelScope)
            try {
                c.connect(host, port, ClientMsg.Join(name, avatar))
                conn = c; isHost = false
                collect(c)
            } catch (e: Exception) {
                flash("Не удалось подключиться: ${e.message}")
                startListening()
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
                        if (screen != Screen.GAME) screen = Screen.GAME
                        if (m.view.gameOver && (prev == null || !prev.gameOver)) payout(m.view)
                        if (!m.view.gameOver) coinsPaid = false
                    }
                    is ServerMsg.Sticker -> addSticker(m.seat, m.id)
                    is ServerMsg.Error -> flash(m.text)
                    is ServerMsg.Closed -> { flash(m.reason); leaveToMenu() }
                }
            }
        }
    }

    private fun payout(v: GameView) {
        if (coinsPaid) return
        coinsPaid = true
        statGames++
        when (v.loser) {
            null -> statDraw++
            v.seat -> { statFool++; coins = (coins - stake).coerceAtLeast(0) }
            else -> coins += stake
        }
        prefs.edit().putInt("coins", coins).putInt("games", statGames).putInt("fool", statFool).putInt("draw", statDraw).apply()
    }

    fun act(a: Action) { conn?.send(ClientMsg.Act(a)) }
    fun sendSticker(id: Int) { conn?.send(ClientMsg.Sticker(id)) }

    private fun addSticker(seat: Int, id: Int) {
        val e = StickerEvt(seat, id, System.nanoTime())
        stickers = stickers.filter { it.seat != seat } + e
        viewModelScope.launch { delay(2600); stickers = stickers.filter { it !== e } }
    }

    fun flash(text: String) {
        toast = text
        viewModelScope.launch { delay(1800); if (toast == text) toast = null }
    }

    /** Sends this app's own APK through Android's share sheet (Bluetooth, Nearby Share, messengers…). */
    fun shareApk(ctx: Context) {
        runCatching {
            val src = File(ctx.applicationInfo.sourceDir)
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val out = File(dir, "durak-offline.apk")
            src.copyTo(out, overwrite = true)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", out)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Отправить игру").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { flash("Не получилось поделиться: ${it.message}") }
    }

    fun leaveToMenu() {
        closeAll()
        screen = Screen.HOME
    }

    private fun closeAll() {
        collectJob?.cancel(); adJob?.cancel(); stopListening()
        runCatching { conn?.close() }
        tcpHost?.stop()
        multicastLock?.let { runCatching { it.release() } }
        conn = null; tcpHost = null; session = null; multicastLock = null
        view = null; seats = emptyList(); stickers = emptyList(); isHost = false; rooms = emptyList(); seen.clear()
    }

    override fun onCleared() { closeAll() }
}
