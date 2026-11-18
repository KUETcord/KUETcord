package com.kuetcord.commons.repository;

import com.kuetcord.commons.model.CommunityMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommunityMemberRoleRepository extends JpaRepository<CommunityMemberRole, CommunityMemberRole.Key> {

    /** All explicit role assignments in a community, for building the member list in one query. */
    @Query("SELECT mr FROM CommunityMemberRole mr WHERE mr.id.communityId = :communityId")
    List<CommunityMemberRole> findByCommunityId(@Param("communityId") Long communityId);

    @Modifying
    @Query("DELETE FROM CommunityMemberRole mr WHERE mr.id.communityId = :communityId AND mr.id.userId = :userId")
    int deleteAllForMember(@Param("communityId") Long communityId, @Param("userId") Long userId);
}
