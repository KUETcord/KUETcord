package com.kuetcord.messaging.access;

import com.kuetcord.commons.model.CommunityPermission;
import com.kuetcord.commons.security.CommunityAuthorityService;
import com.kuetcord.commons.security.MemberAuthority;
import com.kuetcord.messaging.model.Conversation;
import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.messaging.model.MemberStatus;
import com.kuetcord.messaging.repository.ConversationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * CHANNEL: the user must be a (non-banned) member of the owning community.
 * Private channels additionally require explicit channel membership or MANAGE_CHANNELS.
 */
@Component
@RequiredArgsConstructor
public class ChannelAccessResolver implements ConversationAccessResolver {

    private final CommunityAuthorityService authorityService;
    private final ConversationMemberRepository memberRepository;

    @Override
    public Set<ConversationType> supportedTypes() {
        return EnumSet.of(ConversationType.CHANNEL);
    }

    @Override
    public boolean canAccess(Conversation conversation, Long userId) {
        if (conversation.getCommunityId() == null) {
            return false;
        }
        MemberAuthority authority = authorityService.authorityOf(conversation.getCommunityId(), userId);
        if (!authority.member()) {
            return false;
        }
        if (!conversation.isPrivateChannel()) {
            return true;
        }
        return authority.has(CommunityPermission.MANAGE_CHANNELS)
                || memberRepository.existsByConversationIdAndUserIdAndActiveStatus(
                        conversation.getId(), userId, MemberStatus.ACTIVE);
    }
}
