package ru.durak.engine

import kotlinx.serialization.Serializable

@Serializable
data class Rules(
    /** Переводной дурак. */
    val transfer: Boolean = false,
    val handSize: Int = 6,
    /** Max attack cards in the very first turn of the game (classic: 5). */
    val firstTurnLimit: Int = 5,
    /** 24, 36 or 52. */
    val deckSize: Int = 36,
    /** Only the defender's neighbours may throw in ("соседи"); otherwise everyone ("все"). */
    val neighborsOnly: Boolean = false,
)

@Serializable
data class TablePair(val attack: Card, val defense: Card? = null, /** Seat that played the attack card. */ val attackBy: Int = -1)

@Serializable
sealed interface Action {
    /** Lead or throw in a card. */
    @Serializable data class Attack(val card: Card) : Action
    /** Beat the attack card at [index] on the table. */
    @Serializable data class Defend(val index: Int, val card: Card) : Action
    /** Переводной: pass the attack on to the next player with a same-rank card. */
    @Serializable data class Transfer(val card: Card) : Action
    /** Defender picks the cards up. */
    @Serializable data object Take : Action
    /** Attacker has nothing more to add ("Бито" / "Пас"). */
    @Serializable data object Pass : Action
}

@Serializable
data class DefendOption(val index: Int, val card: Card)

/** Moves currently legal for one seat, so a client UI can highlight cards without re-implementing rules. */
@Serializable
data class Legal(
    val attack: List<Card> = emptyList(),
    val defend: List<DefendOption> = emptyList(),
    val transfer: List<Card> = emptyList(),
    val canTake: Boolean = false,
    val canPass: Boolean = false,
)

/** What the table looks like to one particular seat; safe to send over the network. */
@Serializable
data class GameView(
    val seat: Int,
    val playerCount: Int,
    val rules: Rules,
    val trump: Suit,
    /** Visible trump card at the bottom of the talon, null once it has been drawn. */
    val trumpCard: Card?,
    val deckSize: Int,
    val myHand: List<Card>,
    val handSizes: List<Int>,
    val table: List<TablePair>,
    val discardCount: Int,
    val attacker: Int,
    val defender: Int,
    val defenderTaking: Boolean,
    val passed: List<Int>,
    val finished: List<Int>,
    val loser: Int?,
    val gameOver: Boolean,
    val turnNumber: Int,
    /** Seats whose input the game is waiting for. */
    val waitingFor: List<Int>,
    val legal: Legal = Legal(),
)

sealed interface Result {
    data object Ok : Result
    data class Error(val message: String) : Result
}
