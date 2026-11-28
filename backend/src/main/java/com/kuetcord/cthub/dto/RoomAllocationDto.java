package com.kuetcord.cthub.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps a variable student roll range (e.g. {@code 1–40}) to an exam room (e.g. {@code CSE-101}).
 */
public record RoomAllocationDto(
        @Min(value = 1, message = "Starting roll must be at least 1")
        Integer rollStart,

        @Min(value = 1, message = "Ending roll must be at least 1")
        Integer rollEnd,

        @NotBlank(message = "Room name is required")
        @Size(max = 100, message = "Room name must not exceed 100 characters")
        String room
) {
    public String formatEntry() {
        String r = room == null ? "" : room.trim();
        if (rollStart != null && rollEnd != null) {
            return r + " (" + rollStart + "-" + rollEnd + ")";
        }
        if (rollStart != null) {
            return r + " (" + rollStart + "+)";
        }
        return r;
    }

    public static String summarize(List<RoomAllocationDto> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return null;
        }
        String joined = allocations.stream()
                .map(RoomAllocationDto::formatEntry)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
        if (joined.isEmpty()) {
            return null;
        }
        return joined.length() > 255 ? joined.substring(0, 252) + "..." : joined;
    }
}
