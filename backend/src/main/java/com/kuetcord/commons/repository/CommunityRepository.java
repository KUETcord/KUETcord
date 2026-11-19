package com.kuetcord.commons.repository;

import com.kuetcord.commons.model.Community;
import com.kuetcord.commons.model.CommunityVisibility;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    /** Browse list: every public community plus any private community the user belongs to. */
    @Query("""
            SELECT c FROM Community c
            WHERE c.visibility = :publicVisibility
               OR EXISTS (SELECT 1 FROM CommunityMember m WHERE m.communityId = c.id AND m.userId = :userId)
            ORDER BY c.id DESC
            """)
    List<Community> findBrowsable(@Param("userId") Long userId,
                                  @Param("publicVisibility") CommunityVisibility publicVisibility,
                                  Pageable pageable);
}
