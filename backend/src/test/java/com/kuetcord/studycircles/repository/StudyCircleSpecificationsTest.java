package com.kuetcord.studycircles.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudyCircleSpecificationsTest {

    @Test
    @DisplayName("LIKE wildcards in a circle search are matched literally")
    void shouldEscapeLikeWildcards() {
        assertEquals("100\\%\\_sure\\\\", StudyCircleSpecifications.escapeLike("100%_sure\\"));
    }
}
