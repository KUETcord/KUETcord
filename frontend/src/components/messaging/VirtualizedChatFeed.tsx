import { useCallback } from 'react';
import { Virtuoso } from 'react-virtuoso';
import { MessageSquareDashed } from 'lucide-react';
import { ChatMessageItem } from './ChatMessageItem';
import type { ChatMessage } from '../../lib/chat/messageStore';

/** Virtuoso index base; prepending older pages lowers firstItemIndex so the scroll position holds. */
export const FIRST_ITEM_INDEX_BASE = 1_000_000;
const GROUP_WINDOW_MS = 5 * 60_000;

interface Props {
  messages: ChatMessage[];
  currentUserId: number | null;
  prependedCount: number;
  hasMore: boolean;
  onLoadOlder: () => void;
  onRetry: (clientKey: string) => void;
  /**
   * Open scrolled to the newest message (chat default). Virtuoso keeps the list hidden until that
   * initial scroll completes, which never happens in jsdom, so tests render from the top instead.
   */
  startAtBottom?: boolean;
}

/**
 * Windowed chat feed: only the visible rows (+200 px overscan) are in the DOM, so 5,000+ messages
 * scroll at 60 fps. New messages auto-follow only when the reader is already at the bottom.
 */
export function VirtualizedChatFeed({
  messages,
  currentUserId,
  prependedCount,
  hasMore,
  onLoadOlder,
  onRetry,
  startAtBottom = true,
}: Props) {
  const itemContent = useCallback(
    (index: number, message: ChatMessage) => {
      const previous = messages[index - (FIRST_ITEM_INDEX_BASE - prependedCount) - 1];
      const grouped =
        previous != null &&
        previous.senderId === message.senderId &&
        Date.parse(message.createdAt) - Date.parse(previous.createdAt) < GROUP_WINDOW_MS;
      return (
        <ChatMessageItem
          message={message}
          isOwn={message.senderId === currentUserId}
          grouped={grouped}
          onRetry={onRetry}
        />
      );
    },
    [messages, currentUserId, prependedCount, onRetry],
  );

  if (messages.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 px-6 text-center">
        <MessageSquareDashed className="h-8 w-8 shrink-0 text-slate-600" aria-hidden="true" />
        <p className="text-sm font-medium text-slate-300">No messages yet</p>
        <p className="max-w-[65ch] text-xs text-slate-500">Say hello. Messages are saved and synced across your devices.</p>
      </div>
    );
  }

  return (
    <Virtuoso
      className="h-full"
      style={{ height: '100%' }}
      data={messages}
      firstItemIndex={FIRST_ITEM_INDEX_BASE - prependedCount}
      initialTopMostItemIndex={startAtBottom ? messages.length - 1 : 0}
      computeItemKey={(_, message) => message.clientKey}
      followOutput={(atBottom) => (atBottom ? 'smooth' : false)}
      startReached={hasMore ? onLoadOlder : undefined}
      increaseViewportBy={200}
      itemContent={itemContent}
      components={{ Footer: () => <div className="h-3" /> }}
    />
  );
}
