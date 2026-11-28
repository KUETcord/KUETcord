package com.kuetcord.termhub.dto;

import com.kuetcord.termhub.model.TermFinalEntry;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

public record TermFinalEntryDto(
        Long id,
        LocalDate examDate,
        String dayOfWeek,
        Long courseOfferingId,
        String courseCode,
        String courseTitle,
        String courseDepartment,
        String examRoom
) {
    public static TermFinalEntryDto fromEntity(TermFinalEntry row) {
        String computedDay = row.getExamDate()
                .getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String dept = row.getCourseOffering() != null ? row.getCourseOffering().getDepartment() : null;
        return new TermFinalEntryDto(
                row.getId(),
                row.getExamDate(),
                computedDay,
                row.getCourseOffering() != null ? row.getCourseOffering().getId() : null,
                row.getCourseCode(),
                row.getCourseTitle(),
                dept,
                row.getExamRoom()
        );
    }
}
