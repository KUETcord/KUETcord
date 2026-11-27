import { Bookmark, BookmarkCheck } from 'lucide-react';
import { cn } from '../../lib/format';

export function BookmarkButton({
  bookmarked,
  title,
  onToggle,
  disabled,
}: {
  bookmarked: boolean;
  title: string;
  onToggle: () => void;
  disabled?: boolean;
}) {
  const Icon = bookmarked ? BookmarkCheck : Bookmark;
  return (
    <button
      type="button"
      aria-pressed={bookmarked}
      aria-label={bookmarked ? `Remove bookmark for ${title}` : `Bookmark ${title}`}
      disabled={disabled}
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
        onToggle();
      }}
      className={cn(
        'shrink-0 rounded-lg border p-1.5 transition-colors duration-150 ease-out disabled:cursor-not-allowed disabled:opacity-50',
        bookmarked
          ? 'border-emerald-500/40 bg-emerald-500/15 text-emerald-300 hover:bg-emerald-500/25'
          : 'border-slate-700 bg-slate-900 text-slate-400 hover:border-slate-600 hover:text-slate-200',
      )}
    >
      <Icon className="h-4 w-4" aria-hidden="true" />
    </button>
  );
}
