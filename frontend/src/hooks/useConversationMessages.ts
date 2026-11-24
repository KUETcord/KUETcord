import { useCallback, useEffect, useReducer, useRef } from 'react';
import { toast } from 'sonner';
import { messagingApi } from '../lib/messagingApi';
import { chatReducer, EMPTY_CHAT, lastConfirmedId, pendingMessages, type ChatState } from '../lib/chat/messageStore';
import type { RealtimeClient } from '../lib/realtime/RealtimeClient';
import type { ChatMessageDto, StompErrorEvent } from '../types/messaging';

const MAX_SYNC_ROUNDS = 20;

interface Options {
  conversationId: number | null;
  /** Live client, or null for demo/offline preview (local echo). */
  client: RealtimeClient | null;
  /** Demo-mode seed messages; must be referentially stable. */
  demoMessages?: ChatMessageDto[];
  me: { id: number; displayName: string } | null;
}

export interface ConversationMessages {
  state: ChatState;
  send: (content: string) => void;
  retry: (clientKey: string) => void;
  loadOlder: () => void;
}

/**
 * One conversation's message list: initial history, live topic, private error queue,
 * optimistic send with RECEIPT confirmation, and gap catch-up via /sync after a reconnect
 * (the list and scroll position are kept; only the missed messages are appended).
 */
export function useConversationMessages({ conversationId, client, demoMessages, me }: Options): ConversationMessages {
  const [state, dispatch] = useReducer(chatReducer, EMPTY_CHAT);
  const stateRef = useRef(state);
  stateRef.current = state;
  const receiptToKey = useRef(new Map<string, string>());
  const loadingOlder = useRef(false);

  const deliver = useCallback(
    (key: string, content: string) => {
      if (conversationId == null) return;
      if (!client) {
        // Demo preview: simulate commit + broadcast so the SENDING -> SENT transition is visible.
        window.setTimeout(() => {
          const id = lastConfirmedId(stateRef.current) + 1;
          dispatch({
            type: 'SERVER_MESSAGES',
            messages: [{
              id, conversationId, senderId: me?.id ?? 0, senderName: me?.displayName ?? null, content,
              idempotencyKey: key, createdAt: new Date().toISOString(), editedAt: null, deleted: false,
            }],
          });
        }, 350);
        return;
      }
      if (client.status !== 'CONNECTED') {
        // Keep SENDING while reconnecting: resent with the same key once the socket is back.
        if (client.status === 'DISCONNECTED') dispatch({ type: 'SEND_FAILED', idempotencyKey: key });
        return;
      }
      const receipt = `msg-${key}`;
      receiptToKey.current.set(receipt, key);
      client
        .publish(`/app/conversations/${conversationId}/messages`, { content, idempotencyKey: key }, receipt)
        .then(() => dispatch({ type: 'RECEIPT_CONFIRMED', idempotencyKey: key }))
        .catch(() => dispatch({ type: 'SEND_FAILED', idempotencyKey: key }))
        .finally(() => receiptToKey.current.delete(receipt));
    },
    [client, conversationId, me],
  );

  const catchUp = useCallback(async () => {
    if (!client || conversationId == null) return;
    try {
      let after = lastConfirmedId(stateRef.current);
      if (after === 0) {
        dispatch({ type: 'HISTORY_LOADED', page: await messagingApi.history(conversationId), older: false });
        return;
      }
      for (let round = 0; round < MAX_SYNC_ROUNDS; round++) {
        const page = await messagingApi.sync(conversationId, after);
        dispatch({ type: 'SERVER_MESSAGES', messages: page.missedMessages });
        if (!page.hasMore || page.missedMessages.length === 0) break;
        after = page.missedMessages[page.missedMessages.length - 1].id;
      }
    } catch {
      toast.error('Could not fetch missed messages. They will appear after a refresh.');
    }
  }, [client, conversationId]);

  useEffect(() => {
    dispatch({ type: 'RESET' });
    if (conversationId == null) return undefined;

    if (!client) {
      dispatch({
        type: 'HISTORY_LOADED',
        page: { conversationId, messages: demoMessages ?? [], nextCursor: null, hasMore: false },
        older: false,
      });
      return undefined;
    }

    let cancelled = false;
    messagingApi
      .history(conversationId)
      .then((page) => {
        if (!cancelled) dispatch({ type: 'HISTORY_LOADED', page, older: false });
      })
      .catch(() => toast.error('Failed to load message history'));

    const offTopic = client.subscribe<ChatMessageDto>(`/topic/conversations/${conversationId}`, (message) =>
      dispatch({ type: 'SERVER_MESSAGES', messages: [message] }),
    );
    const offErrors = client.subscribe<StompErrorEvent>('/user/queue/errors', (error) => {
      const key = error.receiptId ? receiptToKey.current.get(error.receiptId) : undefined;
      if (key) {
        dispatch({ type: 'SEND_FAILED', idempotencyKey: key });
        toast.error(error.message);
      }
    });
    const offReconnect = client.onReconnect(() => {
      void catchUp();
      pendingMessages(stateRef.current).forEach((m) => deliver(m.clientKey, m.content ?? ''));
    });

    return () => {
      cancelled = true;
      offTopic();
      offErrors();
      offReconnect();
    };
  }, [conversationId, client, demoMessages, catchUp, deliver]);

  const send = useCallback(
    (content: string) => {
      if (conversationId == null || !me) return;
      const key = newIdempotencyKey();
      dispatch({
        type: 'OPTIMISTIC_ADDED',
        message: {
          clientKey: key, id: null, conversationId, senderId: me.id, senderName: me.displayName, content,
          createdAt: new Date().toISOString(), deleted: false, delivery: 'SENDING', idempotencyKey: key,
        },
      });
      deliver(key, content);
    },
    [conversationId, deliver, me],
  );

  const retry = useCallback(
    (clientKey: string) => {
      const message = stateRef.current.messages.find((m) => m.clientKey === clientKey);
      if (!message || message.delivery !== 'FAILED') return;
      dispatch({ type: 'RETRYING', idempotencyKey: clientKey });
      deliver(clientKey, message.content ?? ''); // same key: the server deduplicates if it was saved
    },
    [deliver],
  );

  const loadOlder = useCallback(() => {
    const current = stateRef.current;
    if (!client || conversationId == null || !current.hasMore || loadingOlder.current) return;
    loadingOlder.current = true;
    messagingApi
      .history(conversationId, current.nextCursor)
      .then((page) => dispatch({ type: 'HISTORY_LOADED', page, older: true }))
      .catch(() => toast.error('Failed to load older messages'))
      .finally(() => {
        loadingOlder.current = false;
      });
  }, [client, conversationId]);

  return { state, send, retry, loadOlder };
}

export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `k-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
