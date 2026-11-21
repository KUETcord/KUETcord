package com.kuetcord.playgrounds.repository;

import com.kuetcord.playgrounds.dto.PlaygroundSummaryDto;
import com.kuetcord.playgrounds.model.Playground;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaygroundRepository extends JpaRepository<Playground, Long> {

    /** "My playgrounds": every workspace the user belongs to, most recently touched first. */
    @Query("""
            SELECT new com.kuetcord.playgrounds.dto.PlaygroundSummaryDto(
                p.id, p.title, p.status, p.visibility, m.role,
                (SELECT COUNT(m2) FROM PlaygroundMember m2 WHERE m2.playgroundId = p.id),
                p.updatedAt)
            FROM Playground p
            JOIN PlaygroundMember m ON m.playgroundId = p.id
            WHERE m.userId = :userId
            ORDER BY p.updatedAt DESC, p.id DESC
            """)
    List<PlaygroundSummaryDto> findSummariesForMember(@Param("userId") Long userId);
}
