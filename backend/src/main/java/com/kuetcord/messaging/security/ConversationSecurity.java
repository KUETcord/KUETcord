package com.kuetcord.messaging.security;

import com.kuetcord.common.security.CurrentUser;
import com.kuetcord.messaging.access.ConversationAccessPolicy;
import com.kuetcord.messaging.model.ConversationMember;
import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.messaging.model.GroupRole;
import com.kuetcord.messaging.repository.ConversationMemberRepository;
import com.kuetcord.messaging.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * SpEL entry point for Group DM RBAC, e.g.
 * {@code @PreAuthorize("@conversationSecurity.canAddMember(#id)")}.
 * <p>
 * The acting user is read from the security context rather than passed from SpEL,
 * because {@code AuthenticatedUser} is a record without a {@code username} bean property.
 * <pre>
 *                  rename  add   remove MEMBER  remove ADMIN  promote/demote
 *   GROUP_OWNER      yes   yes       yes            yes            yes
 *   GROUP_ADMIN      yes   yes       yes            no             no
 *   GROUP_MEMBER     no    no        no             no             no
 * </pre>
 * Nobody can remove the owner or themselves (use leave instead).
 */
@Component("conversationSecurity")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationSecurity {

    private final ConversationAccessPolicy accessPolicy;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;

    public boolean isParticipant(Long conversationId) {
        return CurrentUser.id()
                .map(userId -> accessPolicy.canAccess(conversationId, userId))
                .orElse(false);
    }

    public boolean canManageGroup(Long conversationId) {
        return actingGroupMember(conversationId)
                .map(member -> member.getGroupRole().canManageMembers())
                .orElse(false);
    }

    public boolean canAddMember(Long conversationId) {
        return canManageGroup(conversationId);
    }

    public boolean canRemoveMember(Long conversationId, Long targetUserId) {
        Optional<ConversationMember> actor = actingGroupMember(conversationId);
        if (actor.isEmpty() || !actor.get().getGroupRole().canManageMembers()) {
            return false;
        }
        if (actor.get().getUserId().equals(targetUserId)) {
            return false;
        }
        Optional<ConversationMember> target = memberRepository.findByConversationIdAndUserId(conversationId, targetUserId)
                .filter(ConversationMember::isActive);
        if (target.isEmpty()) {
            // Let the service answer 404 for a non-participant target.
            return true;
        }
        GroupRole targetRole = target.get().getGroupRole();
        return switch (actor.get().getGroupRole()) {
            case GROUP_OWNER -> targetRole != GroupRole.GROUP_OWNER;
            case GROUP_ADMIN -> targetRole == GroupRole.GROUP_MEMBER;
            case GROUP_MEMBER -> false;
        };
    }

    public boolean isOwner(Long conversationId) {
        return actingGroupMember(conversationId)
                .map(member -> member.getGroupRole() == GroupRole.GROUP_OWNER)
                .orElse(false);
    }

    /** Active membership of the current user in a GROUP_DM; empty for any other type. */
    private Optional<ConversationMember> actingGroupMember(Long conversationId) {
        Optional<Long> userId = CurrentUser.id();
        if (userId.isEmpty() || conversationId == null) {
            return Optional.empty();
        }
        boolean isGroup = conversationRepository.findById(conversationId)
                .map(conversation -> conversation.getType() == ConversationType.GROUP_DM)
                .orElse(false);
        if (!isGroup) {
            return Optional.empty();
        }
        return memberRepository.findByConversationIdAndUserId(conversationId, userId.get())
                .filter(ConversationMember::isActive);
    }
}
