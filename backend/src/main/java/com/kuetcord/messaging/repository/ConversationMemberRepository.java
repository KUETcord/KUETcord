package com.kuetcord.messaging.repository;

import com.kuetcord.messaging.model.ConversationMember;
import com.kuetcord.messaging.model.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserIdAndActiveStatus(Long conversationId, Long userId, MemberStatus status);

    List<ConversationMember> findByConversationIdAndActiveStatusOrderByJoinedAtAsc(Long conversationId, MemberStatus status);

    /** Batch fetch for the conversation list to avoid N+1 member lookups. */
    List<ConversationMember> findByConversationIdInAndActiveStatus(Collection<Long> conversationIds, MemberStatus status);

    long countByConversationIdAndActiveStatus(Long conversationId, MemberStatus status);
}
