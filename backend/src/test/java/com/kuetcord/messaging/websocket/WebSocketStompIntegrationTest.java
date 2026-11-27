package com.kuetcord.messaging.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.messaging.dto.CreateGroupRequest;
import com.kuetcord.messaging.service.ConversationService;
import com.kuetcord.notifications.outbox.OutboxProcessor;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end STOMP over a real WebSocket on a random port: JWT CONNECT, heartbeat negotiation,
 * RECEIPT-after-commit, broadcast to authorized members only, idempotent retry, and error queue.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MessagingTestUsers.class)
class WebSocketStompIntegrationTest {

    private static final long TIMEOUT_S = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private SimpUserRegistry userRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxProcessor outboxProcessor;

    private ThreadPoolTaskScheduler clientScheduler;
    private WebSocketStompClient stompClient;

    private TestUser alice;
    private TestUser bob;
    private TestUser carol;
    private TestUser dave;
    private long groupId;

    @BeforeEach
    void setUp() {
        clientScheduler = new ThreadPoolTaskScheduler();
        clientScheduler.setPoolSize(2);
        clientScheduler.initialize();

        MappingJackson2MessageConverter json = new MappingJackson2MessageConverter();
        json.setObjectMapper(objectMapper);
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new CompositeMessageConverter(
                List.of(new StringMessageConverter(), new ByteArrayMessageConverter(), json)));
        stompClient.setTaskScheduler(clientScheduler);
        stompClient.setDefaultHeartbeat(new long[]{WebSocketConfig.HEARTBEAT_MS, WebSocketConfig.HEARTBEAT_MS});
        stompClient.setReceiptTimeLimit(TimeUnit.SECONDS.toMillis(TIMEOUT_S));

