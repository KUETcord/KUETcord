package com.kuetcord.playgrounds.service;

import com.kuetcord.opportunities.model.Opportunity;
import com.kuetcord.opportunities.model.OpportunityCategory;
import com.kuetcord.playgrounds.dto.CreatePlaygroundRequest;
import com.kuetcord.playgrounds.dto.LinkPlaygroundRequest;
import com.kuetcord.playgrounds.model.PlaygroundVisibility;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpportunityPlaygroundDefaultsTest {

    @Test
    @DisplayName("One-click team: '<title> Team', contest description, category tag, PUBLIC")
    void shouldDeriveDefaultPlayground() {
        Opportunity opportunity = Opportunity.builder()
                .title("ICPC Dhaka Regional").category(OpportunityCategory.PROGRAMMING_CONTEST).build();
        CreatePlaygroundRequest defaults = OpportunityPlaygroundLinkService.defaultPlaygroundFor(opportunity);

        assertEquals("ICPC Dhaka Regional Team", defaults.title());
        assertNull(defaults.problemStatement());
        assertEquals(List.of("PROGRAMMING_CONTEST"), defaults.tags());
        assertEquals(PlaygroundVisibility.PUBLIC, defaults.visibility());
    }

    @Test
    @DisplayName("A 150-char contest title is shortened so '<title> Team' still fits the 150-char column")
    void shouldKeepDefaultTitleWithinColumn() {
        Opportunity opportunity = Opportunity.builder()
                .title("x".repeat(150)).category(OpportunityCategory.RESEARCH).description("d".repeat(6000)).build();
        CreatePlaygroundRequest defaults = OpportunityPlaygroundLinkService.defaultPlaygroundFor(opportunity);

        assertEquals(150, defaults.title().length());
        assertTrue(defaults.title().endsWith(" Team"));
        assertEquals(5000, defaults.problemStatement().length());
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(defaults).isEmpty());
        }
    }

    @Test
    @DisplayName("Link request: playgroundId and newPlayground together are rejected; either alone or neither is fine")
    void shouldRejectAmbiguousLinkRequest() {
        CreatePlaygroundRequest details = new CreatePlaygroundRequest("Team", null, null, null, null, null);
        assertFalse(new LinkPlaygroundRequest(5L, details).isSingleTarget());
        assertTrue(new LinkPlaygroundRequest(5L, null).isSingleTarget());
        assertTrue(new LinkPlaygroundRequest(null, details).isSingleTarget());
        assertTrue(new LinkPlaygroundRequest(null, null).isSingleTarget());
    }
}
