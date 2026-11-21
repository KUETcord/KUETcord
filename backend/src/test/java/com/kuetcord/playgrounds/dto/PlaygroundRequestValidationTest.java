package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.PlaygroundVisibility;
import com.kuetcord.playgrounds.model.TaskStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Request contracts for playgrounds and Kanban tasks; limits mirror the V3/V9 columns. */
class PlaygroundRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

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
    @DisplayName("Create playground: tags are limited to 8 comma-free entries and links must be http(s)")
    void shouldValidateCreatePlayground() {
        assertTrue(violatedFields(create(List.of("Spring Boot", "React"), "https://github.com/kuetcord")).isEmpty());
        assertEquals(PlaygroundVisibility.PUBLIC, create(null, null).resolvedVisibility());

        assertEquals(Set.of("tags"), violatedFields(create(Collections.nCopies(9, "x"), null)));
        assertEquals(Set.of("tags[0].<list element>"), violatedFields(create(List.of("a,b"), null)));
        assertEquals(Set.of("tags[0].<list element>"), violatedFields(create(List.of("   "), null)));
        assertEquals(Set.of("repoUrl"), violatedFields(create(null, "ftp://example.org/repo")));
    }

    @Test
    @DisplayName("Update playground: expectedVersion is mandatory, an empty link clears it, a blank title is rejected")
    void shouldValidateUpdatePlayground() {
        assertTrue(violatedFields(new UpdatePlaygroundRequest(null, null, null, null, null, "", "", 0L)).isEmpty());
        assertEquals(Set.of("expectedVersion"),
                violatedFields(new UpdatePlaygroundRequest("New", null, null, null, null, null, null, null)));
        assertEquals(Set.of("titleValid"),
                violatedFields(new UpdatePlaygroundRequest("  ", null, null, null, null, null, null, 0L)));
    }

    @Test
    @DisplayName("Update task: expectedVersion is mandatory and assignee/due-date clears cannot be combined with a value")
    void shouldValidateUpdateTask() {
        assertTrue(violatedFields(new UpdateTaskRequest(null, null, TaskStatus.IN_PROGRESS, null, 102L,
                null, null, null, 3L)).isEmpty());
        assertEquals(Set.of("expectedVersion"), violatedFields(new UpdateTaskRequest(null, null, TaskStatus.DONE,
                null, null, null, null, null, null)));
        assertEquals(Set.of("assigneeChangeUnambiguous"), violatedFields(new UpdateTaskRequest(null, null, null,
                null, 102L, true, null, null, 3L)));
        assertEquals(Set.of("dueDateChangeUnambiguous"), violatedFields(new UpdateTaskRequest(null, null, null,
                null, null, null, Instant.now(), true, 3L)));
    }

    @Test
    @DisplayName("Create task: a title is required and defaults are BACKLOG / MEDIUM")
    void shouldValidateCreateTask() {
        CreateTaskRequest minimal = new CreateTaskRequest("Write ADR", null, null, null, null, null);
        assertTrue(violatedFields(minimal).isEmpty());
        assertEquals(TaskStatus.BACKLOG, minimal.resolvedStatus());
        assertEquals("MEDIUM", minimal.resolvedPriority().name());
        assertEquals(Set.of("title"), violatedFields(new CreateTaskRequest(" ", null, null, null, null, null)));
        assertEquals(Set.of("title"), violatedFields(new CreateTaskRequest("x".repeat(151), null, null, null, null, null)));
    }

    private static CreatePlaygroundRequest create(List<String> tags, String repoUrl) {
        return new CreatePlaygroundRequest("Team KUETcord", "Campus workflow system", tags, null, repoUrl, null);
    }

    private static Set<String> violatedFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
