package ru.durak.engine

import kotlin.random.Random

/**
 * Authoritative Durak game (подкидной, optionally переводной). Seats are 0 until playerCount, clockwise.
 * Every non-defender may throw cards in at any moment (like in "Дурак онлайн"); the game settles
 * automatically when nobody can or wants to add more.
 */
class Game private constructor(
    val playerCount: Int,
    val rules: Rules,
    private val deck: ArrayDeque<Card>,
    val trump: Suit,
    private val trumpCard: Card,
    private val hands: List<MutableList<Card>>,
    startAttacker: Int,
) {
    var attacker: Int = startAttacker; private set
    var defender: Int = 0; private set
    var defenderTaking: Boolean = false; private set
    var discardCount: Int = 0; private set
    var turnNumber: Int = 1; private set
    var loser: Int? = null; private set
    var gameOver: Boolean = false; private set

    private val table = mutableListOf<TablePair>()
    private val passed = mutableSetOf<Int>()
    private val finishedOrder = mutableListOf<Int>()

    init {
        defender = nextActive(attacker)
    }

    // ---------- queries ----------
    fun hand(seat: Int): List<Card> = hands[seat].toList()
    fun tableCards(): List<TablePair> = table.toList()
    fun deckSize() = deck.size
    fun finished(): List<Int> = finishedOrder.toList()
    fun isActive(seat: Int) = hands[seat].isNotEmpty() && seat !in finishedOrder

    private val maxAttacks: Int
        get() = if (turnNumber == 1) rules.firstTurnLimit else rules.handSize
    private fun undefended() = table.count { it.defense == null }

    private fun roomToAdd(): Boolean =
        table.size < maxAttacks && undefended() < hands[defender].size

    private fun tableRanks(): Set<Int> = table.flatMap { listOfNotNull(it.attack.rank, it.defense?.rank) }.toSet()

    fun canThrow(seat: Int): Boolean {
        if (gameOver || seat == defender || !isActive(seat) || table.isEmpty()) return false
        if (!roomToAdd()) return false
        val ranks = tableRanks()
        return hands[seat].any { it.rank in ranks }
    }

    /** Cards [seat] may legally play right now as an attack / throw-in. */
    fun attackCards(seat: Int): List<Card> {
        if (gameOver || seat == defender || !isActive(seat)) return emptyList()
        if (table.isEmpty()) return if (seat == attacker) hand(seat) else emptyList()
        if (!roomToAdd()) return emptyList()
        val ranks = tableRanks()
        return hands[seat].filter { it.rank in ranks }
    }

    fun defendOptions(): List<Pair<Int, Card>> {
        if (gameOver || defenderTaking) return emptyList()
        val out = mutableListOf<Pair<Int, Card>>()
        table.forEachIndexed { i, p ->
            if (p.defense == null) hands[defender].filter { it.beats(p.attack, trump) }.forEach { out += i to it }
        }
        return out
    }

    fun transferCards(): List<Card> {
        if (!rules.transfer || gameOver || defenderTaking || table.isEmpty()) return emptyList()
        if (table.any { it.defense != null }) return emptyList()
        if (table.size + 1 > maxAttacks) return emptyList()
        val next = nextActive(defender)
        if (next == defender) return emptyList()
        if (hands[next].size < table.size + 1) return emptyList()
        val rank = table.first().attack.rank
        return hands[defender].filter { it.rank == rank }
    }

    fun canTake() = !gameOver && !defenderTaking && undefended() > 0

    /** Seats the game is currently waiting for. */
    fun waitingFor(): List<Int> {
        if (gameOver) return emptyList()
        if (table.isEmpty()) return listOf(attacker)
        val w = mutableListOf<Int>()
        if (!defenderTaking && undefended() > 0) w += defender
        for (s in 0 until playerCount) if (s != defender && s !in passed && canThrow(s)) w += s
        return w
    }

    // ---------- actions ----------
    fun apply(seat: Int, action: Action): Result {
        if (gameOver) return Result.Error("Игра окончена")
        if (seat !in 0 until playerCount || !isActive(seat)) return Result.Error("Вы не в игре")
        when (action) {
            is Action.Attack -> {
                val c = action.card
                if (c !in hands[seat]) return Result.Error("Нет такой карты")
                if (c !in attackCards(seat)) return Result.Error("Этой картой нельзя ходить")
                hands[seat].remove(c)
                table += TablePair(c)
                passed.clear()
            }
            is Action.Defend -> {
                if (seat != defender) return Result.Error("Вы не отбиваетесь")
                if (defenderTaking) return Result.Error("Вы уже берёте")
                val p = table.getOrNull(action.index) ?: return Result.Error("Нет такой карты на столе")
                if (p.defense != null) return Result.Error("Уже отбито")
                if (action.card !in hands[seat]) return Result.Error("Нет такой карты")
                if (!action.card.beats(p.attack, trump)) return Result.Error("Эта карта не бьёт")
                hands[seat].remove(action.card)
                table[action.index] = p.copy(defense = action.card)
                passed.clear()
            }
            is Action.Transfer -> {
                if (seat != defender) return Result.Error("Вы не отбиваетесь")
                if (action.card !in transferCards()) return Result.Error("Перевести нельзя")
                val next = nextActive(defender)
                hands[seat].remove(action.card)
                table += TablePair(action.card)
                attacker = seat
                defender = next
                passed.clear()
            }
            Action.Take -> {
                if (seat != defender) return Result.Error("Вы не отбиваетесь")
                if (!canTake()) return Result.Error("Нечего брать")
                defenderTaking = true
                passed.clear()
            }
            Action.Pass -> {
                if (seat == defender) return Result.Error("Защитник не может пасовать")
                if (table.isEmpty()) return Result.Error("Сначала нужно сходить")
                passed += seat
            }
        }
        settle()
        return Result.Ok
    }


    private fun settle() {
        if (table.isEmpty()) return
        val someoneCanStillAdd = (0 until playerCount).any { it != defender && it !in passed && canThrow(it) }
        if (someoneCanStillAdd) return
        if (defenderTaking) {
            table.forEach { p -> hands[defender] += p.attack; p.defense?.let { hands[defender] += it } }
            endTurn(taken = true)
        } else if (undefended() == 0) {
            discardCount += table.size * 2
            endTurn(taken = false)
        }
    }

    private fun endTurn(taken: Boolean) {
        table.clear(); passed.clear(); defenderTaking = false
        // Draw: attacker first, other throwers clockwise, defender last.
        val order = mutableListOf<Int>()
        var s = attacker
        repeat(playerCount) { if (s != defender) order += s; s = (s + 1) % playerCount }
        order += defender
        for (p in order) while (hands[p].size < rules.handSize && deck.isNotEmpty()) hands[p] += deck.removeFirst()

        for (p in 0 until playerCount) if (hands[p].isEmpty() && p !in finishedOrder) finishedOrder += p
        val remaining = (0 until playerCount).filter { it !in finishedOrder }
        turnNumber++
        if (remaining.size <= 1) {
            gameOver = true
            loser = remaining.firstOrNull()
            return
        }
        val oldDefender = defender
        attacker = if (!taken && oldDefender !in finishedOrder) oldDefender else nextActive(oldDefender)
        defender = nextActive(attacker)
    }

    private fun nextActive(from: Int): Int {
        var s = (from + 1) % playerCount
        repeat(playerCount) {
            if (s !in finishedOrder && hands[s].isNotEmpty()) return s
            s = (s + 1) % playerCount
        }
        return from
    }

    // ---------- views ----------
    fun viewFor(seat: Int): GameView = GameView(
        seat = seat, playerCount = playerCount, rules = rules, trump = trump,
        trumpCard = if (deck.isNotEmpty()) trumpCard else null,
        deckSize = deck.size,
        myHand = hands[seat].toList(),
        handSizes = hands.map { it.size },
        table = table.toList(), discardCount = discardCount,
        attacker = attacker, defender = defender, defenderTaking = defenderTaking,
        passed = passed.toList(), finished = finishedOrder.toList(),
        loser = loser, gameOver = gameOver, turnNumber = turnNumber,
        waitingFor = waitingFor(),
    )

    companion object {
        fun newGame(playerCount: Int, rules: Rules = Rules(), random: Random = Random.Default, firstAttacker: Int? = null): Game {
            require(playerCount in 2..6) { "2..6 игроков" }
            val cards = Deck.full36().shuffled(random)
            val trumpCard = cards.last() // bottom of the talon (with 6 players it ends up in the last hand)
            val hands = List(playerCount) { MutableList<Card>(0) { error("") } }
            val dq = ArrayDeque(cards)
            repeat(rules.handSize) { for (h in hands) h += dq.removeFirst() }
            val trump = trumpCard.suit
            val first = firstAttacker ?: run {
                // Holder of the lowest trump leads; if nobody has a trump, seat 0.
                val holders = hands.mapIndexedNotNull { i, h -> h.filter { it.suit == trump }.minOfOrNull { it.rank }?.let { i to it } }
                holders.minByOrNull { it.second }?.first ?: 0
            }
            return Game(playerCount, rules, dq, trump, trumpCard, hands, first)
        }

        /** Test helper: build a game from explicit hands/deck (deck's last card is the trump). */
        fun forTesting(
            hands: List<List<Card>>, deck: List<Card>, rules: Rules = Rules(), attacker: Int = 0, trump: Suit? = null,
            turnNumber: Int = 2,
        ): Game {
            val t = deck.lastOrNull() ?: Card(trump!!, 6)
            val g = Game(hands.size, rules, ArrayDeque(deck), trump ?: t.suit, t, hands.map { it.toMutableList() }, attacker)
            g.turnNumber = turnNumber
            return g
        }
    }
}
