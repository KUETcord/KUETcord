package com.kuetcord.opportunities.repository;

import com.kuetcord.opportunities.model.OpportunityBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface OpportunityBookmarkRepository extends JpaRepository<OpportunityBookmark, Long> {

    /**
     * Race-safe idempotent bookmark: concurrent or repeated calls converge on a single row
     * instead of surfacing a unique-constraint violation to the client.
     *
     * @return 1 when a row was inserted, 0 when the bookmark already existed
     */
    @Modifying
    @Query(value = """
            INSERT INTO opportunity_bookmarks (user_id, opportunity_id)
            VALUES (:userId, :opportunityId)
            ON CONFLICT ON CONSTRAINT uq_user_opportunity_bookmark DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("opportunityId") Long opportunityId);

    @Modifying
    @Query("DELETE FROM OpportunityBookmark b WHERE b.userId = :userId AND b.opportunityId = :opportunityId")
    int deleteByUserIdAndOpportunityId(@Param("userId") Long userId, @Param("opportunityId") Long opportunityId);

    boolean existsByUserIdAndOpportunityId(Long userId, Long opportunityId);

    long countByOpportunityId(Long opportunityId);

    /** One query per browse request instead of one per card (no N+1 on the bookmark flag). */
    @Query("SELECT b.opportunityId FROM OpportunityBookmark b WHERE b.userId = :userId")
    Set<Long> findOpportunityIdsByUserId(@Param("userId") Long userId);
}
