import { CheckCircle2, Clock, RefreshCw, XCircle } from 'lucide-react';
import { cn } from '../../lib/format';
import type { CtStatus } from '../../types/domain';

const STATUS_STYLES: Record<
  CtStatus,
  { label: string; badgeClass: string; icon: typeof CheckCircle2 }
> = {
  VERIFIED: {
    label: 'Verified',
    badgeClass: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
    icon: CheckCircle2,
  },
  UNVERIFIED: {
    label: 'Unverified',
    badgeClass: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
    icon: Clock,
  },
  RESCHEDULED: {
    label: 'Rescheduled',
    badgeClass: 'bg-purple-500/15 text-purple-300 border-purple-500/30',
    icon: RefreshCw,
  },
  CANCELLED: {
    label: 'Cancelled',
    badgeClass: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
    icon: XCircle,
  },
};

export function CtStatusBadge({ status, className }: { status: CtStatus; className?: string }) {
  const cfg = STATUS_STYLES[status];
  const Icon = cfg.icon;

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium shrink-0',
        cfg.badgeClass,
        className,
      )}
    >
      <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>{cfg.label}</span>
    </span>
  );
}
