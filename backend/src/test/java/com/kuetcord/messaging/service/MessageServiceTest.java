package com.kuetcord.messaging.service;

import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.messaging.access.ConversationAccessPolicy;
import com.kuetcord.messaging.model.Message;
import com.kuetcord.messaging.repository.ConversationReadStateRepository;
import com.kuetcord.messaging.repository.ConversationRepository;
import com.kuetcord.messaging.repository.MessageMentionRepository;
import com.kuetcord.messaging.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for idempotent message sending and input validation (no database).
 */
class MessageServiceTest {

    private static final long CONVERSATION = 12L;
    private static final long SENDER = 101L;
    private static final String KEY = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d";

    private MessageRepository messageRepository;
    private ConversationAccessPolicy accessPolicy;
    private MessageService service;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        accessPolicy = mock(ConversationAccessPolicy.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        service = new MessageService(messageRepository, mock(MessageMentionRepository.class),
                mock(ConversationRepository.class), mock(ConversationReadStateRepository.class),
                accessPolicy, userRepository);
    }

    @Test
    @DisplayName("Retry with an already-persisted idempotency key returns the original row and inserts nothing")
    void shouldDeduplicateMessageSendWithIdenticalIdempotencyKey() {
        Message original = Message.builder().id(1055L).conversationId(CONVERSATION).senderId(SENDER)
                .content("Can someone share the link?").idempotencyKey(KEY).createdAt(Instant.now()).build();
        when(messageRepository.findByConversationIdAndSenderIdAndIdempotencyKey(CONVERSATION, SENDER, KEY))
                .thenReturn(Optional.of(original));

        MessageService.SendResult result = service.send(CONVERSATION, SENDER, "Can someone share the link?", KEY);

        assertFalse(result.created());
        assertEquals(1055L, result.message().id());
        verify(messageRepository, never()).insertIfAbsent(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Invalid input is rejected before any authorization or database work")
    void shouldRejectInvalidContentAndKeysUpFront() {
        String tooLong = "x".repeat(Message.MAX_CONTENT_LENGTH + 1);

        assertThrows(BadRequestException.class, () -> service.send(CONVERSATION, SENDER, tooLong, KEY));
        assertThrows(BadRequestException.class, () -> service.send(CONVERSATION, SENDER, "   ", KEY));
        assertThrows(BadRequestException.class, () -> service.send(CONVERSATION, SENDER, "hi", null));
        assertThrows(BadRequestException.class, () -> service.send(CONVERSATION, SENDER, "hi", "k".repeat(65)));

        verifyNoInteractions(accessPolicy, messageRepository);
    }

    @Test
    @DisplayName("Exactly 2,000 characters is accepted; a non-member is refused with 403")
    void shouldAcceptMaxLengthButRefuseNonMembers() {
        String max = "a".repeat(Message.MAX_CONTENT_LENGTH);
        when(accessPolicy.requireSend(CONVERSATION, SENDER)).thenThrow(new ForbiddenException(ConversationAccessPolicy.DENIED_MESSAGE));

        assertThrows(ForbiddenException.class, () -> service.send(CONVERSATION, SENDER, max, KEY));
        verify(messageRepository, never()).insertIfAbsent(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Mention parser: distinct lowercase usernames, ignores emails, trims trailing punctuation")
    void shouldParseMentions() {
        Set<String> mentions = MentionParser.parse(
                "Thanks @Sadia.Islam and @rahim_2k21! cc @sadia.islam, mail rahim@stud.kuet.ac.bd, ping @jo.");

        assertEquals(List.of("sadia.islam", "rahim_2k21", "jo"), List.copyOf(mentions));
        assertEquals(Set.of(), MentionParser.parse("no mentions here"));
        assertEquals(MentionParser.MAX_MENTIONS_PER_MESSAGE,
                MentionParser.parse("@u1 @u2 @u3 @u4 @u5 @u6 @u7 @u8 @u9 @u10 @u11 @u12 @u13 @u14 @u15 @u16 @u17 @u18 @u19 @u20 @u21 @u22")
                        .size());
    }
}
