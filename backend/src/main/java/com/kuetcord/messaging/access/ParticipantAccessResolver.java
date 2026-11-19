package com.kuetcord.messaging.access;

import com.kuetcord.messaging.model.Conversation;
import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.messaging.model.MemberStatus;
import com.kuetcord.messaging.repository.ConversationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * DIRECT and GROUP_DM: only ACTIVE participants. LEFT/REMOVED members lose access immediately.
 */
@Component
@RequiredArgsConstructor
public class ParticipantAccessResolver implements ConversationAccessResolver {

    private final ConversationMemberRepository memberRepository;

    @Override
    public Set<ConversationType> supportedTypes() {
        return EnumSet.of(ConversationType.DIRECT, ConversationType.GROUP_DM);
    }

    @Override
    public boolean canAccess(Conversation conversation, Long userId) {
        return memberRepository.existsByConversationIdAndUserIdAndActiveStatus(
                conversation.getId(), userId, MemberStatus.ACTIVE);
    }
}
