package com.kuetcord.cthub.dto;

import com.kuetcord.cthub.model.CourseOffering;

public record CourseOfferingDto(
        Long id,
        String courseCode,
        String courseTitle,
        String department,
        String semester,
        String yearTerm,
        String targetBatch,
        String academicYear,
        boolean followed
) {
    /** Backward-compatible constructor for existing test suites. */
    public CourseOfferingDto(
            Long id,
            String courseCode,
            String courseTitle,
            String department,
            String semester,
            String academicYear,
            boolean followed) {
        this(id, courseCode, courseTitle, department, semester, semester, null, academicYear, followed);
    }

    public static CourseOfferingDto from(CourseOffering course, boolean followed) {
        return new CourseOfferingDto(
                course.getId(),
                course.getCourseCode(),
                course.getCourseTitle(),
                course.getDepartment(),
                course.getSemester(),
                course.getYearTerm(),
                course.getTargetBatch(),
                course.getAcademicYear(),
                followed
        );
    }
}
