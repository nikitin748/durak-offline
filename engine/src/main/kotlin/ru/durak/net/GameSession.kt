package ru.durak.net

import kotlinx.coroutines.*
import ru.durak.engine.*
import kotlin.random.Random

/** One way to talk to a seat's owner: a TCP socket or the host's own UI. */
fun interface SeatLink { fun send(msg: ServerMsg) }

/**
 * Host-side game room: lobby, seat management, bots, authoritative [Game], broadcasting.
 * All mutation is synchronized; messages to links are sent from inside the lock, so links must not block.
 */
class GameSession(
    private val scope: CoroutineScope,
    var roomName: String = "Дурак",
    private val botDelayMs: LongRange = 700L..1400L,
    private val random: Random = Random.Default,
) {
    private val lock = Any()
    private val seats = MutableList(2) { SeatInfo() }
    private val links = HashMap<Int, SeatLink>()
    private var rules = Rules()
    private var game: Game? = null
    private var botJob: Job? = null
    private var lastLoser: Int? = null

    /** For tests: has the current game ended? */
    fun finishedForTest(): Boolean = synchronized(lock) { game?.gameOver == true }

    val started: Boolean get() = synchronized(lock) { game != null }

    // ---------- lobby ----------
    /** Returns the seat taken, or null when the room is full / already playing. */
    fun join(name: String, avatar: Int, link: SeatLink): Int? = synchronized(lock) {
        // Rejoin after a game ended is not supported; during a game, only a seat vacated by a dropped player is taken over.
        if (game != null) return null
        val free = seats.indexOfFirst { it.kind == SeatKind.EMPTY }
        if (free < 0) return null
        seats[free] = SeatInfo(name.take(16), avatar, SeatKind.HUMAN)
        links[free] = link
        link.send(ServerMsg.Welcome(free))
        broadcastLobby()
        free
    }

    fun leave(seat: Int) = synchronized(lock) {
        if (links.remove(seat) == null) return@synchronized
        if (game == null) {
            seats[seat] = SeatInfo()
            compactAfterLeave()
            broadcastLobby()
        } else {
            // Mid-game dropout: a bot takes over the hand so the others can finish.
            seats[seat] = seats[seat].copy(kind = SeatKind.BOT)
            broadcastState()
            runBots()
        }
    }

    private fun compactAfterLeave() { /* seat indexes stay stable in the lobby; nothing to do */ }

    fun configure(seatCount: Int, newRules: Rules) = synchronized(lock) {
        if (game != null) return@synchronized
        require(seatCount in 2..6)
        rules = newRules
        while (seats.size < seatCount) seats += SeatInfo()
        while (seats.size > seatCount) {
            // Drop the last empty/bot seat; never kick a human by shrinking.
            val i = seats.indexOfLast { it.kind != SeatKind.HUMAN }
            if (i < 0 || seats.size <= seatCount) break
            seats.removeAt(i)
            // Shift link keys above i down by one.
            val shifted = links.entries.associate { (k, v) -> (if (k > i) k - 1 else k) to v }
            links.clear(); links.putAll(shifted)
            for ((s, l) in links) l.send(ServerMsg.Welcome(s))
        }
        broadcastLobby()
    }

    fun addBot(): Boolean = synchronized(lock) {
        if (game != null) return false
        val i = seats.indexOfFirst { it.kind == SeatKind.EMPTY }
        if (i < 0) return false
        val n = seats.count { it.kind == SeatKind.BOT }
        seats[i] = SeatInfo(BOT_NAMES[n % BOT_NAMES.size], (i + 3) % AVATAR_COUNT, SeatKind.BOT)
        broadcastLobby()
        true
    }

    fun removeBot(seat: Int) = synchronized(lock) {
        if (game == null && seats.getOrNull(seat)?.kind == SeatKind.BOT) { seats[seat] = SeatInfo(); broadcastLobby() }
    }

    fun humanCount() = synchronized(lock) { seats.count { it.kind == SeatKind.HUMAN } }

    /** Fills any still-empty seats with bots and starts. */
    fun start(): Boolean = synchronized(lock) {
        if (game != null) return false
        while (addBot()) { /* fill */ }
        if (seats.size < 2 || seats.any { it.kind == SeatKind.EMPTY }) return false
        game = Game.newGame(seats.size, rules, random, firstAttacker = lastLoser?.takeIf { it < seats.size })
        broadcastState()
        runBots()
        true
    }

    /** Back to the lobby after a finished game (keeps players, remembers the loser who leads next). */
    fun backToLobby() = synchronized(lock) {
        val g = game ?: return@synchronized
        if (!g.gameOver) return@synchronized
        lastLoser = g.loser
        game = null
        // Bots that replaced humans stay bots; human seats keep their links.
        broadcastLobby()
    }

    // ---------- in-game ----------
    fun handle(seat: Int, msg: ClientMsg) = synchronized(lock) {
        when (msg) {
            is ClientMsg.Act -> {
                val g = game ?: return@synchronized
                when (val r = g.apply(seat, msg.action)) {
                    is Result.Error -> links[seat]?.send(ServerMsg.Error(r.message))
                    Result.Ok -> { broadcastState(); runBots() }
                }
            }
            is ClientMsg.Sticker -> for (l in links.values) l.send(ServerMsg.Sticker(seat, msg.id))
            ClientMsg.Leave -> leave(seat)
            is ClientMsg.Join -> {}
        }
    }

    /** Host UI can call this to push a sticker from a bot-less perspective; bots send random ones themselves. */
    private fun botSticker(seat: Int) {
        if (random.nextInt(14) == 0) for (l in links.values) l.send(ServerMsg.Sticker(seat, random.nextInt(STICKER_COUNT)))
    }

    private fun broadcastLobby() {
        val m = ServerMsg.Lobby(seats.toList(), rules, roomName, game != null)
        for (l in links.values) l.send(m)
    }

    private fun broadcastState() {
        val g = game ?: return
        for ((seat, l) in links) l.send(ServerMsg.State(g.viewFor(seat), seats.toList()))
    }

    private fun runBots() {
        if (botJob?.isActive == true) return
        botJob = scope.launch {
            while (true) {
                delay(random.nextLong(botDelayMs.first, botDelayMs.last + 1))
                val more = synchronized(lock) {
                    val g = game ?: return@synchronized false
                    if (g.gameOver) return@synchronized false
                    for (s in g.waitingFor()) {
                        if (seats[s].kind != SeatKind.BOT) continue
                        val a = Bot.decide(g, s) ?: continue
                        if (g.apply(s, a) is Result.Ok) { broadcastState(); botSticker(s); return@synchronized true }
                    }
                    false
                }
                if (!more) break
            }
        }
    }

    companion object {
        val BOT_NAMES = listOf("Вася", "Маша", "Дима", "Лена", "Гриша")
        const val AVATAR_COUNT = 12
        const val STICKER_COUNT = 12
    }
}
