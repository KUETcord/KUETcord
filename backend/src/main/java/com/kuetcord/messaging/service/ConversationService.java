package com.kuetcord.messaging.service;

import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.common.exception.ConflictException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.messaging.access.ConversationAccessPolicy;
import com.kuetcord.messaging.dto.ConversationDetailDto;
import com.kuetcord.messaging.dto.ConversationMemberDto;
import com.kuetcord.messaging.dto.ConversationSummaryDto;
import com.kuetcord.messaging.dto.CreateGroupRequest;
import com.kuetcord.messaging.model.Conversation;
import com.kuetcord.messaging.model.ConversationMember;
import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.messaging.model.GroupRole;
import com.kuetcord.messaging.model.MemberStatus;
import com.kuetcord.messaging.repository.ConversationMemberRepository;
import com.kuetcord.messaging.repository.ConversationReadStateRepository;
import com.kuetcord.messaging.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Unified conversation manager: 1:1 DMs, group DMs and their Group RBAC lifecycle.
 * Endpoint-level role checks are in {@code @PreAuthorize} ({@code ConversationSecurity});
 * this service enforces state rules (size limits, eligibility, ownership hand-over).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

    public static final int MIN_GROUP_SIZE = 3;
    public static final int MAX_GROUP_SIZE = 25;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final ConversationAccessPolicy accessPolicy;
    private final UserRepository userRepository;
    private final ConversationReadStateRepository readStateRepository;

    /** Result of opening a DM: whether a new conversation was created (201) or reused (200). */
    public record OpenDirectResult(ConversationDetailDto conversation, boolean created) {
    }

    // ----------------------------------------------------------------- reads

    public List<ConversationSummaryDto> listForUser(Long userId) {
        List<Conversation> conversations = conversationRepository.findActiveForUser(
                userId, MemberStatus.ACTIVE, ConversationType.CHANNEL);
        if (conversations.isEmpty()) {
            return List.of();
        }
        List<Long> ids = conversations.stream().map(Conversation::getId).toList();
        Map<Long, List<ConversationMember>> membersByConversation = memberRepository
                .findByConversationIdInAndActiveStatus(ids, MemberStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(ConversationMember::getConversationId));

        Set<Long> peerIds = conversations.stream()
                .filter(c -> c.getType() == ConversationType.DIRECT)
                .map(c -> peerOf(membersByConversation.getOrDefault(c.getId(), List.of()), userId))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> peers = usersById(peerIds);
        Map<Long, Long> unread = readStateRepository.countUnread(userId, ids).stream()
                .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> ((Number) row[1]).longValue()));

        return conversations.stream()
                .map(c -> {
                    List<ConversationMember> members = membersByConversation.getOrDefault(c.getId(), List.of());
                    Long peerId = c.getType() == ConversationType.DIRECT ? peerOf(members, userId) : null;
                    User peer = peerId != null ? peers.get(peerId) : null;
                    String displayTitle = peer != null ? peer.getDisplayName() : c.getTitle();
                    return new ConversationSummaryDto(c.getId(), c.getType(), c.getTitle(), displayTitle,
                            peerId, members.size(), unread.getOrDefault(c.getId(), 0L), c.getUpdatedAt());
                })
                .toList();
    }

    public ConversationDetailDto getDetail(Long conversationId, Long userId) {
        Conversation conversation = accessPolicy.requireAccess(conversationId, userId);
        return toDetail(conversation, userId);
    }

    // ----------------------------------------------------------------- DMs

    @Transactional
    public OpenDirectResult openDirect(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BadRequestException("You cannot open a direct message with yourself");
        }
        requireEligibleUsers(List.of(targetUserId));

        String directKey = Conversation.directKeyFor(userId, targetUserId);
        boolean created = conversationRepository.insertDirectIfAbsent(userId, directKey) == 1;
        Conversation conversation = conversationRepository.findByDirectKey(directKey)
                .orElseThrow(() -> new IllegalStateException("DIRECT conversation vanished for key " + directKey));
        if (created) {
            memberRepository.save(newMember(conversation.getId(), userId, GroupRole.GROUP_MEMBER));
            memberRepository.save(newMember(conversation.getId(), targetUserId, GroupRole.GROUP_MEMBER));
        }
        return new OpenDirectResult(toDetail(conversation, userId), created);
    }

    // ----------------------------------------------------------------- group DMs

    @Transactional
    public ConversationDetailDto createGroup(Long ownerId, CreateGroupRequest request) {
        Set<Long> others = new LinkedHashSet<>(request.memberIds());
        others.remove(ownerId);
        int total = others.size() + 1;
        if (total < MIN_GROUP_SIZE || total > MAX_GROUP_SIZE) {
            throw new BadRequestException("A group DM needs between " + MIN_GROUP_SIZE + " and " + MAX_GROUP_SIZE
                    + " participants including you (got " + total + ")");
        }
        requireEligibleUsers(others);

        Conversation conversation = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP_DM)
                .title(request.title().trim())
                .creatorId(ownerId)
                .build());

        List<ConversationMember> members = new ArrayList<>();
        members.add(newMember(conversation.getId(), ownerId, GroupRole.GROUP_OWNER));
        others.forEach(id -> members.add(newMember(conversation.getId(), id, GroupRole.GROUP_MEMBER)));
        memberRepository.saveAll(members);

        return toDetail(conversation, ownerId);
    }

    @Transactional
    public ConversationDetailDto rename(Long conversationId, Long actorId, String title) {
        Conversation conversation = requireGroup(conversationId);
        conversation.setTitle(title.trim());
        conversation.touch();
        return toDetail(conversation, actorId);
    }

    @Transactional
    public ConversationDetailDto addMember(Long conversationId, Long actorId, Long targetUserId) {
        Conversation conversation = requireGroup(conversationId);
        requireEligibleUsers(List.of(targetUserId));

        ConversationMember existing = memberRepository.findByConversationIdAndUserId(conversationId, targetUserId).orElse(null);
        if (existing != null && existing.isActive()) {
            throw new ConflictException("User is already a member of this group");
        }
        if (memberRepository.countByConversationIdAndActiveStatus(conversationId, MemberStatus.ACTIVE) >= MAX_GROUP_SIZE) {
            throw new ConflictException("Group DMs are limited to " + MAX_GROUP_SIZE + " participants");
        }
        if (existing != null) {
            existing.reactivate(GroupRole.GROUP_MEMBER);
        } else {
            memberRepository.save(newMember(conversationId, targetUserId, GroupRole.GROUP_MEMBER));
        }
        conversation.touch();
        return toDetail(conversation, actorId);
    }

    @Transactional
    public void removeMember(Long conversationId, Long targetUserId) {
        Conversation conversation = requireGroup(conversationId);
        ConversationMember target = requireActiveMember(conversationId, targetUserId);
        target.deactivate(MemberStatus.REMOVED);
        conversation.touch();
    }

    @Transactional
    public ConversationDetailDto changeRole(Long conversationId, Long actorId, Long targetUserId, GroupRole role) {
        Conversation conversation = requireGroup(conversationId);
        if (role == GroupRole.GROUP_OWNER) {
            throw new BadRequestException("Ownership cannot be assigned through role changes");
        }
        if (targetUserId.equals(actorId)) {
            throw new BadRequestException("The owner's role cannot be changed");
        }
        ConversationMember target = requireActiveMember(conversationId, targetUserId);
        target.setGroupRole(role);
        return toDetail(conversation, actorId);
    }

    /**
     * Voluntary exit. If the owner leaves, ownership passes to the longest-standing admin,
     * otherwise to the longest-standing member, so the group never becomes unmanageable.
     */
    @Transactional
    public void leave(Long conversationId, Long userId) {
        Conversation conversation = requireGroup(conversationId);
        ConversationMember self = requireActiveMember(conversationId, userId);
        self.deactivate(MemberStatus.LEFT);

        if (self.getGroupRole() == GroupRole.GROUP_OWNER) {
            List<ConversationMember> remaining = memberRepository
                    .findByConversationIdAndActiveStatusOrderByJoinedAtAsc(conversationId, MemberStatus.ACTIVE).stream()
                    .filter(m -> !m.getUserId().equals(userId))
                    .toList();
            remaining.stream()
                    .min(Comparator.comparing((ConversationMember m) -> m.getGroupRole() != GroupRole.GROUP_ADMIN)
                            .thenComparing(ConversationMember::getJoinedAt))
                    .ifPresent(successor -> successor.setGroupRole(GroupRole.GROUP_OWNER));
            self.setGroupRole(GroupRole.GROUP_MEMBER);
        }
        conversation.touch();
    }

    // ----------------------------------------------------------------- helpers

    private ConversationDetailDto toDetail(Conversation conversation, Long viewerId) {
        List<ConversationMember> members = memberRepository
                .findByConversationIdAndActiveStatusOrderByJoinedAtAsc(conversation.getId(), MemberStatus.ACTIVE);
        Map<Long, User> users = usersById(members.stream().map(ConversationMember::getUserId).toList());

        List<ConversationMemberDto> memberDtos = members.stream()
                .map(m -> {
                    User user = users.get(m.getUserId());
                    return new ConversationMemberDto(m.getUserId(),
                            user != null ? user.getDisplayName() : null,
                            user != null ? user.getUsername() : null,
                            m.getGroupRole(), m.getJoinedAt());
                })
                .toList();

        GroupRole myRole = conversation.getType() == ConversationType.GROUP_DM
                ? members.stream().filter(m -> m.getUserId().equals(viewerId)).map(ConversationMember::getGroupRole)
                        .findFirst().orElse(null)
                : null;

        String displayTitle = conversation.getTitle();
        if (conversation.getType() == ConversationType.DIRECT) {
            Long peerId = peerOf(members, viewerId);
            User peer = peerId != null ? users.get(peerId) : null;
            displayTitle = peer != null ? peer.getDisplayName() : displayTitle;
        }

        return new ConversationDetailDto(conversation.getId(), conversation.getType(), conversation.getTitle(),
                displayTitle, conversation.getCommunityId(), conversation.getCreatorId(), myRole, memberDtos,
                conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private Conversation requireGroup(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));
        if (conversation.getType() != ConversationType.GROUP_DM) {
            throw new BadRequestException("This action is only available for group DMs");
        }
        return conversation;
    }

    private ConversationMember requireActiveMember(Long conversationId, Long userId) {
        return memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .filter(ConversationMember::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation member", "userId", userId));
    }

    /** Every target must exist, be ACTIVE and have a verified institutional email. */
    private void requireEligibleUsers(Collection<Long> userIds) {
        Map<Long, User> users = usersById(userIds);
        List<Long> ineligible = userIds.stream()
                .filter(id -> {
                    User user = users.get(id);
                    return user == null || !user.isActive() || !user.isEmailVerified();
                })
                .toList();
        if (!ineligible.isEmpty()) {
            throw new BadRequestException("These users cannot be added to a conversation: " + ineligible);
        }
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static Long peerOf(List<ConversationMember> members, Long userId) {
        return members.stream().map(ConversationMember::getUserId).filter(id -> !id.equals(userId)).findFirst().orElse(null);
    }

    private static ConversationMember newMember(Long conversationId, Long userId, GroupRole role) {
        return ConversationMember.builder().conversationId(conversationId).userId(userId).groupRole(role).build();
    }
}
