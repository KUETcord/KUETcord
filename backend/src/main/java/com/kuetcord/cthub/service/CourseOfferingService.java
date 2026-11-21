package com.kuetcord.cthub.service;

import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.exception.ConflictException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.cthub.dto.CourseFollowStatusDto;
import com.kuetcord.cthub.dto.CourseOfferingDto;
import com.kuetcord.cthub.dto.CreateCourseOfferingRequest;
import com.kuetcord.cthub.model.CourseFollow;
import com.kuetcord.cthub.model.CourseOffering;
import com.kuetcord.cthub.repository.CourseFollowRepository;
import com.kuetcord.cthub.repository.CourseOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Manages course catalog discovery, creation, and student course follow subscriptions.
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
        List<CourseOffering> courses = courseCatalogCacheService.findCourses(department, semester, academicYear, query);
        Set<Long> followedIds = courseFollowRepository.findFollowedCourseIdsByUserId(userId);
        return courses.stream()
                .map(course -> CourseOfferingDto.from(course, followedIds.contains(course.getId())))
                .toList();
    }

    @Transactional
    public CourseOfferingDto createCourse(CreateCourseOfferingRequest request) {
        String code = request.courseCode().trim().toUpperCase();
        String semester = request.semester().trim();
        String year = request.academicYear().trim();

        if (courseOfferingRepository.existsByCourseCodeIgnoreCaseAndSemesterIgnoreCaseAndAcademicYearIgnoreCase(code, semester, year)) {
            throw new ConflictException("Course offering already exists for " + code + " (" + semester + " " + year + ")");
        }

        CourseOffering created = courseOfferingRepository.save(CourseOffering.builder()
                .courseCode(code)
                .courseTitle(request.courseTitle().trim())
                .department(request.department().trim().toUpperCase())
                .semester(semester)
                .academicYear(year)
                .build());

        courseCatalogCacheService.evictCatalogCache();
        return CourseOfferingDto.from(created, false);
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
}
