package com.kuetcord.messaging.websocket;

import com.kuetcord.messaging.presence.PresenceService;
import com.kuetcord.messaging.presence.PresenceStatus;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chaos test: a client that negotiates heartbeats and then goes silent (laptop lid closed, Wi-Fi
 * roam without TCP FIN) is evicted by the server and its presence drops to OFFLINE.
 * <p>
 * Heartbeats are shortened to 1 s for this context only, so eviction (3 missed intervals) is
 * observed in ~3 s instead of ~30 s. The mechanism is identical to production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "kuetcord.websocket.heartbeat-ms=1000")
@Import(MessagingTestUsers.class)
class DeadSocketEvictionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private PresenceService presenceService;

    @Test
    @DisplayName("Silent socket is closed by the server after 3 missed heartbeats and the user goes OFFLINE")
    void shouldEvictZombieSocketWhenHeartbeatsMissed() throws Exception {
        TestUser user = users.verifiedStudent("Zombie Tab");
        CompletableFuture<String> connected = new CompletableFuture<>();
        CompletableFuture<CloseStatus> closed = new CompletableFuture<>();

        WebSocketSession socket = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                if (message.getPayload().startsWith("CONNECTED")) {
                    connected.complete(message.getPayload());
                }
                // Deliberately never answer: no heartbeats, no frames. A dead client.
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closed.complete(status);
            }
        }, "ws://localhost:" + port + "/ws/websocket").get(5, TimeUnit.SECONDS);

        socket.sendMessage(new TextMessage("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:1000,1000\n"
                + "Authorization:" + user.bearer() + "\n\n\u0000"));

        String connectedFrame = connected.get(5, TimeUnit.SECONDS);
        assertTrue(connectedFrame.contains("heart-beat:1000,1000"), connectedFrame);
        assertEquals(PresenceStatus.ONLINE, awaitStatus(user.id(), PresenceStatus.ONLINE));

        long silentSince = System.nanoTime();
        CloseStatus status = closed.get(10, TimeUnit.SECONDS);
        long evictedAfterMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - silentSince);

        assertTrue(evictedAfterMs < 6_000, "Eviction took " + evictedAfterMs + " ms; expected ~3 missed 1 s intervals");
        assertEquals(PresenceStatus.OFFLINE, awaitStatus(user.id(), PresenceStatus.OFFLINE),
                "Server closed the socket with " + status + " but presence was not cleared");
    }

    private PresenceStatus awaitStatus(Long userId, PresenceStatus expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && presenceService.statusOf(userId) != expected) {
            Thread.sleep(25);
        }
        return presenceService.statusOf(userId);
    }
}
