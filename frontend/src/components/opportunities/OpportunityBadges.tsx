import { BadgeCheck, Clock } from 'lucide-react';
import { CATEGORY_LABELS, DEADLINE_TONE_STYLES, deadlineBadge } from '../../lib/collab/kanban';
import { cn } from '../../lib/format';
import type { OpportunityCategory, VerificationStatus } from '../../types/collab';

export function DeadlinePill({ deadline, now }: { deadline: string; now?: Date }) {
  const badge = deadlineBadge(deadline, now);
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-semibold tabular-nums',
        DEADLINE_TONE_STYLES[badge.tone],
      )}
      title={new Date(deadline).toLocaleString()}
    >
      <Clock className="h-3 w-3 shrink-0" aria-hidden="true" />
      {badge.label}
    </span>
  );
}

export function CategoryPill({ category }: { category: OpportunityCategory }) {
  return (
    <span className="inline-flex shrink-0 rounded-full border border-slate-700 bg-slate-800/70 px-2 py-0.5 text-[11px] font-medium text-slate-300">
      {CATEGORY_LABELS[category]}
    </span>
  );
}

export function VerifiedPill({ status }: { status: VerificationStatus }) {
  if (status !== 'VERIFIED') {
    return (
      <span className="inline-flex shrink-0 rounded-full border border-dashed border-slate-600 px-2 py-0.5 text-[11px] text-slate-400">
        Unverified
      </span>
    );
  }
  return (
    <span className="inline-flex shrink-0 items-center gap-1 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-2 py-0.5 text-[11px] font-medium text-emerald-300">
      <BadgeCheck className="h-3 w-3 shrink-0" aria-hidden="true" />
      Verified
    </span>
  );
}
