import { memo } from 'react';
import { AlertCircle, Check, Loader2, RotateCw } from 'lucide-react';
import { cn, formatExactDateTime, getInitials } from '../../lib/format';
import type { ChatMessage } from '../../lib/chat/messageStore';

const timeFormat = new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' });

interface Props {
  message: ChatMessage;
  isOwn: boolean;
  /** Collapse avatar/name when the previous message is from the same sender. */
  grouped: boolean;
  onRetry: (clientKey: string) => void;
}

export const ChatMessageItem = memo(function ChatMessageItem({ message, isOwn, grouped, onRetry }: Props) {
  const name = message.senderName ?? 'Unknown student';
  const time = timeFormat.format(new Date(message.createdAt));

  return (
    <div
      className={cn('flex gap-3 px-4', grouped ? 'pt-0.5' : 'pt-3', isOwn && 'flex-row-reverse')}
      data-testid="chat-message"
      data-delivery={message.delivery}
    >
      {grouped ? (
        <div className="w-8 shrink-0" aria-hidden="true" />
      ) : (
        <div
          className={cn(
            'flex h-8 w-8 shrink-0 items-center justify-center rounded-full border text-[11px] font-semibold',
            isOwn
              ? 'border-emerald-500/30 bg-emerald-600/25 text-emerald-300'
              : 'border-slate-700 bg-slate-800 text-slate-300',
          )}
          aria-hidden="true"
        >
          {getInitials(name)}
        </div>
      )}

      <div className={cn('flex min-w-0 max-w-[75%] flex-col', isOwn ? 'items-end' : 'items-start')}>
        {!grouped && (
          <div className={cn('mb-1 flex min-w-0 max-w-full items-baseline gap-2', isOwn && 'flex-row-reverse')}>
            <span className="truncate text-xs font-semibold text-slate-200" title={name}>
              {isOwn ? 'You' : name}
            </span>
            <time
              className="shrink-0 text-[11px] text-slate-500 tabular-nums"
              dateTime={message.createdAt}
              title={formatExactDateTime(message.createdAt)}
            >
              {time}
            </time>
          </div>
        )}

        <div
          className={cn(
            'max-w-full whitespace-pre-wrap rounded-2xl px-3.5 py-2 text-sm leading-relaxed [overflow-wrap:anywhere]',
            'transition-opacity duration-150 ease-out',
            isOwn ? 'rounded-tr-md bg-emerald-600/90 text-white' : 'rounded-tl-md bg-slate-800 text-slate-100',
            message.delivery === 'SENDING' && 'opacity-70 motion-safe:animate-pulse',
            message.delivery === 'FAILED' && 'bg-rose-950/60 text-rose-100 ring-1 ring-rose-500/40',
            message.deleted && 'italic text-slate-400',
          )}
        >
          {message.deleted ? 'This message was deleted' : message.content}
        </div>

        {isOwn && <DeliveryStatus message={message} onRetry={onRetry} />}
      </div>
    </div>
  );
});

function DeliveryStatus({ message, onRetry }: { message: ChatMessage; onRetry: (key: string) => void }) {
  if (message.delivery === 'SENDING') {
    return (
      <span className="mt-1 inline-flex items-center gap-1 text-[11px] text-slate-500">
        <Loader2 className="h-3 w-3 shrink-0 motion-safe:animate-spin" aria-hidden="true" />
        Sending…
      </span>
    );
  }
  if (message.delivery === 'FAILED') {
    return (
      <span className="mt-1 inline-flex items-center gap-1.5 text-[11px] text-rose-300" role="alert">
        <AlertCircle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        Not delivered
        <button
          type="button"
          onClick={() => onRetry(message.clientKey)}
          className="inline-flex items-center gap-1 rounded-md bg-rose-500/15 px-1.5 py-0.5 font-medium text-rose-200 hover:bg-rose-500/25"
        >
          <RotateCw className="h-3 w-3 shrink-0" aria-hidden="true" />
          Retry
        </button>
      </span>
    );
  }
  return (
    <span className="mt-1 inline-flex items-center text-emerald-400" aria-label="Sent">
      <Check className="h-3 w-3 shrink-0" aria-hidden="true" />
    </span>
  );
}
