package ru.durak.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Suit(val symbol: String) {
    SPADES("♠"), CLUBS("♣"), DIAMONDS("♦"), HEARTS("♥")
}

/** Rank 6..14 (11=В, 12=Д, 13=К, 14=Т). */
@Serializable
data class Card(val suit: Suit, val rank: Int) {
    init { require(rank in 6..14) { "rank $rank" } }

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
    fun full36(): List<Card> = Suit.entries.flatMap { s -> (6..14).map { Card(s, it) } }
}
