import { cn, getInitials } from '../../lib/format';

/** Initials avatar; `shrink-0` so it never squashes into an oval inside flex rows (DESIGN_GUIDELINES 2.2). */
export function Avatar({ name, size = 'sm', className }: { name: string; size?: 'xs' | 'sm'; className?: string }) {
  return (
    <span
      title={name}
      aria-label={name}
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-full border border-emerald-500/30 bg-emerald-600/20 font-semibold text-emerald-200',
        size === 'xs' ? 'h-6 w-6 text-[10px]' : 'h-8 w-8 text-xs',
        className,
      )}
    >
      {getInitials(name)}
    </span>
  );
}
