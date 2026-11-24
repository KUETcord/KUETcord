import { describe, expect, it } from 'vitest';
import type { ChatMessageDto } from '../../types/messaging';
import { EMPTY_CHAT, chatReducer, lastConfirmedId, pendingMessages, type ChatMessage, type ChatState } from './messageStore';

const dto = (id: number, key: string | null = `k-${id}`, senderId = 2): ChatMessageDto => ({
  id,
  conversationId: 1,
  senderId,
  senderName: 'Sadia Islam',
  content: `message ${id}`,
  idempotencyKey: key,
  createdAt: new Date(1_700_000_000_000 + id * 1000).toISOString(),
  editedAt: null,
  deleted: false,
});

const optimistic = (key: string): ChatMessage => ({
  clientKey: key,
  id: null,
  conversationId: 1,
  senderId: 1,
  senderName: 'Me',
  content: 'hello',
  createdAt: new Date().toISOString(),
  deleted: false,
  delivery: 'SENDING',
  idempotencyKey: key,
});

function loaded(ids: number[], hasMore = false): ChatState {
  return chatReducer(EMPTY_CHAT, {
    type: 'HISTORY_LOADED',
    page: { conversationId: 1, messages: ids.map((id) => dto(id)), nextCursor: hasMore ? ids[0] : null, hasMore },
    older: false,
  });
}

describe('chat message store', () => {
  it('reconciles an optimistic SENDING bubble with its broadcast copy by idempotencyKey', () => {
    let state = loaded([1, 2]);
    state = chatReducer(state, { type: 'OPTIMISTIC_ADDED', message: optimistic('my-key') });
    expect(pendingMessages(state)).toHaveLength(1);

    state = chatReducer(state, { type: 'RECEIPT_CONFIRMED', idempotencyKey: 'my-key' });
    expect(state.messages.at(-1)?.delivery).toBe('SENT');

    state = chatReducer(state, { type: 'SERVER_MESSAGES', messages: [dto(3, 'my-key', 1)] });
    expect(state.messages.map((m) => m.id)).toEqual([1, 2, 3]);
    expect(state.messages).toHaveLength(3);
    expect(lastConfirmedId(state)).toBe(3);
  });

  it('never duplicates when live broadcast and /sync overlap after a reconnect', () => {
    let state = loaded([1, 2, 3]);
    state = chatReducer(state, { type: 'SERVER_MESSAGES', messages: [dto(5)] }); // live, arrived first
    state = chatReducer(state, { type: 'SERVER_MESSAGES', messages: [dto(4), dto(5), dto(6)] }); // /sync
    expect(state.messages.map((m) => m.id)).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it('marks FAILED, retries back to SENDING, and a late broadcast still wins', () => {
    let state = chatReducer(loaded([1]), { type: 'OPTIMISTIC_ADDED', message: optimistic('k') });
    state = chatReducer(state, { type: 'SEND_FAILED', idempotencyKey: 'k' });
    expect(state.messages.at(-1)?.delivery).toBe('FAILED');

    state = chatReducer(state, { type: 'RETRYING', idempotencyKey: 'k' });
    expect(state.messages.at(-1)?.delivery).toBe('SENDING');

    state = chatReducer(state, { type: 'SEND_FAILED', idempotencyKey: 'k' });
    state = chatReducer(state, { type: 'SERVER_MESSAGES', messages: [dto(2, 'k', 1)] });
    expect(state.messages.at(-1)).toMatchObject({ id: 2, delivery: 'SENT' });
  });

  it('prepends older history pages in order and tracks prependedCount for virtualization', () => {
    let state = loaded([31, 32, 33], true);
    state = chatReducer(state, {
      type: 'HISTORY_LOADED',
      page: { conversationId: 1, messages: [dto(29), dto(30)], nextCursor: null, hasMore: false },
      older: true,
    });
    expect(state.messages.map((m) => m.id)).toEqual([29, 30, 31, 32, 33]);
    expect(state.prependedCount).toBe(2);
    expect(state.hasMore).toBe(false);
  });

  it('keeps messages without an idempotency key distinct by server id', () => {
    const state = chatReducer(EMPTY_CHAT, { type: 'SERVER_MESSAGES', messages: [dto(1, null), dto(2, null)] });
    expect(state.messages.map((m) => m.clientKey)).toEqual(['srv-1', 'srv-2']);
  });
});
