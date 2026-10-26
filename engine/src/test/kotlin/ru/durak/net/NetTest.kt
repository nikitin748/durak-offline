package ru.durak.net

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.durak.engine.*
import kotlin.random.Random
import kotlin.test.*

class NetTest {
    @Test fun lobbyJoinStateAndDropoutBecomesBot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = GameSession(scope, "Тест", botDelayMs = 1L..3L, random = Random(5))
        session.configure(3, Rules())
        val hostConn = LocalConnection(session, "Хост", 1)
        val tcp = TcpHost(session, scope, port = 47611); tcp.start()
        val client = TcpClient(scope)
        client.connect("127.0.0.1", 47611, ClientMsg.Join("Гость", 2))

        val welcome = withTimeout(5000) { client.incoming.first { it is ServerMsg.Welcome } } as ServerMsg.Welcome
        assertEquals(1, welcome.seat)
        withTimeout(5000) { while (session.humanCount() < 2) delay(10) }
        assertTrue(session.start()) // third seat filled by a bot

        val st = withTimeout(5000) { hostConn.incoming.first { it is ServerMsg.State } } as ServerMsg.State
        assertEquals(3, st.view.playerCount)
        assertEquals(SeatKind.BOT, st.seats[2].kind)
        assertEquals(6, st.view.myHand.size)

        // the guest sees only their own hand
        val gst = withTimeout(5000) { client.incoming.first { it is ServerMsg.State } } as ServerMsg.State
        assertEquals(1, gst.view.seat)

        // both humans drop -> bots finish the game
        client.close()
        hostConn.close()
        withTimeout(60000) { while (!session.finishedForTest()) delay(20) }
        tcp.stop(); scope.cancel()
    }

    @Test fun fullRoomIsRefused() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = GameSession(scope, "Тест")
        session.configure(2, Rules())
        LocalConnection(session, "A", 0)
        val tcp = TcpHost(session, scope, port = 47612); tcp.start()
        session.join("B", 0) { }
        val c = TcpClient(scope)
        c.connect("127.0.0.1", 47612, ClientMsg.Join("C", 1))
        val m = withTimeout(5000) { c.incoming.first { it is ServerMsg.Closed } }
        assertIs<ServerMsg.Closed>(m)
        tcp.stop(); scope.cancel()
    }
}
