package ru.durak.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.durak.engine.Action
import ru.durak.engine.GameView
import ru.durak.engine.Rules

const val PROTOCOL_VERSION = 1
const val TCP_PORT = 47475
const val DISCOVERY_PORT = 47474

val NetJson = Json { classDiscriminator = "t"; encodeDefaults = true; ignoreUnknownKeys = true }

enum class SeatKind { EMPTY, HUMAN, BOT }

@Serializable
data class SeatInfo(val name: String = "", val avatar: Int = 0, val kind: SeatKind = SeatKind.EMPTY)

@Serializable
sealed interface ClientMsg {
    @Serializable data class Join(val name: String, val avatar: Int, val version: Int = PROTOCOL_VERSION) : ClientMsg
    @Serializable data class Act(val action: Action) : ClientMsg
    @Serializable data class Sticker(val id: Int) : ClientMsg
    @Serializable data object Leave : ClientMsg
}

@Serializable
sealed interface ServerMsg {
    /** Sent once after a successful join. */
    @Serializable data class Welcome(val seat: Int) : ServerMsg
    @Serializable data class Lobby(val seats: List<SeatInfo>, val rules: Rules, val roomName: String, val started: Boolean) : ServerMsg
    @Serializable data class State(val view: GameView, val seats: List<SeatInfo>) : ServerMsg
    @Serializable data class Sticker(val seat: Int, val id: Int) : ServerMsg
    @Serializable data class Error(val text: String) : ServerMsg
    /** Connection refused or closed by the host. */
    @Serializable data class Closed(val reason: String) : ServerMsg
}
