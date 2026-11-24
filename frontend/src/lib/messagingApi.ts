import { request } from './api';
import type {
  ConversationDetail,
  ConversationSummary,
  MessagePage,
  PresenceEvent,
  ReadState,
  SyncResponse,
} from '../types/messaging';

/** REST side of Member B's messaging module. Sending itself happens over STOMP. */
export const messagingApi = {
  listConversations(): Promise<ConversationSummary[]> {
    return request<ConversationSummary[]>('/conversations');
  },

  getConversation(id: number): Promise<ConversationDetail> {
    return request<ConversationDetail>(`/conversations/${id}`);
  },

  history(id: number, cursor?: number | null, limit = 30): Promise<MessagePage> {
    const qs = new URLSearchParams({ limit: String(limit) });
    if (cursor != null) qs.set('cursor', String(cursor));
    return request<MessagePage>(`/conversations/${id}/messages?${qs.toString()}`);
  },

  sync(id: number, afterMessageId: number, limit = 50): Promise<SyncResponse> {
    const qs = new URLSearchParams({ afterMessageId: String(afterMessageId), limit: String(limit) });
    return request<SyncResponse>(`/conversations/${id}/sync?${qs.toString()}`);
  },

  markRead(id: number, lastReadMessageId: number): Promise<ReadState> {
    return request<ReadState>(`/conversations/${id}/read-state`, {
      method: 'POST',
      body: JSON.stringify({ lastReadMessageId }),
    });
  },

  presence(userIds: number[]): Promise<PresenceEvent[]> {
    if (userIds.length === 0) return Promise.resolve([]);
    return request<PresenceEvent[]>(`/users/presence?ids=${userIds.join(',')}`);
  },
};
