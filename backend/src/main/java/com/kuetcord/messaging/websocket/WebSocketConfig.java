package com.kuetcord.messaging.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;

/**
 * STOMP over WebSocket with bidirectional heartbeats, bulkhead thread pools and slow-consumer eviction.
 * <ul>
 *   <li><b>Heartbeats (ADR-005):</b> broker advertises {@code heart-beat:10000,10000}. When a client stops
 *       sending frames (laptop lid closed, Wi-Fi roam without TCP FIN), Spring's simple broker closes the
 *       session after 3 missed intervals (≤ 30 s), freeing the socket and triggering presence cleanup.</li>
 *   <li><b>Bulkheads:</b> {@code ws-inbound-}, {@code ws-outbound-} and {@code ws-heartbeat-} pools are
 *       isolated from HTTP and from each other; the heartbeat scheduler is deliberately <i>not</i> a bean so
 *       future {@code @Scheduled} jobs can never starve ping/eviction.</li>
 *   <li><b>Slow consumers:</b> a client buffering more than 512 KB, or taking longer than 10 s to accept a
 *       frame, is disconnected so it cannot exhaust heap on the 512 MB Render instance.</li>
 * </ul>
 * The single-instance simple broker fits the free tier; multi-instance would need a broker relay.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final long HEARTBEAT_MS = 10_000;

    private final StompAuthChannelInterceptor authInterceptor;
    private final StompDestinationGuard destinationGuard;
    private final StompSecurityErrorHandler errorHandler;
    private final String[] allowedOrigins;
    /** {@code kuetcord.websocket.heartbeat-ms}; 10 s in every real profile, shortened only by the chaos test. */
    private final long heartbeatMs;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor,
                           StompDestinationGuard destinationGuard,
                           StompSecurityErrorHandler errorHandler,
                           @Value("${kuetcord.cors.allowed-origins:http://localhost:5173}") String allowedOriginsRaw,
                           @Value("${kuetcord.websocket.heartbeat-ms:" + HEARTBEAT_MS + "}") long heartbeatMs) {
        this.heartbeatMs = heartbeatMs;
        this.authInterceptor = authInterceptor;
        this.destinationGuard = destinationGuard;
        this.errorHandler = errorHandler;
        this.allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket clients connect to /ws/websocket; SockJS fallback is served at /ws.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS()
                .setHeartbeatTime(25_000);
        registry.setErrorHandler(errorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setPoolSize(2);
        heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-");
        heartbeatScheduler.setDaemon(true);
        heartbeatScheduler.initialize();

        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{heartbeatMs, heartbeatMs})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        // Keep per-session frame order (RECEIPT before later broadcasts) across the outbound pool.
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Order matters: authenticate CONNECT first, then authorize SUBSCRIBE/SEND.
        registration.interceptors(authInterceptor, destinationGuard);
        registration.taskExecutor()
                .corePoolSize(8)
                .maxPoolSize(32)
                .queueCapacity(1000)
                .keepAliveSeconds(60);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(8)
                .maxPoolSize(32)
                .queueCapacity(2000)
                .keepAliveSeconds(60);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(10 * 1000)
                .setSendBufferSizeLimit(512 * 1024)
                .setMessageSizeLimit(64 * 1024);
    }
}
