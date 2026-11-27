package com.kuetcord.cthub.service;

import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.academic.YearTerm;
import com.kuetcord.common.exception.ConflictException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.cthub.dto.CourseFollowStatusDto;
import com.kuetcord.cthub.dto.CourseOfferingDto;
import com.kuetcord.cthub.dto.CreateCourseOfferingRequest;
import com.kuetcord.cthub.dto.UpdateCourseOfferingRequest;
import com.kuetcord.cthub.model.CourseFollow;
import com.kuetcord.cthub.model.CourseOffering;
import com.kuetcord.cthub.repository.CourseFollowRepository;
import com.kuetcord.cthub.repository.CourseOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Manages course catalog discovery, creation, admin CRUD, and student course follow subscriptions.
 */
@Service
@RequiredArgsConstructor
public class CourseOfferingService {

    private final CourseCatalogCacheService courseCatalogCacheService;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseFollowRepository courseFollowRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CourseOfferingDto> listCourses(
            Long userId, String department, String semester, String academicYear, String query) {
        return listCourses(userId, department, semester, null, academicYear, query);
    }

    @Transactional(readOnly = true)
    public List<CourseOfferingDto> listCourses(
            Long userId, String department, String semester, String yearTerm, String academicYear, String query) {
        List<CourseOffering> courses = courseCatalogCacheService.findCourses(
                department, semester, yearTerm, academicYear, query);
        Set<Long> followedIds = userId != null
                ? courseFollowRepository.findFollowedCourseIdsByUserId(userId)
                : Set.of();
        return courses.stream()
                .map(course -> CourseOfferingDto.from(course, followedIds.contains(course.getId())))
                .toList();
    }

    @Transactional
    public CourseOfferingDto createCourse(CreateCourseOfferingRequest request) {
        String code = request.courseCode().trim().toUpperCase(Locale.ROOT);
        String resolvedYearTerm = YearTerm.resolveForCourse(request.yearTerm(), request.semester());
        String semester = (request.semester() != null && !request.semester().isBlank())
                ? request.semester().trim()
                : YearTerm.toLegacySemester(resolvedYearTerm);
        String year = request.academicYear().trim();
        String targetBatch = trimNullable(request.targetBatch());

        if (courseOfferingRepository.existsByCourseCodeIgnoreCaseAndSemesterIgnoreCaseAndAcademicYearIgnoreCase(code, semester, year)) {
            throw new ConflictException("Course offering already exists for " + code + " (" + semester + " " + year + ")");
        }

        CourseOffering created = courseOfferingRepository.save(CourseOffering.builder()
                .courseCode(code)
                .courseTitle(request.courseTitle().trim())
                .department(request.department().trim().toUpperCase(Locale.ROOT))
                .semester(semester)
                .yearTerm(resolvedYearTerm)
                .targetBatch(targetBatch)
                .academicYear(year)
                .build());

        courseCatalogCacheService.evictCatalogCache();
        return CourseOfferingDto.from(created, false);
    }

    @Transactional
    public CourseOfferingDto updateCourse(Long courseId, UpdateCourseOfferingRequest request) {
        CourseOffering existing = courseOfferingRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + courseId));

        String code = request.courseCode().trim().toUpperCase(Locale.ROOT);
        String resolvedYearTerm = YearTerm.resolveForCourse(request.yearTerm(), request.semester());
        String semester = (request.semester() != null && !request.semester().isBlank())
                ? request.semester().trim()
                : YearTerm.toLegacySemester(resolvedYearTerm);
        String year = request.academicYear().trim();

        if (courseOfferingRepository.existsByCourseCodeIgnoreCaseAndSemesterIgnoreCaseAndAcademicYearIgnoreCaseAndIdNot(
                code, semester, year, courseId)) {
            throw new ConflictException("Another course offering already exists for " + code + " (" + semester + " " + year + ")");
        }

        existing.setCourseCode(code);
        existing.setCourseTitle(request.courseTitle().trim());
        existing.setDepartment(request.department().trim().toUpperCase(Locale.ROOT));
        existing.setYearTerm(resolvedYearTerm);
        existing.setSemester(semester);
        existing.setAcademicYear(year);
        existing.setTargetBatch(trimNullable(request.targetBatch()));

        CourseOffering saved = courseOfferingRepository.save(existing);
        courseCatalogCacheService.evictCatalogCache();
        return CourseOfferingDto.from(saved, false);
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        CourseOffering existing = courseOfferingRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + courseId));
        courseOfferingRepository.delete(existing);
        courseCatalogCacheService.evictCatalogCache();
    }

    @Transactional
    public CourseFollowStatusDto followCourse(Long userId, Long courseOfferingId) {
        CourseOffering course = courseOfferingRepository.findById(courseOfferingId)
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + courseOfferingId));

        if (!courseFollowRepository.existsByUserIdAndCourseOfferingId(userId, courseOfferingId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            courseFollowRepository.save(CourseFollow.builder()
                    .user(user)
                    .courseOffering(course)
                    .build());
        }

        long count = courseFollowRepository.countByCourseOfferingId(courseOfferingId);
        return new CourseFollowStatusDto(courseOfferingId, true, count);
    }

    @Transactional
    public CourseFollowStatusDto unfollowCourse(Long userId, Long courseOfferingId) {
        if (!courseOfferingRepository.existsById(courseOfferingId)) {
            throw new ResourceNotFoundException("Course offering not found: " + courseOfferingId);
        }

        if (courseFollowRepository.existsByUserIdAndCourseOfferingId(userId, courseOfferingId)) {
            courseFollowRepository.deleteByUserIdAndCourseOfferingId(userId, courseOfferingId);
        }

        long count = courseFollowRepository.countByCourseOfferingId(courseOfferingId);
        return new CourseFollowStatusDto(courseOfferingId, false, count);
    }

    private static String trimNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