        alice = users.verifiedStudent("Alice");
        bob = users.verifiedStudent("Bob");
        carol = users.verifiedStudent("Carol");
        dave = users.verifiedStudent("Dave Outsider");
        groupId = conversationService.createGroup(alice.id(),
                new CreateGroupRequest("CSE 3220 Lab Team", List.of(bob.id(), carol.id()))).id();
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
        clientScheduler.shutdown();
    }

    @Test
    @DisplayName("CONNECT negotiates bidirectional 10s/10s heartbeats")
    void shouldExchangeBidirectionalStompHeartbeats() throws Exception {
        Client client = connect(alice);

        assertArrayEquals(new long[]{10_000, 10_000}, client.handler.connectedHeaders.get(TIMEOUT_S, TimeUnit.SECONDS).getHeartbeat());
        client.session.disconnect();
    }

    @Test
    @DisplayName("CONNECT without a token, or with a forged token, never reaches CONNECTED")
    void shouldRejectUnauthenticatedConnect() throws Exception {
        for (String header : new String[]{null, "Bearer forged.jwt.token"}) {
            RecordingHandler handler = new RecordingHandler();
            StompHeaders connectHeaders = new StompHeaders();
            if (header != null) {
                connectHeaders.add("Authorization", header);
            }
            CompletableFuture<StompSession> future = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders, handler);

            assertThrows(Exception.class, () -> future.get(3, TimeUnit.SECONDS));
            assertNotNull(handler.errors.poll(TIMEOUT_S, TimeUnit.SECONDS), "Server must answer with ERROR / close");
        }
    }

    @Test
    @DisplayName("Sender gets RECEIPT after commit; members receive the broadcast; forged senderId is ignored; retry is not re-broadcast")
    void shouldBroadcastToGroupMembersWithReceipt() throws Exception {
        Client aliceClient = connect(alice);
        Client bobClient = connect(bob);
        BlockingQueue<Map<String, Object>> bobInbox = subscribe(bobClient, bob, "/topic/conversations/" + groupId);

        String key = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of("content", "Hey team, the revision notes for CT 2 are ready!",
                "idempotencyKey", key, "senderId", dave.id());

        assertTrue(sendWithReceipt(aliceClient, payload), "RECEIPT expected after persistence");
        Map<String, Object> delivered = bobInbox.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertNotNull(delivered, "Bob must receive Alice's message live");
        assertEquals(alice.id().intValue(), ((Number) delivered.get("senderId")).intValue(), "senderId comes from the JWT, never the payload");
        assertEquals("Hey team, the revision notes for CT 2 are ready!", delivered.get("content"));
        assertEquals(key, delivered.get("idempotencyKey"));

        // Network retry of the same frame: ACKed again, but no duplicate row and no second broadcast.
        assertTrue(sendWithReceipt(aliceClient, payload));
        assertNull(bobInbox.poll(1, TimeUnit.SECONDS), "A retry must not be broadcast twice");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND idempotency_key = ?", Integer.class, groupId, key));

        aliceClient.session.disconnect();
        bobClient.session.disconnect();
    }

    @Test
    @DisplayName("A non-member's SUBSCRIBE is rejected with an ERROR frame and they receive nothing")
    void shouldRejectSubscriptionFromNonMember() throws Exception {
        Client daveClient = connect(dave);
        daveClient.session.subscribe("/topic/conversations/" + groupId, new MapFrameHandler(new LinkedBlockingQueue<>()));

        Object error = daveClient.handler.errors.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertNotNull(error, "Unauthorized subscription must produce an ERROR frame");
        assertTrue(String.valueOf(error).contains("Not allowed to subscribe"), "Got: " + error);
    }

    @Test
    @DisplayName("Rejected SEND (oversized content) is reported privately on /user/queue/errors with the receipt id")
    void shouldReportRejectedSendOnUserErrorQueue() throws Exception {
        Client aliceClient = connect(alice);
        BlockingQueue<Map<String, Object>> errors = subscribe(aliceClient, alice, "/user/queue/errors");

        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/conversations/" + groupId + "/messages");
        headers.setReceipt("msg-" + UUID.randomUUID());
        aliceClient.session.send(headers, Map.of("content", "x".repeat(2001), "idempotencyKey", UUID.randomUUID().toString()));

        Map<String, Object> error = errors.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertNotNull(error, "Sender must be told the message failed so the UI can show Retry");
        assertEquals("BAD_REQUEST", error.get("code"));
        assertEquals(headers.getReceipt(), error.get("receiptId"));
        aliceClient.session.disconnect();
    }

    @Test
    @DisplayName("Multi-tab presence over real sockets: ONLINE on first tab, still ONLINE after closing one of two, OFFLINE after both")
    void shouldTrackMultiTabPresenceOverRealSockets() throws Exception {
        Client watcher = connect(dave);
        BlockingQueue<Map<String, Object>> presence = subscribe(watcher, dave, "/topic/presence");

        Client tab1 = connect(alice);
        assertEquals("ONLINE", nextPresenceFor(presence, alice).get("status"));
        Client tab2 = connect(alice);

        tab1.session.disconnect();
        assertNull(nextPresenceFor(presence, alice, 1), "Closing 1 of 2 tabs must not broadcast OFFLINE");

        tab2.session.disconnect();
        assertEquals("OFFLINE", nextPresenceFor(presence, alice).get("status"));
        watcher.session.disconnect();
    }

    @Test
    @DisplayName("Typing pings reach other members on the typing topic and are throttled server-side")
    void shouldBroadcastThrottledTypingIndicators() throws Exception {
        Client aliceClient = connect(alice);
        Client bobClient = connect(bob);
        BlockingQueue<Map<String, Object>> typing = subscribe(bobClient, bob, "/topic/conversations/" + groupId + "/typing");

        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/conversations/" + groupId + "/typing");
        aliceClient.session.send(headers, Map.of("conversationId", groupId));
        aliceClient.session.send(headers, Map.of("conversationId", groupId));

        Map<String, Object> event = typing.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertNotNull(event, "Bob should see Alice typing");
        assertEquals(alice.id().intValue(), ((Number) event.get("userId")).intValue());
        assertNull(typing.poll(1, TimeUnit.SECONDS), "Second ping within 2 s is throttled");

        aliceClient.session.disconnect();
        bobClient.session.disconnect();
    }

    // ----------------------------------------------------------------- helpers

    @Test
    @DisplayName("Outbox worker pushes the committed notification to /user/queue/notifications of the recipient only")
    void shouldPushNotificationToPrivateUserQueue() throws Exception {
        Client aliceClient = connect(alice);
        Client bobClient = connect(bob);
        Client daveClient = connect(dave);
        BlockingQueue<Map<String, Object>> bobQueue = subscribe(bobClient, bob, "/user/queue/notifications");
        BlockingQueue<Map<String, Object>> aliceQueue = subscribe(aliceClient, alice, "/user/queue/notifications");
        BlockingQueue<Map<String, Object>> daveQueue = subscribe(daveClient, dave, "/user/queue/notifications");

        assertTrue(sendWithReceipt(aliceClient, Map.of("content", "Viva slides are in the drive",
                "idempotencyKey", UUID.randomUUID().toString())));
        outboxProcessor.drainAll();

        Map<String, Object> pushed = bobQueue.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertNotNull(pushed, "Bob must get a live notification after the worker commits");
        assertEquals("MESSAGE", pushed.get("type"));
        assertEquals("Alice in CSE 3220 Lab Team", pushed.get("title"));
        assertEquals("Viva slides are in the drive", pushed.get("preview"));
        assertEquals(groupId, ((Number) pushed.get("targetId")).longValue());
        assertEquals(Boolean.FALSE, pushed.get("read"));
        assertNull(aliceQueue.poll(500, TimeUnit.MILLISECONDS), "The sender is never notified about their own message");
        assertNull(daveQueue.poll(200, TimeUnit.MILLISECONDS), "Non-members receive nothing");

        // Replaying the drain (e.g. after a crash) inserts nothing new and pushes nothing twice.
        outboxProcessor.drainAll();
        assertNull(bobQueue.poll(500, TimeUnit.MILLISECONDS));

        aliceClient.session.disconnect();
        bobClient.session.disconnect();
        daveClient.session.disconnect();
    }

    private Map<String, Object> nextPresenceFor(BlockingQueue<Map<String, Object>> events, TestUser user) throws InterruptedException {
        Map<String, Object> event = nextPresenceFor(events, user, TIMEOUT_S);
        assertNotNull(event, "No presence event for user " + user.id());
        return event;
    }

    /** Skips other users' presence events (other tests' sessions may still be closing). */
    private Map<String, Object> nextPresenceFor(BlockingQueue<Map<String, Object>> events, TestUser user, long timeoutSeconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            Map<String, Object> event = events.poll(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())), TimeUnit.MILLISECONDS);
            if (event != null && ((Number) event.get("userId")).longValue() == user.id()) {
                return event;
            }
        }
        return null;
    }

    private boolean sendWithReceipt(Client client, Map<String, Object> payload) throws InterruptedException {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/conversations/" + groupId + "/messages");
        headers.setReceipt("msg-" + UUID.randomUUID());
        CountDownLatch receipt = new CountDownLatch(1);
        client.session.send(headers, payload).addReceiptTask(receipt::countDown);
        return receipt.await(TIMEOUT_S, TimeUnit.SECONDS);
    }

    /** Subscribes and waits until the server-side registry shows the subscription (simple broker has no SUBSCRIBE receipts). */
    private BlockingQueue<Map<String, Object>> subscribe(Client client, TestUser user, String destination) throws InterruptedException {
        BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        client.session.subscribe(destination, new MapFrameHandler(inbox));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S);
        while (System.nanoTime() < deadline) {
            boolean registered = userRegistry.getUser(String.valueOf(user.id())) != null
                    && userRegistry.getUser(String.valueOf(user.id())).getSessions().stream()
                    .anyMatch(s -> s.getSubscriptions().stream().anyMatch(sub -> sub.getDestination().equals(destination)));
            if (registered) {
                return inbox;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Subscription to " + destination + " was never registered");
    }

    private Client connect(TestUser user) throws Exception {
        RecordingHandler handler = new RecordingHandler();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", user.bearer());
        StompSession session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders, handler)
                .get(TIMEOUT_S, TimeUnit.SECONDS);
        session.setAutoReceipt(false);
        return new Client(session, handler);
    }

    private String url() {
        return "ws://localhost:" + port + "/ws/websocket";
    }

    private record Client(StompSession session, RecordingHandler handler) {
    }

    private static class RecordingHandler extends StompSessionHandlerAdapter {
        final CompletableFuture<StompHeaders> connectedHeaders = new CompletableFuture<>();
        final BlockingQueue<Object> errors = new LinkedBlockingQueue<>();

        @Override
        public void afterConnected(StompSession session, StompHeaders headers) {
            connectedHeaders.complete(headers);
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            // Only ERROR frames reach the session-level handler.
            errors.add(headers.getFirst("message"));
        }

        @Override
        public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            errors.add(exception);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            errors.add(exception);
        }
    }

    private record MapFrameHandler(BlockingQueue<Map<String, Object>> inbox) implements StompFrameHandler {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            inbox.add((Map<String, Object>) payload);
        }
    }
}
