package ru.durak.net

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import java.net.*

data class RoomAd(val name: String, val host: String, val port: Int, val players: Int, val seats: Int)

/** UDP broadcast on the local network (Wi‑Fi / phone hotspot). No internet needed. */
object Discovery {
    private const val MAGIC = "DURAK1"

    /** Broadcast addresses of every active IPv4 interface (255.255.255.255 alone is unreliable on Android hotspots). */
    private fun broadcastTargets(): List<InetAddress> {
        val out = mutableListOf<InetAddress>()
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) ia.broadcast?.let { out += it }
            }
        }
        runCatching { out += InetAddress.getByName("255.255.255.255") }
        return out.distinct()
    }

    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().map { it.hostAddress!! }
    }.getOrDefault(emptyList())

    fun advertise(scope: CoroutineScope, info: () -> RoomAd, port: Int = DISCOVERY_PORT): Job = scope.launch(Dispatchers.IO) {
        DatagramSocket().use { sock ->
            sock.broadcast = true
            while (isActive) {
                val ad = info()
                val payload = "$MAGIC|${ad.name.replace('|', ' ')}|${ad.port}|${ad.players}|${ad.seats}".toByteArray()
                for (t in broadcastTargets()) runCatching { sock.send(DatagramPacket(payload, payload.size, t, port)) }
                delay(1000)
            }
        }
    }

    fun listen(port: Int = DISCOVERY_PORT): Flow<RoomAd> = callbackFlow {
        val sock = DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(port)); soTimeout = 1000 }
        val job = launch(Dispatchers.IO) {
            val buf = ByteArray(512)
            while (isActive) {
                val p = DatagramPacket(buf, buf.size)
                try { sock.receive(p) } catch (_: SocketTimeoutException) { continue } catch (_: Exception) { break }
                val parts = String(p.data, 0, p.length).split('|')
                if (parts.size < 5 || parts[0] != MAGIC) continue
                val ad = runCatching { RoomAd(parts[1], p.address.hostAddress!!, parts[2].toInt(), parts[3].toInt(), parts[4].toInt()) }.getOrNull() ?: continue
                trySend(ad)
            }
        }
        awaitClose { job.cancel(); sock.close() }
    }
}
