package com.kuetcord.studycircles.repository;

import com.kuetcord.studycircles.dto.RevisionItemDto;
import com.kuetcord.studycircles.model.RevisionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RevisionItemRepository extends JpaRepository<RevisionItem, Long> {

    Optional<RevisionItem> findByIdAndStudyCircleId(Long id, Long studyCircleId);

    /** Oldest first with both names joined in, in one query; served by {@code idx_revision_items_circle_created}. */
    @Query("""
            SELECT new com.kuetcord.studycircles.dto.RevisionItemDto(
                r.id, r.title, r.done, c.id, c.displayName, d.id, d.displayName, r.completedAt, r.createdAt)
            FROM RevisionItem r
            LEFT JOIN User c ON c.id = r.createdBy
            LEFT JOIN User d ON d.id = r.completedBy
            WHERE r.studyCircleId = :circleId
            ORDER BY r.createdAt ASC, r.id ASC
            """)
    List<RevisionItemDto> findItemDtos(@Param("circleId") Long circleId);

    @Query("""
            SELECT new com.kuetcord.studycircles.dto.RevisionItemDto(
                r.id, r.title, r.done, c.id, c.displayName, d.id, d.displayName, r.completedAt, r.createdAt)
            FROM RevisionItem r
            LEFT JOIN User c ON c.id = r.createdBy
            LEFT JOIN User d ON d.id = r.completedBy
            WHERE r.id = :id
            """)
    Optional<RevisionItemDto> findItemDto(@Param("id") Long id);
}
