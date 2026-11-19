package com.kuetcord.opportunities.dto;

import com.kuetcord.opportunities.model.OpportunityCategory;
import com.kuetcord.opportunities.model.OpportunityMode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the opportunity submission DTO: field limits mirror the V3/V6 schema and
 * cross-field rules reject impossible events before they reach the database CHECK constraints.
 */
class CreateOpportunityRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private final Instant deadline = Instant.now().plus(Duration.ofDays(8));
    private final Instant start = deadline.plus(Duration.ofDays(7));
    private final Instant end = start.plus(Duration.ofDays(1));

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    @DisplayName("The documented hackathon payload is valid and defaults are applied")
    void shouldAcceptPlanExamplePayload() {
        CreateOpportunityRequest request = request("https://hackathon.kuet.ac.bd", deadline, start, end, 3, 5);
        assertTrue(validator.validate(request).isEmpty());

        CreateOpportunityRequest minimal = new CreateOpportunityRequest("ICPC Dhaka Regional",
                OpportunityCategory.PROGRAMMING_CONTEST, "ICPC", "https://icpc.global", deadline, start, end,
                null, null, null, null, null);
        assertTrue(validator.validate(minimal).isEmpty());
        assertEquals(OpportunityMode.ONLINE, minimal.resolvedMode());
        assertEquals(1, minimal.resolvedTeamSizeMin());
        assertEquals(4, minimal.resolvedTeamSizeMax());
    }

    @Test
    @DisplayName("Non-http source links and past deadlines are rejected")
    void shouldRejectUnsafeLinksAndPastDeadlines() {
        assertEquals(Set.of("sourceUrl"),
                violatedFields(request("javascript:alert(1)", deadline, start, end, 3, 5)));
        assertEquals(Set.of("registrationDeadline"),
                violatedFields(request("https://x.org", Instant.now().minusSeconds(60), start, end, 3, 5)));
    }

    @Test
    @DisplayName("Cross-field rules: event window, deadline before event end, team size range")
    void shouldRejectInconsistentEventData() {
        assertEquals(Set.of("eventWindowValid"),
                violatedFields(request("https://x.org", deadline, end, start, 3, 5)));
        assertEquals(Set.of("deadlineWithinEvent"),
                violatedFields(request("https://x.org", end.plus(Duration.ofDays(1)), start, end, 3, 5)));
        assertEquals(Set.of("teamSizeRangeValid"),
                violatedFields(request("https://x.org", deadline, start, end, 5, 3)));
        assertTrue(violatedFields(request("https://x.org", deadline, start, end, 6, null)).isEmpty(),
                "An omitted max defaults up to the given min instead of failing");
    }

    @Test
    @DisplayName("Titles longer than the 150-char column are rejected")
    void shouldRejectOverlongTitle() {
        CreateOpportunityRequest request = new CreateOpportunityRequest("x".repeat(151), OpportunityCategory.HACKATHON,
                "Org", "https://x.org", deadline, start, end, OpportunityMode.HYBRID, null, 1, 4, null);
        assertEquals(Set.of("title"), violatedFields(request));
    }

    private CreateOpportunityRequest request(String url, Instant deadline, Instant start, Instant end,
                                             Integer min, Integer max) {
        return new CreateOpportunityRequest("KUET Hackathon 2026: Tech for Impact", OpportunityCategory.HACKATHON,
                "KUET Computer Club", url, deadline, start, end, OpportunityMode.ON_SITE,
                "Undergraduate students, all departments", min, max,
                "48-hour build sprint addressing campus sustainability.");
    }

    private Set<String> violatedFields(CreateOpportunityRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
