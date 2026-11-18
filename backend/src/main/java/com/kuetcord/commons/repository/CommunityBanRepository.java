package com.kuetcord.commons.repository;

import com.kuetcord.commons.model.CommunityBan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityBanRepository extends JpaRepository<CommunityBan, Long> {

    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);

    @Modifying
    @Query("DELETE FROM CommunityBan b WHERE b.communityId = :communityId AND b.userId = :userId")
    int deleteBan(@Param("communityId") Long communityId, @Param("userId") Long userId);
}
