package com.kuetcord.studycircles.repository;

import com.kuetcord.studycircles.dto.StudyResourceDto;
import com.kuetcord.studycircles.model.StudyResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudyResourceRepository extends JpaRepository<StudyResource, Long> {

    long countByStudyCircleId(Long studyCircleId);

    /** Newest first, contributor name joined in; served by {@code idx_resources_circle_created}. */
    @Query("""
            SELECT new com.kuetcord.studycircles.dto.StudyResourceDto(
                r.id, r.title, r.resourceUrl, r.category, u.id, u.displayName, r.createdAt)
            FROM StudyResource r
            LEFT JOIN User u ON u.id = r.contributedBy
            WHERE r.studyCircleId = :circleId
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<StudyResourceDto> findResourceDtos(@Param("circleId") Long circleId);

    @Query("""
            SELECT new com.kuetcord.studycircles.dto.StudyResourceDto(
                r.id, r.title, r.resourceUrl, r.category, u.id, u.displayName, r.createdAt)
            FROM StudyResource r
            LEFT JOIN User u ON u.id = r.contributedBy
            WHERE r.id = :id
            """)
    Optional<StudyResourceDto> findResourceDto(@Param("id") Long id);
}
