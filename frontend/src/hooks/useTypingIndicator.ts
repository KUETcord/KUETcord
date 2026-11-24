import { useCallback, useEffect, useRef, useState } from 'react';
import type { RealtimeClient } from '../lib/realtime/RealtimeClient';
import type { TypingEvent } from '../types/messaging';

/** Outbound keystroke pings are debounced to one per 3 s. */
export const TYPING_SEND_INTERVAL_MS = 3_000;
/** A typist disappears 4 s after their last ping. */
export const TYPING_CLEAR_AFTER_MS = 4_000;

/**
 * Ephemeral typing indicator. Nothing is persisted: pings go to /app/conversations/{id}/typing
 * and arrive on /topic/conversations/{id}/typing; the receiver clears each typist after 4 s.
 */
export function useTypingIndicator(conversationId: number | null, client: RealtimeClient | null, meId: number | null) {
  const [expiries, setExpiries] = useState<Record<number, number>>({});
  const lastSentAt = useRef(0);

  useEffect(() => {
    setExpiries({});
    if (!client || conversationId == null) return undefined;
    return client.subscribe<TypingEvent>(`/topic/conversations/${conversationId}/typing`, (event) => {
      if (event.userId === meId) return;
      setExpiries((current) => ({ ...current, [event.userId]: Date.now() + TYPING_CLEAR_AFTER_MS }));
    });
  }, [client, conversationId, meId]);

  useEffect(() => {
    const deadlines = Object.values(expiries);
    if (deadlines.length === 0) return undefined;
    const wait = Math.max(0, Math.min(...deadlines) - Date.now());
    const timer = window.setTimeout(() => {
      const now = Date.now();
      setExpiries((current) => Object.fromEntries(Object.entries(current).filter(([, until]) => until > now)));
    }, wait + 10);
    return () => window.clearTimeout(timer);
  }, [expiries]);

  const notifyTyping = useCallback(() => {
    if (!client || conversationId == null || client.status !== 'CONNECTED') return;
    const now = Date.now();
    if (now - lastSentAt.current < TYPING_SEND_INTERVAL_MS) return;
    lastSentAt.current = now;
    client.publish(`/app/conversations/${conversationId}/typing`, { conversationId }).catch(() => undefined);
  }, [client, conversationId]);

  /** Drop a typist immediately when their message lands. */
  const clearTypist = useCallback((userId: number) => {
    setExpiries((current) => {
      if (!(userId in current)) return current;
      const next = { ...current };
      delete next[userId];
      return next;
    });
  }, []);

  return { typingUserIds: Object.keys(expiries).map(Number), notifyTyping, clearTypist };
}
