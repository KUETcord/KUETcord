import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { toast } from 'sonner';
import { LogIn } from 'lucide-react';
import { ChatPanel } from '../components/messaging/ChatPanel';
import { ConversationList } from '../components/messaging/ConversationList';
import { useAuth } from '../context/AuthContext';
import { useRealtime } from '../context/RealtimeContext';
import { buildChatFixture, parseChatFixtureMode } from '../fixtures/messagingFixtures';
import { usePresence } from '../hooks/usePresence';
import { cn, pluralize } from '../lib/format';
import { messagingApi } from '../lib/messagingApi';
import type { ConversationSummary } from '../types/messaging';

/**
 * Core Messaging (Member B). Live mode uses REST + STOMP; demo mode (no token) runs entirely on
 * fixtures selected with ?data=demo|worst|empty|one|huge so the UI can be reviewed without a backend.
 */
export function MessagingPage() {
  const { user } = useAuth();
  const { client, status } = useRealtime();
  const location = useLocation();
  const fixtureMode = parseChatFixtureMode(location.search);
  const live = client != null;

  const fixture = useMemo(
    () => (user && !live ? buildChatFixture(fixtureMode, { id: user.id, displayName: user.displayName }) : null),
    [fixtureMode, live, user],
  );

  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const refresh = useCallback(() => {
    if (!live) return;
    messagingApi
      .listConversations()
      .then(setConversations)
      .catch(() => toast.error('Failed to load conversations'));
  }, [live]);

  useEffect(() => {
    if (fixture) {
      setConversations(fixture.conversations);
      setSelectedId(fixture.conversations[0]?.id ?? null);
      return undefined;
    }
    refresh();
    return client ? client.onReconnect(refresh) : undefined;
  }, [client, fixture, refresh]);

  useEffect(() => {
    if (selectedId == null && conversations.length > 0 && window.matchMedia?.('(min-width: 768px)').matches !== false) {
      setSelectedId(conversations[0].id);
    }
  }, [conversations, selectedId]);

  const peerIds = useMemo(
    () => conversations.map((c) => c.peerUserId).filter((id): id is number => id != null),
    [conversations],
  );
  const presence = usePresence(client, peerIds);

  const markLocallyRead = useCallback((conversationId: number) => {
    setConversations((current) =>
      current.map((c) => (c.id === conversationId && c.unreadCount > 0 ? { ...c, unreadCount: 0 } : c)),
    );
  }, []);

  if (!user) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center gap-3 rounded-xl border border-slate-800 bg-slate-900/50 p-8 text-center">
        <h1 className="text-2xl font-bold text-white">Core Messaging</h1>
        <p className="text-sm text-slate-400">Sign in with your KUET email to chat with classmates.</p>
        <Link
          to="/auth"
          className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500"
        >
          <LogIn className="h-4 w-4 shrink-0" />
          Sign in
        </Link>
      </div>
    );
  }

  const selected = conversations.find((c) => c.id === selectedId) ?? null;
  const totalUnread = conversations.reduce((sum, c) => sum + c.unreadCount, 0);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div className="min-w-0">
          <h1 className="text-2xl font-bold text-white">Core Messaging</h1>
          <p className="text-sm text-slate-400 tabular-nums">
            {pluralize(conversations.length, 'conversation', 'conversations')}
            {totalUnread > 0 && ` · ${pluralize(totalUnread, 'unread message', 'unread messages')}`}
          </p>
        </div>
      </div>

      <div className="grid h-[calc(100vh-14rem)] min-h-[30rem] overflow-hidden rounded-xl border border-slate-800 bg-slate-900/40 md:grid-cols-[18rem_minmax(0,1fr)]">
        <aside
          className={cn('min-h-0 overflow-y-auto border-slate-800 md:border-r', selected ? 'hidden md:block' : 'block')}
        >
          <ConversationList
            conversations={conversations}
            selectedId={selectedId}
            presence={presence}
            onSelect={setSelectedId}
          />
        </aside>

        <div className={cn('min-h-0 min-w-0', selected ? 'block' : 'hidden md:block')}>
          {selected ? (
            <ChatPanel
              key={selected.id}
              conversation={selected}
              client={client}
              status={live ? status : 'DEMO'}
              me={{ id: user.id, displayName: user.displayName }}
              demoMembers={fixture?.members[selected.id]}
              demoMessages={fixture?.messages[selected.id]}
              peerStatus={selected.peerUserId != null ? presence[selected.peerUserId] : undefined}
              onRead={markLocallyRead}
              onBack={() => setSelectedId(null)}
            />
          ) : (
            <div className="flex h-full items-center justify-center p-6 text-sm text-slate-500">
              Select a conversation to start chatting.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
