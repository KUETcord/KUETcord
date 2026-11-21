package com.kuetcord.messaging.websocket;

import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.messaging.dto.SendMessageFrame;
import com.kuetcord.messaging.dto.StompErrorDto;
import com.kuetcord.messaging.service.MessageBroadcaster;
import com.kuetcord.messaging.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Ingress of the Web-Queue-Worker pipeline for {@code SEND /app/conversations/{id}/messages}:
 * <ol>
 *   <li>validate + authorize + persist with the client idempotency key (commits here);</li>
 *   <li>send STOMP {@code RECEIPT receipt-id:<receipt>} to the sender only after the commit;</li>
 *   <li>enqueue fan-out to {@code /topic/conversations/{id}} on the fanout-worker pool.</li>
 * </ol>
 * A retry of an already-persisted key is ACKed again but not re-broadcast.
 */
@Controller
public class ChatMessageController {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageController.class);
    private static final String RECEIPT_HEADER = "receipt";

    private final MessageService messageService;
    private final MessageBroadcaster broadcaster;
    private final MessageChannel clientOutboundChannel;

    public ChatMessageController(MessageService messageService,
                                 MessageBroadcaster broadcaster,
                                 @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
        this.messageService = messageService;
        this.broadcaster = broadcaster;
        this.clientOutboundChannel = clientOutboundChannel;
    }

    @MessageMapping("/conversations/{conversationId}/messages")
    public void send(@DestinationVariable Long conversationId,
                     @Payload SendMessageFrame frame,
                     Principal principal,
                     SimpMessageHeaderAccessor headers) {
        Long senderId = ((StompPrincipal) principal).userId();
        MessageService.SendResult result = messageService.send(conversationId, senderId, frame.content(), frame.idempotencyKey());

        sendReceipt(headers);
        if (result.created()) {
            broadcaster.broadcast(result.message());
        }
    }

    @MessageExceptionHandler({BadRequestException.class, ForbiddenException.class})
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public StompErrorDto handleRejected(RuntimeException ex, SimpMessageHeaderAccessor headers) {
        String code = ex instanceof ForbiddenException ? "FORBIDDEN" : "BAD_REQUEST";
        return new StompErrorDto(code, ex.getMessage(), headers.getFirstNativeHeader(RECEIPT_HEADER));
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public StompErrorDto handleUnexpected(Exception ex, SimpMessageHeaderAccessor headers) {
        log.error("Unexpected failure handling STOMP message", ex);
        return new StompErrorDto("INTERNAL_ERROR", "Message could not be processed", headers.getFirstNativeHeader(RECEIPT_HEADER));
    }

    /**
     * The simple broker does not emit RECEIPT frames for application destinations, so we send
     * one explicitly to the originating session once the message is durable.
     */
    private void sendReceipt(SimpMessageHeaderAccessor headers) {
        String receiptId = headers.getFirstNativeHeader(RECEIPT_HEADER);
        if (receiptId == null || headers.getSessionId() == null) {
            return;
        }
        StompHeaderAccessor receipt = StompHeaderAccessor.create(StompCommand.RECEIPT);
        receipt.setReceiptId(receiptId);
        receipt.setSessionId(headers.getSessionId());
        clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], receipt.getMessageHeaders()));
    }
}
