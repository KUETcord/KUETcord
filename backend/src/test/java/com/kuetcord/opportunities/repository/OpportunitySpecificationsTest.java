package com.kuetcord.opportunities.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpportunitySpecificationsTest {

    @Test
    @DisplayName("Search text is escaped so LIKE wildcards in user input match literally")
    void shouldEscapeLikeWildcards() {
        assertEquals("100\\%", OpportunitySpecifications.escapeLike("100%"));
        assertEquals("snake\\_case", OpportunitySpecifications.escapeLike("snake_case"));
        assertEquals("a\\\\b", OpportunitySpecifications.escapeLike("a\\b"));
        assertEquals("hackathon", OpportunitySpecifications.escapeLike("hackathon"));
    }
}
