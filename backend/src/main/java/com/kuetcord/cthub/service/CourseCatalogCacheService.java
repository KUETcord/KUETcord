package com.kuetcord.cthub.service;

import com.kuetcord.cthub.model.CourseOffering;
import com.kuetcord.cthub.repository.CourseOfferingRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated L1 Caffeine cache boundary for static course catalog reads, ensuring
 * Spring AOP intercepts cache lookups and evictions without self-invocation bypass.
 */
@Service
@RequiredArgsConstructor
public class CourseCatalogCacheService {

    private final CourseOfferingRepository courseOfferingRepository;

    @Transactional(readOnly = true)
    @Cacheable(
            value = "course_offerings",
            key = "(#department ?: '*') + '|' + (#semester ?: '*') + '|' + (#academicYear ?: '*') + '|' + (#query ?: '*')"
    )
    public List<CourseOffering> findCourses(String department, String semester, String academicYear, String query) {
        Specification<CourseOffering> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (department != null && !department.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("department")), department.trim().toLowerCase(Locale.ROOT)));
            }
            if (semester != null && !semester.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("semester")), semester.trim().toLowerCase(Locale.ROOT)));
            }
            if (academicYear != null && !academicYear.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("academicYear")), academicYear.trim().toLowerCase(Locale.ROOT)));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("courseCode")), pattern),
                        cb.like(cb.lower(root.get("courseTitle")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return courseOfferingRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "courseCode"));
    }

    @CacheEvict(value = "course_offerings", allEntries = true)
    public void evictCatalogCache() {
        // Eviction handled by Spring Cache interceptor
    }
}
