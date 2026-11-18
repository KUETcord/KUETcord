package com.kuetcord.commons.security;

import com.kuetcord.commons.model.CommunityRole;
import com.kuetcord.commons.repository.CommunityBanRepository;
import com.kuetcord.commons.repository.CommunityMemberRepository;
import com.kuetcord.commons.repository.CommunityRepository;
import com.kuetcord.commons.repository.CommunityRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resolves {@link MemberAuthority} with a short-lived Caffeine L1 cache (2 min, see CacheConfig).
 * Every chat send/subscribe authorization hits this path, so caching keeps it sub-millisecond.
 * Mutations in {@code CommunityService} evict the whole cache.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityAuthorityService {

    public static final String CACHE_NAME = "community_permissions";

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final CommunityRoleRepository roleRepository;
    private final CommunityBanRepository banRepository;

    @Cacheable(value = CACHE_NAME, key = "#communityId + ':' + #userId")
    public MemberAuthority authorityOf(Long communityId, Long userId) {
        if (communityId == null || userId == null) {
            return MemberAuthority.NONE;
        }
        return communityRepository.findById(communityId)
                .map(community -> {
                    if (!community.isOwnedBy(userId) && banRepository.existsByCommunityIdAndUserId(communityId, userId)) {
                        return MemberAuthority.NONE;
                    }
                    boolean isMember = memberRepository.existsByCommunityIdAndUserId(communityId, userId);
                    CommunityRole defaultRole = roleRepository.findFirstByCommunityIdAndDefaultRoleTrue(communityId).orElse(null);
                    List<CommunityRole> assigned = isMember
                            ? roleRepository.findAssignedRoles(communityId, userId)
                            : List.of();
                    return MemberAuthority.resolve(community, userId, isMember, defaultRole, assigned);
                })
                .orElse(MemberAuthority.NONE);
    }
}
