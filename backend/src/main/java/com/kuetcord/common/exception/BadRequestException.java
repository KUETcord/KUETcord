package com.kuetcord.common.exception;

/**
 * Thrown when client input violates business or domain validation rules (HTTP 400).
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

