package com.kuetcord.messaging.dto;

/**
 * Pushed to the sending session only, on {@code /user/queue/errors}, when a SEND is rejected.
 * {@code receiptId} echoes the frame's {@code receipt} header so the client can flip that exact
 * optimistic bubble to FAILED and offer Retry.
 */
public record StompErrorDto(
        String code,
        String message,
        String receiptId
) {
}
