package ru.durak.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Suit(val symbol: String) {
    SPADES("♠"), CLUBS("♣"), DIAMONDS("♦"), HEARTS("♥")
}

/** Rank 2..14 (11=В, 12=Д, 13=К, 14=Т). */
@Serializable
data class Card(val suit: Suit, val rank: Int) {
    init { require(rank in 2..14) { "rank $rank" } }

    val rankLabel: String
        get() = when (rank) { 11 -> "В"; 12 -> "Д"; 13 -> "К"; 14 -> "Т"; else -> rank.toString() }

    override fun toString() = "$rankLabel${suit.symbol}"

    fun beats(other: Card, trump: Suit): Boolean = when {
        suit == other.suit -> rank > other.rank
        suit == trump -> true
        else -> false
    }
}

object Deck {
    fun full36(): List<Card> = of(36)

    /** 24 (9..A), 36 (6..A) or 52 (2..A) cards. */
    fun of(size: Int): List<Card> {
        val from = when (size) { 24 -> 9; 36 -> 6; 52 -> 2; else -> error("deck $size") }
        return Suit.entries.flatMap { s -> (from..14).map { Card(s, it) } }
    }

    /** Most players a deck can deal six cards to. */
    fun maxPlayers(size: Int) = minOf(6, size / 6)
}
