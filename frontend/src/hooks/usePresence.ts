import { useEffect, useState } from 'react';
import { messagingApi } from '../lib/messagingApi';
import type { RealtimeClient } from '../lib/realtime/RealtimeClient';
import type { PresenceEvent, PresenceStatus } from '../types/messaging';

/**
 * Live presence for a set of users: REST snapshot first, then /topic/presence deltas.
 * Events older than what we already hold for a user are ignored (out-of-order safety).
 */
export function usePresence(client: RealtimeClient | null, userIds: number[]): Record<number, PresenceStatus> {
  const [events, setEvents] = useState<Record<number, PresenceEvent>>({});
  const key = [...new Set(userIds)].sort((a, b) => a - b).join(',');

  useEffect(() => {
    if (!client || key === '') return undefined;
    const ids = key.split(',').map(Number);
    let cancelled = false;
    const apply = (event: PresenceEvent) =>
      setEvents((current) => {
        const previous = current[event.userId];
        if (previous && Date.parse(previous.timestamp) > Date.parse(event.timestamp)) return current;
        return { ...current, [event.userId]: event };
      });

    messagingApi
      .presence(ids)
      .then((snapshot) => {
        if (!cancelled) snapshot.forEach(apply);
      })
      .catch(() => undefined);
    const off = client.subscribe<PresenceEvent>('/topic/presence', (event) => {
      if (ids.includes(event.userId)) apply(event);
    });
    return () => {
      cancelled = true;
      off();
    };
  }, [client, key]);

  return Object.fromEntries(Object.entries(events).map(([id, event]) => [id, event.status]));
}
