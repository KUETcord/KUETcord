package com.kuetcord.commons.service;

import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.commons.dto.BanRequest;
import com.kuetcord.commons.dto.ChannelDto;
import com.kuetcord.commons.dto.CommunityDetailDto;
import com.kuetcord.commons.dto.CommunityMemberDto;
import com.kuetcord.commons.dto.CommunitySummaryDto;
import com.kuetcord.commons.dto.CreateChannelRequest;
import com.kuetcord.commons.dto.CreateCommunityRequest;
import com.kuetcord.commons.dto.RoleDto;
import com.kuetcord.commons.dto.RoleRequest;
import com.kuetcord.commons.model.Community;
import com.kuetcord.commons.model.CommunityBan;
import com.kuetcord.commons.model.CommunityMember;
import com.kuetcord.commons.model.CommunityMemberRole;
import com.kuetcord.commons.model.CommunityPermission;
import com.kuetcord.commons.model.CommunityRole;
import com.kuetcord.commons.model.CommunityVisibility;
import com.kuetcord.commons.repository.CommunityBanRepository;
import com.kuetcord.commons.repository.CommunityMemberRepository;
import com.kuetcord.commons.repository.CommunityMemberRoleRepository;
import com.kuetcord.commons.repository.CommunityRepository;
import com.kuetcord.commons.repository.CommunityRoleRepository;
import com.kuetcord.commons.security.CommunityAuthorityService;
import com.kuetcord.commons.security.MemberAuthority;
import com.kuetcord.messaging.access.ConversationAccessPolicy;
import com.kuetcord.messaging.model.Conversation;
import com.kuetcord.messaging.model.ConversationMember;
import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.messaging.model.GroupRole;
import com.kuetcord.messaging.repository.ConversationMemberRepository;
import com.kuetcord.messaging.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Campus Commons: Discord-style communities, channels, roles and moderation.
 * <p>
 * Endpoint-level permission checks live in {@code @PreAuthorize}; this service enforces the
 * rules SpEL cannot express: role hierarchy, no privilege escalation, owner immunity.
 * Every mutation that can change someone's authority evicts the permission cache.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityService {

    public static final String DEFAULT_ROLE_NAME = "Member";
    public static final String DEFAULT_CHANNEL_NAME = "general";
    private static final int MAX_PAGE_SIZE = 50;

    private final CommunityRepository communityRepository;
    private final CommunityRoleRepository roleRepository;
    private final CommunityMemberRepository memberRepository;
    private final CommunityMemberRoleRepository memberRoleRepository;
    private final CommunityBanRepository banRepository;
    private final CommunityAuthorityService authorityService;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationAccessPolicy accessPolicy;
    private final UserRepository userRepository;

    // ----------------------------------------------------------------- browse & detail

    public List<CommunitySummaryDto> browse(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<Community> communities = communityRepository.findBrowsable(
                userId, CommunityVisibility.PUBLIC, PageRequest.of(Math.max(page, 0), safeSize));
        if (communities.isEmpty()) {
            return List.of();
        }
        List<Long> ids = communities.stream().map(Community::getId).toList();
        Map<Long, Long> counts = memberRepository.countMembersByCommunityIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Set<Long> joined = new HashSet<>(memberRepository.findJoinedCommunityIds(userId, ids));
        return communities.stream()
                .map(c -> CommunitySummaryDto.from(c, counts.getOrDefault(c.getId(), 0L), joined.contains(c.getId())))
                .toList();
    }

    public CommunityDetailDto getDetail(Long communityId, Long userId) {
        Community community = requireCommunity(communityId);
        MemberAuthority authority = authorityService.authorityOf(communityId, userId);

        List<CommunityMember> members = memberRepository.findByCommunityIdOrderByJoinedAtAsc(communityId);
        Map<Long, User> users = usersById(members.stream().map(CommunityMember::getUserId).toList());
        Map<Long, List<Long>> rolesByUser = memberRoleRepository.findByCommunityId(communityId).stream()
                .collect(Collectors.groupingBy(mr -> mr.getId().getUserId(),
                        Collectors.mapping(mr -> mr.getId().getRoleId(), Collectors.toList())));

        List<CommunityMemberDto> memberDtos = members.stream()
                .map(m -> {
                    User user = users.get(m.getUserId());
                    return new CommunityMemberDto(m.getUserId(),
                            user != null ? user.getDisplayName() : null,
                            user != null ? user.getUsername() : null,
                            m.getNickname(),
                            community.isOwnedBy(m.getUserId()),
                            rolesByUser.getOrDefault(m.getUserId(), List.of()),
                            m.getJoinedAt());
                })
                .toList();

        List<RoleDto> roles = roleRepository.findByCommunityIdOrderByPositionDesc(communityId).stream()
                .map(RoleDto::from)
                .toList();

        return new CommunityDetailDto(community.getId(), community.getName(), community.getDescription(),
                community.getVisibility(), community.getOwnerId(), community.getCreatedAt(),
                visibleChannels(communityId, userId), roles, memberDtos,
                authority.owner() ? CommunityPermission.fromMask(CommunityPermission.ALL)
                        : CommunityPermission.fromMask(authority.permissions()));
    }

    public List<ChannelDto> visibleChannels(Long communityId, Long userId) {
        return conversationRepository.findByCommunityIdAndTypeOrderByIdAsc(communityId, ConversationType.CHANNEL).stream()
                .filter(channel -> accessPolicy.canAccess(channel, userId))
                .map(ChannelDto::from)
                .toList();
    }

    // ----------------------------------------------------------------- lifecycle

    /**
     * Creates the community with its owner membership, an implicit default role and a #general channel.
     */
    @Transactional
    @CacheEvict(cacheNames = CommunityAuthorityService.CACHE_NAME, allEntries = true)
    public CommunityDetailDto create(Long ownerId, CreateCommunityRequest request) {
        Community community = communityRepository.save(Community.builder()
                .name(request.name().trim())
                .description(trimToNull(request.description()))
                .visibility(request.visibility() != null ? request.visibility() : CommunityVisibility.PUBLIC)
                .ownerId(ownerId)
                .build());

        roleRepository.save(CommunityRole.builder()
                .communityId(community.getId())
                .name(DEFAULT_ROLE_NAME)
                .position(0)
                .permissions(CommunityPermission.DEFAULT_MEMBER)
                .defaultRole(true)
                .build());

        memberRepository.save(CommunityMember.builder()
                .communityId(community.getId())
                .userId(ownerId)
                .build());

        conversationRepository.save(Conversation.builder()
                .type(ConversationType.CHANNEL)
                .title(DEFAULT_CHANNEL_NAME)
                .communityId(community.getId())
                .creatorId(ownerId)
                .build());

        return getDetail(community.getId(), ownerId);
    }

    @Transactional
    @CacheEvict(cacheNames = CommunityAuthorityService.CACHE_NAME, allEntries = true)
    public CommunityDetailDto join(Long communityId, Long userId) {
        Community community = requireCommunity(communityId);
        if (banRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw new ForbiddenException("You are banned from this community");
        }
        if (!memberRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            if (community.getVisibility() != CommunityVisibility.PUBLIC) {
                throw new ForbiddenException("This community is private and requires an invitation");
            }
            memberRepository.save(CommunityMember.builder().communityId(communityId).userId(userId).build());
        }
        return getDetail(communityId, userId);
    }

    // ----------------------------------------------------------------- channels

    @Transactional
    public ChannelDto createChannel(Long communityId, Long actorId, CreateChannelRequest request) {
        requireCommunity(communityId);
        String name = normalizeChannelName(request.name());
        Conversation channel = conversationRepository.save(Conversation.builder()
                .type(ConversationType.CHANNEL)
                .title(name)
                .communityId(communityId)
                .creatorId(actorId)
                .privateChannel(request.privateChannel())
                .build());
        if (request.privateChannel()) {
            // The creator must be able to see the private channel they just made.
            conversationMemberRepository.save(ConversationMember.builder()
                    .conversationId(channel.getId())
                    .userId(actorId)
                    .groupRole(GroupRole.GROUP_OWNER)
                    .build());
        }
        return ChannelDto.from(channel);
    }

    // ----------------------------------------------------------------- roles

    @Transactional
    @CacheEvict(cacheNames = CommunityAuthorityService.CACHE_NAME, allEntries = true)
    public RoleDto createRole(Long communityId, Long actorId, RoleRequest request) {
        requireCommunity(communityId);
        MemberAuthority actor = authorityService.authorityOf(communityId, actorId);
        int position = request.position() != null ? request.position() : 1;
        requireGrantable(actor, request.permissions());
        requireBelowActor(actor, position, "You can only create roles ranked below your highest role");

        CommunityRole role = roleRepository.save(CommunityRole.builder()
                .communityId(communityId)
                .name(request.name().trim())
                .color(request.color() != null ? request.color() : CommunityRole.DEFAULT_COLOR)
                .position(position)
                .permissions(request.permissions())
                .defaultRole(false)
                .build());
        return RoleDto.from(role);
    }

    @Transactional
    @CacheEvict(cacheNames = CommunityAuthorityService.CACHE_NAME, allEntries = true)
    public RoleDto updateRole(Long communityId, Long roleId, Long actorId, RoleRequest request) {
        CommunityRole role = requireRole(communityId, roleId);
        MemberAuthority actor = authorityService.authorityOf(communityId, actorId);
        requireBelowActor(actor, role.getPosition(), "You can only edit roles ranked below your highest role");
        requireGrantable(actor, request.permissions());

        role.setName(request.name().trim());
        if (request.color() != null) {
            role.setColor(request.color());
        }
        role.setPermissions(request.permissions());
        if (!role.isDefaultRole() && request.position() != null) {
            requireBelowActor(actor, request.position(), "You cannot move a role to or above your highest role");
            role.setPosition(request.position());
        }
        return RoleDto.from(role);
    }

    @Transactional
    @CacheEvict(cacheNames = CommunityAuthorityService.CACHE_NAME, allEntries = true)
    public void assignRole(Long communityId, Long targetUserId, Long roleId, Long actorId) {
        CommunityRole role = requireRole(communityId, roleId);
        if (role.isDefaultRole()) {
            throw new BadRequestException("The default role applies to every member automatically");
        }
        requireMember(communityId, targetUserId);
        MemberAuthority actor = authorityService.authorityOf(communityId, actorId);
        requireBelowActor(actor, role.getPosition(), "You can only assign roles ranked below your highest role");

        CommunityMemberRole.Key key = new CommunityMemberRole.Key(communityId, targetUserId, roleId);
        if (!memberRoleRepository.existsById(key)) {
            memberRoleRepository.save(new CommunityMemberRole(communityId, targetUserId, roleId));
        }
    }

    // ----------------------------------------------------------------- moderation

    @Transactional
    @CacheEvict(cacheNames = CommunityAuthorityService.CACHE_NAME, allEntries = true)
    public void kick(Long communityId, Long targetUserId, Long actorId) {
        Community community = requireCommunity(communityId);
        requireMember(communityId, targetUserId);
        requireModeratable(community, targetUserId, actorId, "kick");
        removeMembership(communityId, targetUserId);
    }

    @Transactional
    @CacheEvict(cacheNames = CommunityAuthorityService.CACHE_NAME, allEntries = true)
    public void ban(Long communityId, Long actorId, BanRequest request) {
        Community community = requireCommunity(communityId);
        Long targetUserId = request.userId();
        if (!userRepository.existsById(targetUserId)) {
            throw new ResourceNotFoundException("User", "id", targetUserId);
        }
        requireModeratable(community, targetUserId, actorId, "ban");
        if (!banRepository.existsByCommunityIdAndUserId(communityId, targetUserId)) {
            banRepository.save(CommunityBan.builder()
                    .communityId(communityId)
                    .userId(targetUserId)
                    .bannedBy(actorId)
                    .reason(trimToNull(request.reason()))
                    .build());
        }
        removeMembership(communityId, targetUserId);
    }

    @Transactional
    @CacheEvict(cacheNames = CommunityAuthorityService.CACHE_NAME, allEntries = true)
    public void unban(Long communityId, Long targetUserId) {
        requireCommunity(communityId);
        if (banRepository.deleteBan(communityId, targetUserId) == 0) {
            throw new ResourceNotFoundException("Ban", "userId", targetUserId);
        }
    }

    // ----------------------------------------------------------------- helpers

    private void requireModeratable(Community community, Long targetUserId, Long actorId, String action) {
        if (targetUserId.equals(actorId)) {
            throw new BadRequestException("You cannot " + action + " yourself");
        }
        if (community.isOwnedBy(targetUserId)) {
            throw new ForbiddenException("The community owner cannot be " + (action.equals("ban") ? "banned" : "kicked"));
        }
        MemberAuthority actor = authorityService.authorityOf(community.getId(), actorId);
        MemberAuthority target = authorityService.authorityOf(community.getId(), targetUserId);
        if (target.member() && !actor.outranks(target)) {
            throw new ForbiddenException("You can only " + action + " members ranked below you");
        }
    }

    private void removeMembership(Long communityId, Long userId) {
        memberRoleRepository.deleteAllForMember(communityId, userId);
        memberRepository.deleteMembership(communityId, userId);
    }

    private static void requireGrantable(MemberAuthority actor, long mask) {
        if (!CommunityPermission.isValidMask(mask)) {
            throw new BadRequestException("Permissions bitmask contains undefined bits");
        }
        if (!actor.canGrant(mask)) {
            throw new ForbiddenException("You cannot grant permissions you do not have");
        }
    }

    private static void requireBelowActor(MemberAuthority actor, int position, String message) {
        if (!actor.outranks(position)) {
            throw new ForbiddenException(message);
        }
    }

    private Community requireCommunity(Long communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", "id", communityId));
    }

    private CommunityRole requireRole(Long communityId, Long roleId) {
        return roleRepository.findByIdAndCommunityId(roleId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));
    }

    private void requireMember(Long communityId, Long userId) {
        if (!memberRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw new ResourceNotFoundException("Community member", "userId", userId);
        }
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** "CSE 3103 Chat" -> "cse-3103-chat" (Discord-style channel slug). */
    static String normalizeChannelName(String raw) {
        String slug = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-").replaceAll("-{2,}", "-");
        if (slug.isEmpty()) {
            throw new BadRequestException("Channel name is required");
        }
        return slug;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
