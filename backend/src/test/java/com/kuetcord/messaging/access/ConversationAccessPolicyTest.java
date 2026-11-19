package com.kuetcord.messaging.access;

import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.commons.model.CommunityPermission;
import com.kuetcord.commons.security.CommunityAuthorityService;
import com.kuetcord.commons.security.MemberAuthority;
import com.kuetcord.messaging.model.Conversation;
import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.messaging.model.MemberStatus;
import com.kuetcord.messaging.repository.ConversationMemberRepository;
import com.kuetcord.messaging.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for type-aware conversation authorization (fail-closed policy).
 */
class ConversationAccessPolicyTest {

    private static final long MEMBER = 1L;
    private static final long STRANGER = 2L;
    private static final long REMOVED = 3L;
    private static final long COMMUNITY_ID = 50L;

    private ConversationRepository conversationRepository;
    private ConversationMemberRepository memberRepository;
    private CommunityAuthorityService authorityService;
    private ConversationAccessPolicy policy;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        memberRepository = mock(ConversationMemberRepository.class);
        authorityService = mock(CommunityAuthorityService.class);
        policy = new ConversationAccessPolicy(conversationRepository, List.of(
                new ParticipantAccessResolver(memberRepository),
                new ChannelAccessResolver(authorityService, memberRepository)));
    }

    @Test
    @DisplayName("GROUP_DM: active participant allowed, non-member denied")
    void shouldDenyNonMemberOfGroupDm() {
        Conversation group = conversation(100L, ConversationType.GROUP_DM);
        activeMember(100L, MEMBER, true);
        activeMember(100L, STRANGER, false);

        assertTrue(policy.canAccess(100L, MEMBER));
        assertFalse(policy.canAccess(100L, STRANGER));
        assertThrows(ForbiddenException.class, () -> policy.requireAccess(group.getId(), STRANGER));
    }

    @Test
    @DisplayName("GROUP_DM: a REMOVED member immediately loses access")
    void shouldDenyRemovedMember() {
        conversation(101L, ConversationType.GROUP_DM);
        // Only ACTIVE rows count; the removed user's row exists but with status REMOVED.
        activeMember(101L, REMOVED, false);

        assertFalse(policy.canAccess(101L, REMOVED));
    }

    @Test
    @DisplayName("Unknown IDs and types without a resolver (PLAYGROUND) are denied: fail-closed")
    void shouldFailClosedForUnknownConversationsAndUnresolvedTypes() {
        when(conversationRepository.findById(404L)).thenReturn(Optional.empty());
        conversation(102L, ConversationType.PLAYGROUND);
        activeMember(102L, MEMBER, true);

        assertFalse(policy.canAccess(404L, MEMBER));
        assertFalse(policy.canAccess(102L, MEMBER), "No PLAYGROUND resolver registered yet: must deny");
        assertFalse(policy.canAccess(102L, null));
    }

    @Test
    @DisplayName("CHANNEL: community members see public channels; private needs channel membership or MANAGE_CHANNELS")
    void shouldApplyCommunityRulesToChannels() {
        Conversation open = conversation(200L, ConversationType.CHANNEL);
        open.setCommunityId(COMMUNITY_ID);
        Conversation secret = conversation(201L, ConversationType.CHANNEL);
        secret.setCommunityId(COMMUNITY_ID);
        secret.setPrivateChannel(true);

        MemberAuthority plainMember = new MemberAuthority(true, false, CommunityPermission.SEND_MESSAGES.bit(), 0);
        MemberAuthority channelManager = new MemberAuthority(true, false, CommunityPermission.MANAGE_CHANNELS.bit(), 3);
        when(authorityService.authorityOf(COMMUNITY_ID, MEMBER)).thenReturn(plainMember);
        when(authorityService.authorityOf(COMMUNITY_ID, STRANGER)).thenReturn(MemberAuthority.NONE);
        when(authorityService.authorityOf(COMMUNITY_ID, 9L)).thenReturn(channelManager);
        activeMember(201L, MEMBER, false);

        assertTrue(policy.canAccess(200L, MEMBER));
        assertFalse(policy.canAccess(200L, STRANGER), "Outsiders cannot read public channels");
        assertFalse(policy.canAccess(201L, MEMBER), "Private channel requires explicit membership");
        assertTrue(policy.canAccess(201L, 9L), "MANAGE_CHANNELS can see private channels");
    }

    @Test
    @DisplayName("Registering two resolvers for the same type is a startup error")
    void shouldRejectDuplicateResolvers() {
        assertThrows(IllegalStateException.class, () -> new ConversationAccessPolicy(conversationRepository, List.of(
                new ParticipantAccessResolver(memberRepository),
                new ParticipantAccessResolver(memberRepository))));
    }

    private Conversation conversation(long id, ConversationType type) {
        Conversation conversation = Conversation.builder().id(id).type(type).build();
        when(conversationRepository.findById(id)).thenReturn(Optional.of(conversation));
        return conversation;
    }

    private void activeMember(long conversationId, long userId, boolean active) {
        when(memberRepository.existsByConversationIdAndUserIdAndActiveStatus(conversationId, userId, MemberStatus.ACTIVE))
                .thenReturn(active);
    }
}
