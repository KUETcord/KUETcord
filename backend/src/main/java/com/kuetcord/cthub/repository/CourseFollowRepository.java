package com.kuetcord.cthub.repository;

import com.kuetcord.cthub.model.CourseFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface CourseFollowRepository extends JpaRepository<CourseFollow, Long> {

    boolean existsByUserIdAndCourseOfferingId(Long userId, Long courseOfferingId);

    void deleteByUserIdAndCourseOfferingId(Long userId, Long courseOfferingId);

    @Query("SELECT cf.courseOffering.id FROM CourseFollow cf WHERE cf.user.id = :userId")
    Set<Long> findFollowedCourseIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT cf.user.id FROM CourseFollow cf WHERE cf.courseOffering.id = :courseOfferingId")
    List<Long> findFollowerUserIdsByCourseOfferingId(@Param("courseOfferingId") Long courseOfferingId);

    long countByCourseOfferingId(Long courseOfferingId);
}
