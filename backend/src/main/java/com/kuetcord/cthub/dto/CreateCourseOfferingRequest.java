package com.kuetcord.cthub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseOfferingRequest(
        @NotBlank(message = "Course code is required")
        @Size(max = 20, message = "Course code must not exceed 20 characters")
        String courseCode,

        @NotBlank(message = "Course title is required")
        @Size(max = 150, message = "Course title must not exceed 150 characters")
        String courseTitle,

        @NotBlank(message = "Department is required")
        @Size(max = 50, message = "Department must not exceed 50 characters")
        String department,

        @Size(max = 20, message = "Semester must not exceed 20 characters")
        String semester,

        @NotBlank(message = "Academic year is required")
        @Size(max = 10, message = "Academic year must not exceed 10 characters")
        String academicYear,

        @Size(max = 10, message = "Year-Term must not exceed 10 characters")
        String yearTerm,

        @Size(max = 20, message = "Target batch must not exceed 20 characters")
        String targetBatch
) {
    /** Backward-compatible constructor for existing test suites. */
    public CreateCourseOfferingRequest(
            String courseCode,
            String courseTitle,
            String department,
            String semester,
            String academicYear) {
        this(courseCode, courseTitle, department, semester, academicYear, null, null);
    }
}
