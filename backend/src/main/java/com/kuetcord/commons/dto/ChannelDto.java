package com.kuetcord.commons.dto;

import com.kuetcord.messaging.model.Conversation;

import java.time.Instant;

/** A community text channel; {@code id} is the shared conversation ID used by the chat engine. */
public record ChannelDto(
        Long id,
        Long communityId,
        String name,
        boolean privateChannel,
        Instant createdAt
) {
    public static ChannelDto from(Conversation conversation) {
        return new ChannelDto(conversation.getId(), conversation.getCommunityId(), conversation.getTitle(),
                conversation.isPrivateChannel(), conversation.getCreatedAt());
    }
}
