package com.kuetcord.messaging.websocket;

import com.kuetcord.auth.service.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP {@code CONNECT} frame using the same KUETcord JWT as the REST API.
 * <p>
 * The HTTP handshake on {@code /ws} is anonymous (browsers cannot set headers on WebSocket
 * upgrades), so identity is established here from the native header
 * {@code Authorization: Bearer <token>}. Missing, malformed, forged or expired tokens make
 * the broker answer with a STOMP {@code ERROR} frame and the session never becomes CONNECTED.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public StompAuthChannelInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }
        accessor.setUser(authenticate(bearerToken(accessor)));
        return message;
    }

    private StompPrincipal authenticate(String token) {
        try {
            Claims claims = jwtTokenService.parseAndValidate(token);
            Object uid = claims.get("uid");
            String email = claims.getSubject();
            if (!(uid instanceof Number number) || email == null) {
                throw new BadCredentialsException("Token is missing identity claims");
            }
            return new StompPrincipal(number.longValue(), email);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid or expired access token");
        }
    }

    private static String bearerToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        if (header == null || !header.startsWith(BEARER_PREFIX) || header.length() <= BEARER_PREFIX.length()) {
            throw new BadCredentialsException("STOMP CONNECT requires 'Authorization: Bearer <token>'");
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }
}
