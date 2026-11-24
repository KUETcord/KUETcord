package com.kuetcord.studycircles.repository;

import com.kuetcord.studycircles.dto.CircleMemberDto;
import com.kuetcord.studycircles.model.StudyCircleMember;
import com.kuetcord.studycircles.model.StudyCircleRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudyCircleMemberRepository extends JpaRepository<StudyCircleMember, Long> {

    /**
     * Joins unless already a member; {@code uq_circle_user} makes a double-click or a retried request
     * converge on one row instead of failing with a unique violation.
     *
     * @return 1 if the member was added, 0 if they already belonged to the circle
     */
    @Modifying
    @Query(value = """
            INSERT INTO study_circle_members (study_circle_id, user_id, role)
            VALUES (:circleId, :userId, :role)
            ON CONFLICT ON CONSTRAINT uq_circle_user DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("circleId") Long circleId, @Param("userId") Long userId, @Param("role") String role);

    @Query("SELECT m.role FROM StudyCircleMember m WHERE m.studyCircleId = :circleId AND m.userId = :userId")
    Optional<StudyCircleRole> findRole(@Param("circleId") Long circleId, @Param("userId") Long userId);

    long countByStudyCircleId(Long studyCircleId);

    @Query("SELECT m.userId FROM StudyCircleMember m WHERE m.studyCircleId = :circleId ORDER BY m.userId")
    List<Long> findUserIds(@Param("circleId") Long circleId);

    @Query("""
            SELECT new com.kuetcord.studycircles.dto.CircleMemberDto(m.userId, u.displayName, m.role, m.joinedAt)
            FROM StudyCircleMember m
            JOIN User u ON u.id = m.userId
            WHERE m.studyCircleId = :circleId
            ORDER BY m.joinedAt ASC, m.id ASC
            """)
    List<CircleMemberDto> findMemberDtos(@Param("circleId") Long circleId);

    /** Rows of {@code [circleId, count]} for a page of circles (one query, not one per card). */
    @Query("""
            SELECT m.studyCircleId, COUNT(m)
            FROM StudyCircleMember m
            WHERE m.studyCircleId IN :circleIds
            GROUP BY m.studyCircleId
            """)
    List<Object[]> countByCircleIds(@Param("circleIds") Collection<Long> circleIds);

    /** Rows of {@code [circleId, role]}: the viewer's role in each circle of a page. */
    @Query("""
            SELECT m.studyCircleId, m.role
            FROM StudyCircleMember m
            WHERE m.userId = :userId AND m.studyCircleId IN :circleIds
            """)
    List<Object[]> findRolesForUser(@Param("userId") Long userId, @Param("circleIds") Collection<Long> circleIds);
}
