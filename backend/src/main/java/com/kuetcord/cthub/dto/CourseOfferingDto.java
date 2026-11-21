package com.kuetcord.cthub.dto;

import com.kuetcord.cthub.model.CourseOffering;

public record CourseOfferingDto(
        Long id,
        String courseCode,
        String courseTitle,
        String department,
        String semester,
        String academicYear,
        boolean followed
) {
    public static CourseOfferingDto from(CourseOffering course, boolean followed) {
        return new CourseOfferingDto(
                course.getId(),
                course.getCourseCode(),
                course.getCourseTitle(),
                course.getDepartment(),
                course.getSemester(),
                course.getAcademicYear(),
                followed
        );
    }
}
