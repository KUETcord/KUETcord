package com.kuetcord.playgrounds.repository;

import com.kuetcord.playgrounds.dto.LinkedOpportunityDto;
import com.kuetcord.playgrounds.dto.LinkedPlaygroundDto;
import com.kuetcord.playgrounds.model.OpportunityPlaygroundLink;
import com.kuetcord.playgrounds.model.PlaygroundVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OpportunityPlaygroundLinkRepository
        extends JpaRepository<OpportunityPlaygroundLink, OpportunityPlaygroundLink.Key> {

    /** @return 1 if the link was created, 0 if the playground was already linked to the opportunity */
    @Modifying
    @Query(value = """
            INSERT INTO opportunity_playgrounds (opportunity_id, playground_id, linked_by)
            VALUES (:opportunityId, :playgroundId, :linkedBy)
            ON CONFLICT (opportunity_id, playground_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("opportunityId") Long opportunityId,
                       @Param("playgroundId") Long playgroundId,
                       @Param("linkedBy") Long linkedBy);

    /**
     * Teams formed for one opportunity that the viewer may see: every PUBLIC playground plus PRIVATE ones
     * the viewer belongs to. One query, newest link first.
     */
    @Query("""
            SELECT new com.kuetcord.playgrounds.dto.LinkedPlaygroundDto(
                p.id, p.title, p.status, p.visibility,
                (SELECT COUNT(m2) FROM PlaygroundMember m2 WHERE m2.playgroundId = p.id),
                l.linkedAt)
            FROM OpportunityPlaygroundLink l
            JOIN Playground p ON p.id = l.id.playgroundId
            WHERE l.id.opportunityId = :opportunityId
              AND (p.visibility = :publicVisibility
                   OR EXISTS (SELECT 1 FROM PlaygroundMember m
                              WHERE m.playgroundId = p.id AND m.userId = :viewerId))
            ORDER BY l.linkedAt DESC, p.id DESC
            """)
    List<LinkedPlaygroundDto> findVisiblePlaygrounds(@Param("opportunityId") Long opportunityId,
                                                     @Param("viewerId") Long viewerId,
                                                     @Param("publicVisibility") PlaygroundVisibility publicVisibility);

    /** Contests a playground was formed for, nearest registration deadline first. */
    @Query("""
            SELECT new com.kuetcord.playgrounds.dto.LinkedOpportunityDto(
                o.id, o.title, o.category, o.registrationDeadline)
            FROM OpportunityPlaygroundLink l
            JOIN Opportunity o ON o.id = l.id.opportunityId
            WHERE l.id.playgroundId = :playgroundId
            ORDER BY o.registrationDeadline ASC, o.id ASC
            """)
    List<LinkedOpportunityDto> findLinkedOpportunities(@Param("playgroundId") Long playgroundId);
}
