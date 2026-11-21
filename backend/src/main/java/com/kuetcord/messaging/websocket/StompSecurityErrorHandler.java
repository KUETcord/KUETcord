package com.kuetcord.messaging.websocket;

import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

/**
 * Turns interceptor rejections into readable STOMP {@code ERROR} frames.
 * <p>
 * Spring wraps interceptor exceptions in a generic "Failed to send message to
 * clientInboundChannel" error. Clients need the real reason to react correctly
 * (refresh token vs. stop retrying a forbidden subscription), so security causes are
 * unwrapped. Every other failure gets a generic message so internals never leak.
 */
@Component
public class StompSecurityErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(@Nullable Message<byte[]> clientMessage, Throwable ex) {
        Throwable cause = rootSecurityCause(ex);
        String message;
        if (cause instanceof AuthenticationException) {
            message = "UNAUTHORIZED: " + cause.getMessage();
        } else if (cause instanceof AccessDeniedException) {
            message = "FORBIDDEN: " + cause.getMessage();
        } else {
            message = "ERROR: Frame could not be processed";
        }

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(message);
        accessor.setLeaveMutable(true);
        if (clientMessage != null) {
            StompHeaderAccessor clientHeaders = StompHeaderAccessor.wrap(clientMessage);
            if (clientHeaders.getReceipt() != null) {
                accessor.setReceiptId(clientHeaders.getReceipt());
            }
        }
        return MessageBuilder.createMessage(message.getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders());
    }

    private static Throwable rootSecurityCause(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof AuthenticationException || current instanceof AccessDeniedException) {
                return current;
            }
            current = current.getCause();
        }
        return ex;
    }
}
