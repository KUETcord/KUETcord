package com.kuetcord.studycircles.dto;

import com.kuetcord.cthub.model.CourseOffering;

/** The catalog course a circle studies for (Member A's {@code course_offerings}). */
public record CourseRefDto(Long id, String courseCode, String courseTitle, String semester, String academicYear) {

    public static CourseRefDto of(CourseOffering course) {
        return course == null ? null : new CourseRefDto(course.getId(), course.getCourseCode(),
                course.getCourseTitle(), course.getSemester(), course.getAcademicYear());
    }
}
