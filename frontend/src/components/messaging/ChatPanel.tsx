import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { messagingApi } from '../../lib/messagingApi';
import { pluralize } from '../../lib/format';
import { lastConfirmedId } from '../../lib/chat/messageStore';
import type { RealtimeClient } from '../../lib/realtime/RealtimeClient';
import type { ConnectionStatus } from '../../lib/realtime/connectionMachine';
import { useConversationMessages } from '../../hooks/useConversationMessages';
import { useTypingIndicator } from '../../hooks/useTypingIndicator';
import type { ChatMessageDto, ConversationMember, ConversationSummary, PresenceStatus } from '../../types/messaging';
import { MessageComposer } from './MessageComposer';
import { ConnectionStatusPill, PresenceDot, TypingIndicator } from './StatusPills';
import { VirtualizedChatFeed } from './VirtualizedChatFeed';

const READ_MARK_DELAY_MS = 800;

interface Props {
  conversation: ConversationSummary;
  client: RealtimeClient | null;
  status: ConnectionStatus | 'DEMO';
  me: { id: number; displayName: string };
  demoMembers?: ConversationMember[];
  demoMessages?: ChatMessageDto[];
  peerStatus?: PresenceStatus;
  onRead: (conversationId: number) => void;
  onBack: () => void;
}

export function ChatPanel({ conversation, client, status, me, demoMembers, demoMessages, peerStatus, onRead, onBack }: Props) {
  const { state, send, retry, loadOlder } = useConversationMessages({
    conversationId: conversation.id,
    client,
    demoMessages,
    me,
  });
  const { typingUserIds, notifyTyping, clearTypist } = useTypingIndicator(conversation.id, client, me.id);
  const [members, setMembers] = useState<ConversationMember[]>(demoMembers ?? []);

  useEffect(() => {
    if (!client) {
      setMembers(demoMembers ?? []);
      return undefined;
    }
    let cancelled = false;
    messagingApi
      .getConversation(conversation.id)
      .then((detail) => {
        if (!cancelled) setMembers(detail.members);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [client, conversation.id, demoMembers]);

  // A typist's indicator disappears as soon as their message lands.
  const lastMessage = state.messages[state.messages.length - 1];
  useEffect(() => {
    if (lastMessage && lastMessage.senderId !== me.id) clearTypist(lastMessage.senderId);
  }, [lastMessage, me.id, clearTypist]);

  // Mark read (debounced) whenever newer confirmed messages are on screen.
  const newestId = lastConfirmedId(state);
  const lastMarked = useRef(0);
  useEffect(() => {
    if (newestId === 0 || newestId <= lastMarked.current) return undefined;
    const timer = window.setTimeout(() => {
      lastMarked.current = newestId;
      onRead(conversation.id);
      if (client && document.visibilityState !== 'hidden') {
        messagingApi.markRead(conversation.id, newestId).catch(() => undefined);
      }
    }, READ_MARK_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [client, conversation.id, newestId, onRead]);

  const typingNames = useMemo(() => {
    const byId = new Map(members.map((m) => [m.userId, m.displayName ?? m.username ?? 'Someone']));
    return typingUserIds.map((id) => byId.get(id) ?? 'Someone');
  }, [members, typingUserIds]);

  const title = conversation.displayTitle ?? conversation.title ?? 'Conversation';
  const subtitle =
    conversation.type === 'DIRECT'
      ? 'Direct message'
      : pluralize(members.length || conversation.memberCount, 'member', 'members');
  const offline = status === 'DISCONNECTED';

  return (
    <section className="flex h-full min-w-0 flex-col" aria-label={`Conversation: ${title}`}>
      <header className="flex items-center gap-3 border-b border-slate-800 px-4 py-3">
        <button
          type="button"
          onClick={onBack}
          aria-label="Back to conversations"
          className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white md:hidden shrink-0"
        >
          <ArrowLeft className="h-4 w-4" />
        </button>
        <div className="min-w-0 flex-1">
          <h2 className="flex min-w-0 items-center gap-2 text-base font-semibold text-white">
            <span className="truncate" title={title}>
              {title}
            </span>
            {peerStatus && <PresenceDot status={peerStatus} />}
          </h2>
          <p className="truncate text-xs text-slate-400 tabular-nums">{subtitle}</p>
        </div>
        <ConnectionStatusPill status={status} />
      </header>

      <div className="min-h-0 flex-1">
        {state.loaded ? (
          <VirtualizedChatFeed
            messages={state.messages}
            currentUserId={me.id}
            prependedCount={state.prependedCount}
            hasMore={state.hasMore}
            onLoadOlder={loadOlder}
            onRetry={retry}
          />
        ) : (
          <div className="space-y-3 p-4" aria-busy="true" aria-label="Loading messages">
            {[0, 1, 2].map((i) => (
              <div key={i} className="h-10 w-2/3 rounded-2xl bg-slate-800/60 motion-safe:animate-pulse" />
            ))}
          </div>
        )}
      </div>

      <TypingIndicator names={typingNames} />
      <MessageComposer
        disabled={offline}
        placeholder={offline ? 'Offline: reconnect to send messages' : `Message ${title}`}
        onSend={send}
        onTyping={notifyTyping}
      />
    </section>
  );
}
