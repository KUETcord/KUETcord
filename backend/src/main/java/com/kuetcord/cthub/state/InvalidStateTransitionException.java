package com.kuetcord.cthub.state;

import com.kuetcord.common.exception.BadRequestException;

/**
 * Thrown when an illegal CT lifecycle state transition is attempted (e.g., verifying a CANCELLED CT).
 */
public class InvalidStateTransitionException extends BadRequestException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
