import type { ChatMessageDto, MessagePage } from '../../types/messaging';

/**
 * Client-side message list with optimistic delivery states (pure reducer, unit-tested).
 *
 *   SENDING  rendered immediately at 70 % opacity (no server id yet)
 *   SENT     RECEIPT arrived, or the broadcast copy with the same idempotencyKey replaced it
 *   FAILED   rejected on /user/queue/errors, receipt timeout, or offline; offers Retry (same key)
 *
 * Every message carries a stable `clientKey` (its idempotencyKey, or `srv-<id>` for messages
 * from others) so live broadcasts, history pages and /sync results can overlap freely without
 * ever producing a duplicate bubble.
 */
export type DeliveryState = 'SENDING' | 'SENT' | 'FAILED';

export interface ChatMessage {
  clientKey: string;
  id: number | null;
  conversationId: number;
  senderId: number;
  senderName: string | null;
  content: string | null;
  createdAt: string;
  deleted: boolean;
  delivery: DeliveryState;
  idempotencyKey: string | null;
}

export interface ChatState {
  messages: ChatMessage[];
  hasMore: boolean;
  nextCursor: number | null;
  /** Number of older messages prepended so far; drives react-virtuoso's firstItemIndex. */
  prependedCount: number;
  loaded: boolean;
}

export const EMPTY_CHAT: ChatState = { messages: [], hasMore: false, nextCursor: null, prependedCount: 0, loaded: false };

export type ChatAction =
  | { type: 'RESET' }
  | { type: 'HISTORY_LOADED'; page: MessagePage; older: boolean }
  | { type: 'SERVER_MESSAGES'; messages: ChatMessageDto[] }
  | { type: 'OPTIMISTIC_ADDED'; message: ChatMessage }
  | { type: 'RECEIPT_CONFIRMED'; idempotencyKey: string }
  | { type: 'SEND_FAILED'; idempotencyKey: string }
  | { type: 'RETRYING'; idempotencyKey: string };

export function fromServer(dto: ChatMessageDto): ChatMessage {
  return {
    clientKey: dto.idempotencyKey ?? `srv-${dto.id}`,
    id: dto.id,
    conversationId: dto.conversationId,
    senderId: dto.senderId,
    senderName: dto.senderName,
    content: dto.content,
    createdAt: dto.createdAt,
    deleted: dto.deleted,
    delivery: 'SENT',
    idempotencyKey: dto.idempotencyKey,
  };
}

export function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'RESET':
      return EMPTY_CHAT;

    case 'HISTORY_LOADED': {
      const merged = upsertAll(state.messages, action.page.messages);
      const added = merged.length - state.messages.length;
      return {
        messages: merged,
        hasMore: action.page.hasMore,
        nextCursor: action.page.nextCursor,
        prependedCount: action.older ? state.prependedCount + added : state.prependedCount,
        loaded: true,
      };
    }

    case 'SERVER_MESSAGES':
      return { ...state, messages: upsertAll(state.messages, action.messages) };

    case 'OPTIMISTIC_ADDED':
      return { ...state, messages: [...state.messages, action.message] };

    case 'RECEIPT_CONFIRMED':
      return withDelivery(state, action.idempotencyKey, 'SENT', ['SENDING', 'FAILED']);

    case 'SEND_FAILED':
      return withDelivery(state, action.idempotencyKey, 'FAILED', ['SENDING']);

    case 'RETRYING':
      return withDelivery(state, action.idempotencyKey, 'SENDING', ['FAILED']);

    default:
      return state;
  }
}

/** Highest server id the client holds: the /sync cursor after a reconnect. */
export function lastConfirmedId(state: ChatState): number {
  return state.messages.reduce((max, m) => (m.id != null && m.id > max ? m.id : max), 0);
}

export function pendingMessages(state: ChatState): ChatMessage[] {
  return state.messages.filter((m) => m.id == null && m.delivery === 'SENDING');
}

function upsertAll(current: ChatMessage[], incoming: ChatMessageDto[]): ChatMessage[] {
  if (incoming.length === 0) return current;
  const byKey = new Map(current.map((m) => [m.clientKey, m]));
  const knownIds = new Set(current.filter((m) => m.id != null).map((m) => m.id));
  for (const dto of incoming) {
    if (knownIds.has(dto.id)) continue;
    const next = fromServer(dto);
    byKey.set(next.clientKey, next); // replaces the optimistic copy with the same idempotencyKey
    knownIds.add(dto.id);
  }
  return order([...byKey.values()]);
}

/** Confirmed messages by server id, then still-pending ones in the order they were typed. */
function order(messages: ChatMessage[]): ChatMessage[] {
  const confirmed = messages.filter((m) => m.id != null).sort((a, b) => (a.id as number) - (b.id as number));
  const pending = messages.filter((m) => m.id == null);
  return [...confirmed, ...pending];
}

function withDelivery(state: ChatState, key: string, delivery: DeliveryState, from: DeliveryState[]): ChatState {
  let changed = false;
  const messages = state.messages.map((m) => {
    if (m.clientKey === key && from.includes(m.delivery)) {
      changed = true;
      return { ...m, delivery };
    }
    return m;
  });
  return changed ? { ...state, messages } : state;
}
