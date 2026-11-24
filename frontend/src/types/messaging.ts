/** Mirrors backend DTOs in com.kuetcord.messaging.dto (Member B). */

export type ConversationType = 'CHANNEL' | 'DIRECT' | 'GROUP_DM' | 'PLAYGROUND' | 'STUDY_CIRCLE';
export type GroupRole = 'GROUP_OWNER' | 'GROUP_ADMIN' | 'GROUP_MEMBER';
export type PresenceStatus = 'ONLINE' | 'IDLE' | 'OFFLINE';

export interface ConversationSummary {
  id: number;
  type: ConversationType;
  title: string | null;
  displayTitle: string | null;
  peerUserId: number | null;
  memberCount: number;
  unreadCount: number;
  updatedAt: string;
}

export interface ConversationMember {
  userId: number;
  displayName: string | null;
  username: string | null;
  groupRole: GroupRole;
  joinedAt: string;
}

export interface ConversationDetail {
  id: number;
  type: ConversationType;
  title: string | null;
  displayTitle: string | null;
  communityId: number | null;
  creatorId: number | null;
  myRole: GroupRole | null;
  members: ConversationMember[];
  createdAt: string;
  updatedAt: string;
}

/** Wire format on /topic/conversations/{id}, history pages and /sync. */
export interface ChatMessageDto {
  id: number;
  conversationId: number;
  senderId: number;
  senderName: string | null;
  content: string | null;
  idempotencyKey: string | null;
  createdAt: string;
  editedAt: string | null;
  deleted: boolean;
}

export interface MessagePage {
  conversationId: number;
  messages: ChatMessageDto[];
  nextCursor: number | null;
  hasMore: boolean;
}

export interface SyncResponse {
  conversationId: number;
  missedMessages: ChatMessageDto[];
  hasMore: boolean;
}

export interface ReadState {
  conversationId: number;
  lastReadMessageId: number;
  unreadCount: number;
}

export interface PresenceEvent {
  userId: number;
  status: PresenceStatus;
  timestamp: string;
}

export interface TypingEvent {
  conversationId: number;
  userId: number;
  timestamp: string;
}

/** Private rejection pushed on /user/queue/errors. */
export interface StompErrorEvent {
  code: 'BAD_REQUEST' | 'FORBIDDEN' | 'INTERNAL_ERROR';
  message: string;
  receiptId: string | null;
}
