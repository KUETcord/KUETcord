import { Hash, MessageCircle, Users } from 'lucide-react';
import { cn, formatSmartTimestamp, pluralize } from '../../lib/format';
import type { ConversationSummary, PresenceStatus } from '../../types/messaging';
import { PresenceDot, UnreadBadge } from './StatusPills';

interface Props {
  conversations: ConversationSummary[];
  selectedId: number | null;
  presence: Record<number, PresenceStatus>;
  onSelect: (id: number) => void;
}

export function ConversationList({ conversations, selectedId, presence, onSelect }: Props) {
  if (conversations.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
        <MessageCircle className="h-7 w-7 shrink-0 text-slate-600" aria-hidden="true" />
        <p className="text-sm font-medium text-slate-300">No conversations yet</p>
        <p className="text-xs text-slate-500">Open a DM or create a group with at least 3 classmates.</p>
      </div>
    );
  }

  return (
    <ul className="space-y-0.5 p-2" aria-label="Conversations">
      {conversations.map((conversation) => {
        const title = conversation.displayTitle ?? conversation.title ?? 'Untitled conversation';
        const Icon = conversation.type === 'CHANNEL' ? Hash : conversation.type === 'DIRECT' ? MessageCircle : Users;
        const peerStatus = conversation.peerUserId != null ? presence[conversation.peerUserId] : undefined;
        const active = conversation.id === selectedId;
        return (
          <li key={conversation.id}>
            <button
              type="button"
              onClick={() => onSelect(conversation.id)}
              aria-current={active ? 'true' : undefined}
              className={cn(
                'flex w-full items-center gap-3 rounded-lg border px-2.5 py-2 text-left transition-colors duration-150 ease-out',
                active
                  ? 'border-emerald-500/20 bg-emerald-500/10'
                  : 'border-transparent hover:bg-slate-800/60',
              )}
            >
              <span className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-800 text-slate-300">
                <Icon className="h-4 w-4" aria-hidden="true" />
                {peerStatus && <PresenceDot status={peerStatus} className="absolute -bottom-0.5 -right-0.5" />}
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex min-w-0 items-center gap-2">
                  <span
                    className={cn('truncate text-sm', conversation.unreadCount > 0 ? 'font-semibold text-white' : 'text-slate-200')}
                    title={title}
                  >
                    {title}
                  </span>
                </span>
                <span className="flex items-center gap-1.5 text-[11px] text-slate-500">
                  <span className="truncate">
                    {conversation.type === 'DIRECT' ? 'Direct message' : pluralize(conversation.memberCount, 'member', 'members')}
                  </span>
                  <span aria-hidden="true">·</span>
                  <time className="shrink-0 tabular-nums" dateTime={conversation.updatedAt}>
                    {formatSmartTimestamp(conversation.updatedAt)}
                  </time>
                </span>
              </span>
              <UnreadBadge count={conversation.unreadCount} />
            </button>
          </li>
        );
      })}
    </ul>
  );
}
