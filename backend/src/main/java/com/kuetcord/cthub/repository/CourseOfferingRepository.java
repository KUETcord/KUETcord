package com.kuetcord.cthub.repository;

import com.kuetcord.cthub.model.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long>, JpaSpecificationExecutor<CourseOffering> {

    boolean existsByCourseCodeIgnoreCaseAndSemesterIgnoreCaseAndAcademicYearIgnoreCase(
            String courseCode, String semester, String academicYear);

    boolean existsByCourseCodeIgnoreCaseAndSemesterIgnoreCaseAndAcademicYearIgnoreCaseAndIdNot(
            String courseCode, String semester, String academicYear, Long id);
}
