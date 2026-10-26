package ru.durak.net

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.*

/** What the UI talks to, whether the room is local (host) or remote (client). */
interface Connection {
    val incoming: Flow<ServerMsg>
    fun send(msg: ClientMsg)
    fun close()
}

/** TCP server on the host phone: one socket per remote player, JSON lines. */
class TcpHost(private val session: GameSession, private val scope: CoroutineScope, val port: Int = TCP_PORT) {
    private var server: ServerSocket? = null
    private val sockets = mutableSetOf<Socket>()

    fun start() {
        val ss = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) }
        server = ss
        scope.launch(Dispatchers.IO) {
            while (isActive && !ss.isClosed) {
                val s = try { ss.accept() } catch (_: Exception) { break }
                launch { serve(s) }
            }
        }
    }

    private suspend fun serve(sock: Socket) = withContext(Dispatchers.IO) {
        synchronized(sockets) { sockets += sock }
        sock.tcpNoDelay = true
        val out = Channel<String>(Channel.UNLIMITED)
        val writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8))
        val writerJob = launch {
            try { for (line in out) { writer.write(line); writer.write("\n"); writer.flush() } } catch (_: Exception) { }
        }
        val link = SeatLink { out.trySend(NetJson.encodeToString(ServerMsg.serializer(), it)) }
        var seat: Int? = null
        try {
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                val msg = try { NetJson.decodeFromString(ClientMsg.serializer(), line) } catch (_: Exception) { continue }
                val s = seat
                if (s == null) {
                    if (msg is ClientMsg.Join) {
                        if (msg.version != PROTOCOL_VERSION) { link.send(ServerMsg.Closed("Разные версии приложения")); break }
                        seat = session.join(msg.name, msg.avatar, link)
                        if (seat == null) { link.send(ServerMsg.Closed("Комната заполнена или игра уже идёт")); break }
                    }
                } else session.handle(s, msg)
            }
        } catch (_: Exception) {
        } finally {
            seat?.let { session.leave(it) }
            out.close()
            withTimeoutOrNull(500) { writerJob.join() }
            runCatching { sock.close() }
            synchronized(sockets) { sockets -= sock }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        synchronized(sockets) { sockets.toList() }.forEach { runCatching { it.close() } }
    }
}

class TcpClient(private val scope: CoroutineScope) : Connection {
    private val ch = Channel<ServerMsg>(Channel.UNLIMITED)
    override val incoming: Flow<ServerMsg> = ch.receiveAsFlow()
    private val out = Channel<String>(Channel.UNLIMITED)
    private var sock: Socket? = null

    /** Connects and sends [join]; throws on failure. */
    suspend fun connect(host: String, port: Int, join: ClientMsg.Join) = withContext(Dispatchers.IO) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 5000)
        s.tcpNoDelay = true
        sock = s
        val writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
        scope.launch(Dispatchers.IO) {
            try { for (line in out) { writer.write(line); writer.write("\n"); writer.flush() } } catch (_: Exception) { }
        }
        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    val m = try { NetJson.decodeFromString(ServerMsg.serializer(), line) } catch (_: Exception) { continue }
                    ch.trySend(m)
                }
            } catch (_: Exception) { }
            ch.trySend(ServerMsg.Closed("Соединение потеряно"))
            ch.close()
        }
        send(join)
    }

    override fun send(msg: ClientMsg) { out.trySend(NetJson.encodeToString(ClientMsg.serializer(), msg)) }
    override fun close() { runCatching { send(ClientMsg.Leave) }; out.close(); runCatching { sock?.close() } }
}

/** Connection for the host's own player: no sockets, talks to the session directly. */
class LocalConnection(private val session: GameSession, name: String, avatar: Int) : Connection {
    private val ch = Channel<ServerMsg>(Channel.UNLIMITED)
    override val incoming: Flow<ServerMsg> = ch.receiveAsFlow()
    val seat: Int = session.join(name, avatar) { ch.trySend(it) } ?: error("host seat unavailable")
    override fun send(msg: ClientMsg) = session.handle(seat, msg)
    override fun close() { session.leave(seat); ch.close() }
}
