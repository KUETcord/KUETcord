package com.kuetcord.playgrounds.service;

import com.kuetcord.playgrounds.model.Playground;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaygroundTagsTest {

    @Test
    @DisplayName("Tags are trimmed and de-duplicated case-insensitively, keeping the first spelling and order")
    void shouldNormalizeTags() {
        assertEquals(List.of("React", "Spring Boot"),
                PlaygroundService.normalizeTags(Arrays.asList(" React ", "Spring Boot", "react", "")));
        assertEquals(List.of(), PlaygroundService.normalizeTags(null));
    }

    @Test
    @DisplayName("The comma-separated column round-trips, including rows written by the seed script")
    void shouldRoundTripTagColumn() {
        Playground seeded = Playground.builder().tags("Spring Boot, React, PostgreSQL, WebSocket").build();
        assertEquals(List.of("Spring Boot", "React", "PostgreSQL", "WebSocket"), seeded.tagList());

        Playground playground = Playground.builder().build();
        playground.replaceTags(List.of("ML", "Computer Vision"));
        assertEquals("ML, Computer Vision", playground.getTags());
        assertEquals(List.of("ML", "Computer Vision"), playground.tagList());

        playground.replaceTags(List.of());
        assertNull(playground.getTags());
        assertEquals(List.of(), playground.tagList());
    }
}
