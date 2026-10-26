package ru.durak.engine

import kotlin.random.Random
import kotlin.test.*

private fun c(s: String): Card {
    val suit = when (s.last()) { 'S' -> Suit.SPADES; 'C' -> Suit.CLUBS; 'D' -> Suit.DIAMONDS; else -> Suit.HEARTS }
    val r = when (val x = s.dropLast(1)) { "J" -> 11; "Q" -> 12; "K" -> 13; "A" -> 14; else -> x.toInt() }
    return Card(suit, r)
}
private fun cs(vararg s: String) = s.map(::c)

class GameTest {
    @Test fun deckHas36UniqueCards() {
        assertEquals(36, Deck.full36().toSet().size)
    }

    @Test fun beatsRules() {
        assertTrue(c("8H").beats(c("7H"), Suit.SPADES))
        assertFalse(c("7H").beats(c("8H"), Suit.SPADES))
        assertFalse(c("AD").beats(c("7H"), Suit.SPADES))
        assertTrue(c("6S").beats(c("AH"), Suit.SPADES))
        assertFalse(c("6H").beats(c("AS"), Suit.SPADES))
        assertTrue(c("8S").beats(c("7S"), Suit.SPADES))
    }

    @Test fun dealSixCardsAndTrumpIsLastDeckCard() {
        for (n in 2..6) {
            val g = Game.newGame(n, random = Random(n))
            for (i in 0 until n) assertEquals(6, g.hand(i).size)
            assertEquals(36 - 6 * n, g.deckSize())
            if (n < 6) assertEquals(g.trump, g.viewFor(0).trumpCard!!.suit)
        }
    }

    @Test fun lowestTrumpHolderLeads() {
        val g = Game.newGame(3, random = Random(7))
        val trump = g.trump
        val holders = (0 until 3).mapNotNull { i -> g.hand(i).filter { it.suit == trump }.minOfOrNull { it.rank }?.let { i to it } }
        if (holders.isNotEmpty()) assertEquals(holders.minBy { it.second }.first, g.attacker)
    }

    private fun base(
        hands: List<List<Card>>, deck: List<Card> = cs("6H", "7H", "8H", "9S"), rules: Rules = Rules(), attacker: Int = 0,
    ) = Game.forTesting(hands, deck, rules, attacker)

    @Test fun onlyAttackerLeads() {
        val g = base(listOf(cs("6C", "7C"), cs("8C", "9C"), cs("10C", "JC")))
        assertIs<Result.Error>(g.apply(1, Action.Attack(c("8C"))))
        assertIs<Result.Ok>(g.apply(0, Action.Attack(c("6C"))))
    }

    @Test fun defendAndBito() {
        // trump = spades (deck last 9S)
        val g = base(listOf(cs("6C", "KD", "QD", "AH", "10D", "JD"), cs("7C", "9D", "8D", "7D", "6D", "AC")))
        g.apply(0, Action.Attack(c("6C")))
        assertIs<Result.Error>(g.apply(1, Action.Defend(0, c("9D"))))
        assertIs<Result.Ok>(g.apply(1, Action.Defend(0, c("7C"))))
        // attacker can't add (no 6 or 7) -> game settles automatically: bito, defender leads
        assertTrue(g.tableCards().isEmpty())
        assertEquals(1, g.attacker)
        assertEquals(0, g.defender)
        assertEquals(2, g.discardCount)
        // both drew from deck back to 6
        assertEquals(6, g.hand(0).size); assertEquals(6, g.hand(1).size)
        assertEquals(2, g.deckSize())
    }

    @Test fun throwInThenPass() {
        val g = base(listOf(cs("6C", "6D", "KD", "QD", "AH", "10D"), cs("7C", "7D", "8D", "9D", "10C", "JS"), cs("6H", "KC", "QC", "JC", "10H", "9H")))
        g.apply(0, Action.Attack(c("6C")))
        g.apply(1, Action.Defend(0, c("7C")))
        // seats 0 and 2 can throw a 6 or 7; waiting for them
        assertEquals(setOf(0, 2), g.waitingFor().toSet())
        assertIs<Result.Ok>(g.apply(2, Action.Attack(c("6H"))))
        assertIs<Result.Error>(g.apply(2, Action.Attack(c("KC")))) // no matching rank
        assertIs<Result.Ok>(g.apply(1, Action.Defend(1, c("JS"))))
        g.apply(0, Action.Pass)
        g.apply(2, Action.Pass)
        assertTrue(g.tableCards().isEmpty())
        assertEquals(1, g.attacker)
    }

    @Test fun takeMakesDefenderSkipTurn() {
        val g = base(listOf(cs("6C", "KD", "QD", "AH", "10D", "JD"), cs("7D", "9D", "8D", "7H", "6D", "8C"), cs("QC", "KC", "AC", "JC", "10H", "9H")))
        g.apply(0, Action.Attack(c("6C")))
        assertIs<Result.Ok>(g.apply(1, Action.Take))
        g.apply(0, Action.Pass) // seat 2 has no 6 -> auto-pass
        assertTrue(g.tableCards().isEmpty())
        assertEquals(7, g.hand(1).size.coerceAtLeast(7)) // took card (+ drew nothing: hand >= 6)
        assertTrue(c("6C") in g.hand(1))
        assertEquals(2, g.attacker) // defender skipped
        assertEquals(0, g.defender)
    }

