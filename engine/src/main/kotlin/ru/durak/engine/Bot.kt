package ru.durak.engine

/** Simple heuristic bot. Works on the host with full game access, but only uses what a player could see. */
object Bot {
    private fun cost(c: Card, trump: Suit) = c.rank + if (c.suit == trump) 100 else 0

    /** Returns the action the bot wants to take now, or null if it has nothing to do. */
    fun decide(game: Game, seat: Int): Action? {
        if (game.gameOver || !game.isActive(seat)) return null
        val trump = game.trump
        if (seat == game.defender) {
            if (game.canTake() && !game.defenderTaking) {
                val transfer = game.transferCards().minByOrNull { cost(it, trump) }
                if (transfer != null && transfer.suit != trump) return Action.Transfer(transfer)
                // Defend each undefended card with the cheapest beating card, if all can be beaten.
                val plan = planDefense(game)
                return if (plan != null) plan.first().let { Action.Defend(it.first, it.second) } else Action.Take
            }
            return null
        }
        if (seat !in game.waitingFor()) return null
        val table = game.tableCards()
        val options = game.attackCards(seat)
        if (table.isEmpty()) {
            return options.minByOrNull { c -> cost(c, trump) - options.count { it.rank == c.rank } }?.let { Action.Attack(it) }
        }
        // Throw in only cheap non-trump cards; keep trumps. When the defender is taking, dump anything cheap.
        val cheap = options.filter { it.suit != trump || (game.deckSize() == 0 && it.rank < 11) }
            .filter { it.rank <= 10 || game.defenderTaking || game.deckSize() == 0 }
            .minByOrNull { cost(it, trump) }
        return if (cheap != null) Action.Attack(cheap) else Action.Pass
    }

    private fun planDefense(game: Game): List<Pair<Int, Card>>? {
        val hand = game.hand(game.defender).toMutableList()
        val plan = mutableListOf<Pair<Int, Card>>()
        game.tableCards().forEachIndexed { i, p ->
            if (p.defense != null) return@forEachIndexed
            val c = hand.filter { it.beats(p.attack, game.trump) }.minByOrNull { cost(it, game.trump) } ?: return null
            hand.remove(c); plan += i to c
        }
        // Don't burn high trumps on a trivial small attack when the talon is still full.
        if (game.deckSize() > 6 && plan.any { it.second.suit == game.trump && it.second.rank >= 12 }) return null
        return plan.ifEmpty { null }
    }
}
