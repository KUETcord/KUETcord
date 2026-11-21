package com.kuetcord.playgrounds.access;

import com.kuetcord.playgrounds.model.Playground;
import com.kuetcord.playgrounds.model.PlaygroundRole;

/**
 * Result of a successful access check: the loaded playground and the caller's role in it
 * ({@code null} for a non-member reading a PUBLIC playground).
 */
public record PlaygroundAccess(Playground playground, PlaygroundRole role) {

    public boolean isMember() {
        return role != null;
    }
}