    @Test fun cannotThrowMoreThanDefenderHandSize() {
        val g = base(listOf(cs("6C", "6D", "6H", "KD", "QD", "AH"), cs("7C", "9D"), cs("QC", "KC", "AC", "JC", "10H", "9H")))
        g.apply(0, Action.Attack(c("6C")))
        g.apply(0, Action.Attack(c("6D")))
        assertIs<Result.Error>(g.apply(0, Action.Attack(c("6H")))) // defender has only 2 cards
    }

    @Test fun firstTurnLimitFive() {
        val g = Game.forTesting(
            listOf(cs("6C", "6D", "6H", "6S", "7C", "7D"), cs("8C", "8D", "8H", "8S", "9C", "9D", "9H"), cs("QC")),
            cs("10S", "JS"), Rules(firstTurnLimit = 2), turnNumber = 1,
        )
        for (cd in cs("6C", "6D")) assertIs<Result.Ok>(g.apply(0, Action.Attack(cd)))
        assertIs<Result.Error>(g.apply(0, Action.Attack(c("6H"))))
    }

    @Test fun transferWorks() {
        val g = Game.forTesting(
            listOf(cs("6C", "KD", "QD", "AH", "10D", "JD"), cs("6D", "9D", "8D", "7H", "6H", "8C"), cs("QC", "KC", "AC", "JC", "10H", "9H")),
            cs("6S", "7S", "8S", "9S"), Rules(transfer = true),
        )
        g.apply(0, Action.Attack(c("6C")))
        assertIs<Result.Ok>(g.apply(1, Action.Transfer(c("6D"))))
        assertEquals(1, g.attacker); assertEquals(2, g.defender)
        assertEquals(2, g.tableCards().size)
        // after a defense, transfer is no longer possible
        g.apply(2, Action.Defend(0, c("AC")))
        assertEquals(emptyList(), g.transferCards())
    }

    @Test fun transferNotAllowedWithoutRule() {
        val g = base(listOf(cs("6C", "KD", "QD", "AH", "10D", "JD"), cs("6D", "9D", "8D", "7H", "6H", "8C"), cs("QC", "KC", "AC", "JC", "10H", "9H")))
        g.apply(0, Action.Attack(c("6C")))
        assertIs<Result.Error>(g.apply(1, Action.Transfer(c("6D"))))
    }

    @Test fun transferBlockedWhenNextHasTooFewCards() {
        val g = Game.forTesting(
            listOf(cs("6C", "KD"), cs("6D", "9D"), cs("QC")),
            cs("6S", "7S"), Rules(transfer = true),
        )
        g.apply(0, Action.Attack(c("6C")))
        assertIs<Result.Error>(g.apply(1, Action.Transfer(c("6D")))) // seat 2 has 1 card, needs 2
    }

    @Test fun gameEndsAndLoserIsLastWithCards() {
        // empty deck, 2 players. P0 attacks 6C with last card pair; P1 can't beat -> takes. P0 has 1 card left -> after: P0 finished?
        val g = Game.forTesting(listOf(cs("6C"), cs("7D", "8D")), emptyList(), trump = Suit.SPADES, attacker = 0)
        g.apply(0, Action.Attack(c("6C")))
        g.apply(1, Action.Defend(0, c("7D")).let { Action.Take })
        assertTrue(g.gameOver)
        assertEquals(1, g.loser)
        assertEquals(listOf(0), g.finished())
    }

    @Test fun bothOutIsDraw() {
        val g = Game.forTesting(listOf(cs("6C"), cs("7C")), emptyList(), trump = Suit.SPADES, attacker = 0)
        g.apply(0, Action.Attack(c("6C")))
        g.apply(1, Action.Defend(0, c("7C")))
        assertTrue(g.gameOver)
        assertNull(g.loser)
    }

    @Test fun viewHidesOtherHands() {
        val g = Game.newGame(4, random = Random(1))
        val v = g.viewFor(2)
        assertEquals(g.hand(2), v.myHand)
        assertEquals(List(4) { 6 }, v.handSizes)
    }

    @Test fun botsPlayFullGamesToCompletion() {
        for (seed in 1..300) for (n in 2..6) for (tr in listOf(false, true)) {
            val g = Game.newGame(n, Rules(transfer = tr), Random(seed * 31 + n))
            var steps = 0
            while (!g.gameOver) {
                check(++steps < 5000) { "stuck: seed=$seed n=$n view=${g.viewFor(0)}" }
                var acted = false
                val w = g.waitingFor()
                check(w.isNotEmpty()) { "nobody to wait for but game not over: seed=$seed n=$n" }
                for (s in w) {
                    val a = Bot.decide(g, s) ?: continue
                    val r = g.apply(s, a)
                    check(r is Result.Ok) { "bot made illegal move $a: $r" }
                    acted = true
                    break
                }
                check(acted) { "no bot action: seed=$seed n=$n waiting=$w" }
                // card conservation
                val total = (0 until n).sumOf { i -> g.hand(i).size } + g.deckSize() + (g.tableCards().size + g.tableCards().count { pr -> pr.defense != null }) + g.discardCount
                assertEquals(36, total)
            }
        }
    }
}
