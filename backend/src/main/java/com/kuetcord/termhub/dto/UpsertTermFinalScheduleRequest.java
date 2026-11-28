package com.kuetcord.termhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record UpsertTermFinalScheduleRequest(
        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 180)
        String title,

        @NotBlank(message = "Target department is required")
        @Size(max = 16)
        String targetDepartment,

        @NotBlank(message = "Year-Term is required")
        @Size(max = 10)
        String yearTerm,

        @NotBlank(message = "Batch is required")
        @Size(max = 20)
        String batch,

        @NotBlank(message = "Session is required")
        @Size(max = 20)
        String session,

        @NotNull(message = "Exam start time is required")
        LocalTime examStartTime,

        @NotNull(message = "Exam end time is required")
        LocalTime examEndTime,

        @NotEmpty(message = "At least one exam date row is required")
        @Size(max = 30)
        List<@Valid ExamRowRequest> entries
) {
    public record ExamRowRequest(
            @NotNull(message = "Exam date is required")
            LocalDate examDate,
            Long courseOfferingId,
            @Size(max = 30)
            String courseCode,
            @Size(max = 180)
            String courseTitle,
            @Size(max = 120)
            String examRoom
    ) {}
}
