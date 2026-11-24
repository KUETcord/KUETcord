import React, { createContext, useContext, useEffect, useState } from 'react';
import { toast } from 'sonner';
import { getAccessToken } from '../lib/api';
import { RealtimeClient, defaultWebSocketUrl } from '../lib/realtime/RealtimeClient';
import type { ConnectionStatus } from '../lib/realtime/connectionMachine';
import { useAuth } from './AuthContext';

interface RealtimeContextValue {
  /** Null in demo mode or when signed out: components fall back to local fixtures. */
  client: RealtimeClient | null;
  status: ConnectionStatus;
}

const DISABLED: RealtimeContextValue = { client: null, status: 'DISCONNECTED' };

const RealtimeContext = createContext<RealtimeContextValue>(DISABLED);

/**
 * Owns the single STOMP connection for the signed-in user (one socket per tab).
 * Also reports tab visibility as presence: a hidden tab is IDLE, a visible one ONLINE.
 */
export function RealtimeProvider({ children }: { children: React.ReactNode }) {
  const { user, isDemoMode } = useAuth();
  const [value, setValue] = useState<RealtimeContextValue>(DISABLED);
  const userId = user?.id ?? null;

  useEffect(() => {
    if (isDemoMode || userId == null || !getAccessToken()) {
      setValue(DISABLED);
      return undefined;
    }

    const client = new RealtimeClient({ url: defaultWebSocketUrl(), getToken: getAccessToken });
    const offStatus = client.onStatus((status) => setValue({ client, status }));
    const offFatal = client.onFatal(() => toast.error('Session expired. Please sign in again to reconnect chat.'));
    const offConnect = client.onStatus((status) => {
      if (status === 'CONNECTED') reportVisibility(client);
    });

    const onVisibility = () => reportVisibility(client);
    document.addEventListener('visibilitychange', onVisibility);

    setValue({ client, status: client.status });
    client.start();

    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      offStatus();
      offFatal();
      offConnect();
      client.stop();
    };
  }, [isDemoMode, userId]);

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

function reportVisibility(client: RealtimeClient) {
  if (client.status !== 'CONNECTED') return;
  const status = document.visibilityState === 'hidden' ? 'IDLE' : 'ONLINE';
  client.publish('/app/presence', { status }).catch(() => undefined);
}

export function useRealtime(): RealtimeContextValue {
  return useContext(RealtimeContext);
}
