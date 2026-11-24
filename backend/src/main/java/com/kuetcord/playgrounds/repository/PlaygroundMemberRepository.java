package com.kuetcord.playgrounds.repository;

import com.kuetcord.playgrounds.dto.PlaygroundMemberDto;
import com.kuetcord.playgrounds.model.PlaygroundMember;
import com.kuetcord.playgrounds.model.PlaygroundRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaygroundMemberRepository extends JpaRepository<PlaygroundMember, Long> {

    /**
     * Adds a member unless they already belong to the playground. Relies on {@code uq_playground_user}
     * so two leads inviting the same student at once cannot produce a duplicate or a 500.
     *
     * @return 1 if the member was added, 0 if they were already a member
     */
    @Modifying
    @Query(value = """
            INSERT INTO playground_members (playground_id, user_id, role)
            VALUES (:playgroundId, :userId, :role)
            ON CONFLICT ON CONSTRAINT uq_playground_user DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("playgroundId") Long playgroundId,
                       @Param("userId") Long userId,
                       @Param("role") String role);

    @Query("SELECT m.userId FROM PlaygroundMember m WHERE m.playgroundId = :playgroundId ORDER BY m.userId")
    List<Long> findUserIds(@Param("playgroundId") Long playgroundId);

    @Query("SELECT m.role FROM PlaygroundMember m WHERE m.playgroundId = :playgroundId AND m.userId = :userId")
    Optional<PlaygroundRole> findRole(@Param("playgroundId") Long playgroundId, @Param("userId") Long userId);

    @Query("""
            SELECT new com.kuetcord.playgrounds.dto.PlaygroundMemberDto(
                m.userId, u.displayName, u.username, m.role, m.joinedAt)
            FROM PlaygroundMember m
            JOIN User u ON u.id = m.userId
            WHERE m.playgroundId = :playgroundId
            ORDER BY m.joinedAt ASC, m.id ASC
            """)
    List<PlaygroundMemberDto> findMemberDtos(@Param("playgroundId") Long playgroundId);
}
