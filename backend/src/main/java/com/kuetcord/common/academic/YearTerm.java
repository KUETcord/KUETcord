package com.kuetcord.common.academic;

import com.kuetcord.common.exception.BadRequestException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Canonical KUET undergraduate Year–Term representation ({@code 1-1} through {@code 4-2}),
 * shared across Identity, Course Catalog, CT Hub, Coursework Hubs, Project Hub, and Campus Pulse.
 */
public final class YearTerm {

    public static final List<String> ALL_CODES = List.of(
            "1-1", "1-2", "2-1", "2-2", "3-1", "3-2", "4-1", "4-2"
    );

    private static final Set<String> VALID_CODES = Set.copyOf(ALL_CODES);

    private static final Map<String, String> SEMESTER_TO_YEAR_TERM = Map.ofEntries(
            Map.entry("1st", "1-1"),
            Map.entry("2nd", "1-2"),
            Map.entry("3rd", "2-1"),
            Map.entry("4th", "2-2"),
            Map.entry("5th", "3-1"),
            Map.entry("6th", "3-2"),
            Map.entry("7th", "4-1"),
            Map.entry("8th", "4-2")
    );

    private static final Map<String, String> YEAR_TERM_TO_SEMESTER = Map.of(
            "1-1", "1st",
            "1-2", "2nd",
            "2-1", "3rd",
            "2-2", "4th",
            "3-1", "5th",
            "3-2", "6th",
            "4-1", "7th",
            "4-2", "8th"
    );

    private static final Map<String, String> DISPLAY_LABELS = Map.of(
            "1-1", "1st Year, 1st Term",
            "1-2", "1st Year, 2nd Term",
            "2-1", "2nd Year, 1st Term",
            "2-2", "2nd Year, 2nd Term",
            "3-1", "3rd Year, 1st Term",
            "3-2", "3rd Year, 2nd Term",
            "4-1", "4th Year, 1st Term",
            "4-2", "4th Year, 2nd Term"
    );

    private YearTerm() {
    }

    public static boolean isValid(String value) {
        return value != null && VALID_CODES.contains(value.trim());
    }

    public static String normalizeNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (VALID_CODES.contains(trimmed)) {
            return trimmed;
        }
        String mapped = SEMESTER_TO_YEAR_TERM.get(trimmed);
        if (mapped != null) {
            return mapped;
        }
        throw new BadRequestException("Invalid yearTerm '" + raw + "'. Expected one of: " + String.join(", ", ALL_CODES));
    }

    public static String resolveForCourse(String yearTerm, String semester) {
        if (yearTerm != null && !yearTerm.isBlank()) {
            return normalizeNullable(yearTerm);
        }
        if (semester != null && !semester.isBlank()) {
            String normalized = semester.trim().toLowerCase(Locale.ROOT);
            if (VALID_CODES.contains(normalized)) {
                return normalized;
            }
            String mapped = SEMESTER_TO_YEAR_TERM.get(normalized);
            if (mapped != null) {
                return mapped;
            }
        }
        return "3-1";
    }

    public static String toLegacySemester(String yearTerm) {
        return YEAR_TERM_TO_SEMESTER.getOrDefault(yearTerm, yearTerm);
    }

    public static String labelOf(String yearTerm) {
        return DISPLAY_LABELS.getOrDefault(yearTerm, yearTerm);
    }
}
