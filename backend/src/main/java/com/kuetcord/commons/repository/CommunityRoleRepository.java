package com.kuetcord.commons.repository;

import com.kuetcord.commons.model.CommunityRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommunityRoleRepository extends JpaRepository<CommunityRole, Long> {

    List<CommunityRole> findByCommunityIdOrderByPositionDesc(Long communityId);

    Optional<CommunityRole> findByIdAndCommunityId(Long id, Long communityId);

    Optional<CommunityRole> findFirstByCommunityIdAndDefaultRoleTrue(Long communityId);

    /** Explicitly assigned roles of one member (excludes the implicit default role). */
    @Query("""
            SELECT r FROM CommunityRole r
            WHERE r.id IN (SELECT mr.id.roleId FROM CommunityMemberRole mr
                           WHERE mr.id.communityId = :communityId AND mr.id.userId = :userId)
            """)
    List<CommunityRole> findAssignedRoles(@Param("communityId") Long communityId, @Param("userId") Long userId);
}
