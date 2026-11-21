package com.kuetcord.messaging.service;

import com.kuetcord.messaging.config.FanoutExecutorConfig;
import com.kuetcord.messaging.dto.ChatMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;

/**
 * Queue -> Worker stage of the Web-Queue-Worker pattern: hands delivery of an already-persisted
 * message to the {@code fanout-worker-} pool so the inbound STOMP thread returns immediately.
 */
@Component
public class MessageBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(MessageBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final TaskExecutor fanoutExecutor;

    public MessageBroadcaster(SimpMessagingTemplate messagingTemplate,
                              @Qualifier(FanoutExecutorConfig.FANOUT_EXECUTOR) TaskExecutor fanoutExecutor) {
        this.messagingTemplate = messagingTemplate;
        this.fanoutExecutor = fanoutExecutor;
    }

    public static String conversationTopic(Long conversationId) {
        return "/topic/conversations/" + conversationId;
    }

    public void broadcast(ChatMessageDto message) {
        try {
            fanoutExecutor.execute(() -> messagingTemplate.convertAndSend(conversationTopic(message.conversationId()), message));
        } catch (RejectedExecutionException ex) {
            // Durable in PostgreSQL already; subscribers recover it via history or /sync on reconnect.
            log.warn("Fan-out queue saturated; dropped live delivery of message {} in conversation {}",
                    message.id(), message.conversationId());
        }
    }
}
