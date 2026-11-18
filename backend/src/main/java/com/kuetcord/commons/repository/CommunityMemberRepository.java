package com.kuetcord.commons.repository;

import com.kuetcord.commons.model.CommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {

    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);

    List<CommunityMember> findByCommunityIdOrderByJoinedAtAsc(Long communityId);

    long countByCommunityId(Long communityId);

    /** Rows of [communityId, memberCount] for a batch of communities (single GROUP BY query). */
    @Query("SELECT m.communityId, COUNT(m) FROM CommunityMember m WHERE m.communityId IN :ids GROUP BY m.communityId")
    List<Object[]> countMembersByCommunityIds(@Param("ids") Collection<Long> communityIds);

    @Query("SELECT m.communityId FROM CommunityMember m WHERE m.userId = :userId AND m.communityId IN :ids")
    List<Long> findJoinedCommunityIds(@Param("userId") Long userId, @Param("ids") Collection<Long> communityIds);

    @Modifying
    @Query("DELETE FROM CommunityMember m WHERE m.communityId = :communityId AND m.userId = :userId")
    int deleteMembership(@Param("communityId") Long communityId, @Param("userId") Long userId);
}
