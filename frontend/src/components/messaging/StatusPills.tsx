import { cn } from '../../lib/format';
import type { ConnectionStatus } from '../../lib/realtime/connectionMachine';
import type { PresenceStatus } from '../../types/messaging';

const CONNECTION_COPY: Record<ConnectionStatus | 'DEMO', { label: string; dot: string; tone: string }> = {
  CONNECTED: { label: 'Live', dot: 'bg-emerald-400', tone: 'border-emerald-500/20 bg-emerald-500/10 text-emerald-300' },
  CONNECTING: { label: 'Connecting…', dot: 'bg-amber-400', tone: 'border-amber-500/20 bg-amber-500/10 text-amber-300' },
  RECONNECTING: { label: 'Reconnecting…', dot: 'bg-amber-400', tone: 'border-amber-500/20 bg-amber-500/10 text-amber-300' },
  DISCONNECTED: { label: 'Offline', dot: 'bg-rose-400', tone: 'border-rose-500/20 bg-rose-500/10 text-rose-300' },
  DEMO: { label: 'Demo preview', dot: 'bg-sky-400', tone: 'border-sky-500/20 bg-sky-500/10 text-sky-300' },
};

export function ConnectionStatusPill({ status }: { status: ConnectionStatus | 'DEMO' }) {
  const copy = CONNECTION_COPY[status];
  return (
    <span
      className={cn('inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium', copy.tone)}
      role="status"
      aria-live="polite"
    >
      <span
        className={cn('h-1.5 w-1.5 shrink-0 rounded-full', copy.dot, status === 'RECONNECTING' && 'motion-safe:animate-pulse')}
        aria-hidden="true"
      />
      {copy.label}
    </span>
  );
}

const PRESENCE_DOT: Record<PresenceStatus, string> = {
  ONLINE: 'bg-emerald-400',
  IDLE: 'bg-amber-400',
  OFFLINE: 'bg-slate-600',
};

export function PresenceDot({ status, className }: { status: PresenceStatus; className?: string }) {
  return (
    <span
      className={cn('inline-block h-2.5 w-2.5 shrink-0 rounded-full ring-2 ring-slate-900', PRESENCE_DOT[status], className)}
      title={status.charAt(0) + status.slice(1).toLowerCase()}
      aria-label={`Presence: ${status.toLowerCase()}`}
    />
  );
}

export function UnreadBadge({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span
      className="inline-flex min-w-[1.25rem] shrink-0 items-center justify-center rounded-full bg-emerald-500 px-1.5 py-0.5 text-[10px] font-semibold text-slate-950 tabular-nums"
      aria-label={`${count} unread`}
    >
      {count > 99 ? '99+' : count}
    </span>
  );
}

export function TypingIndicator({ names }: { names: string[] }) {
  if (names.length === 0) return <div className="h-5" aria-hidden="true" />;
  const label =
    names.length === 1
      ? `${names[0]} is typing…`
      : names.length === 2
        ? `${names[0]} and ${names[1]} are typing…`
        : `${names.length} people are typing…`;
  return (
    <p className="h-5 truncate px-4 text-[11px] text-slate-400" aria-live="polite" title={label}>
      {label}
    </p>
  );
}
