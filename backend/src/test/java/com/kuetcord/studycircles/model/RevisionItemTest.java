package com.kuetcord.studycircles.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevisionItemTest {

    private static final Instant T1 = Instant.parse("2026-11-20T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-20T11:00:00Z");

    @Test
    @DisplayName("Ticking records who and when; ticking again is a no-op that keeps the first tick")
    void shouldBeIdempotentWhenTickedTwice() {
        RevisionItem item = RevisionItem.builder().studyCircleId(1L).title("Dijkstra").createdBy(7L).build();

        assertTrue(item.markDone(true, 7L, T1));
        assertFalse(item.markDone(true, 8L, T2));

        assertTrue(item.isDone());
        assertEquals(7L, item.getCompletedBy());
        assertEquals(T1, item.getCompletedAt());
    }

    @Test
    @DisplayName("Unticking clears the completion so the row satisfies chk_revision_item_completion")
    void shouldClearCompletionWhenUnticked() {
        RevisionItem item = RevisionItem.builder().studyCircleId(1L).title("Bellman-Ford").build();
        item.markDone(true, 7L, T1);

        assertTrue(item.markDone(false, 8L, T2));

        assertFalse(item.isDone());
        assertNull(item.getCompletedBy());
        assertNull(item.getCompletedAt());
        assertFalse(item.markDone(false, 8L, T2));
    }
}
