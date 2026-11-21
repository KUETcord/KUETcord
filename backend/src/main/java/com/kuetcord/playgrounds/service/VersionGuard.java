package com.kuetcord.playgrounds.service;

import jakarta.persistence.OptimisticLockException;

/**
 * Compares the version the client last saw with the current one before any change is applied.
 * <p>
 * This catches the common case (a stale browser tab) early with a clear message. The race where two
 * requests pass this check at the same moment is still caught by {@code @Version}: the second
 * {@code UPDATE ... WHERE version = ?} matches no row. Both paths end in HTTP 409 through
 * {@code GlobalExceptionHandler}.
 */
final class VersionGuard {

    private VersionGuard() {
    }

    static void assertMatches(String resource, Long currentVersion, Long expectedVersion) {
        if (currentVersion == null || !currentVersion.equals(expectedVersion)) {
            throw new OptimisticLockException(resource + " was modified by a teammate (expected version "
                    + expectedVersion + ", current " + currentVersion + "). Reload and try again.");
        }
    }
}
