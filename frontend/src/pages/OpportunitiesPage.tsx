import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Loader2, Search, Trophy, Users } from 'lucide-react';
import { toast } from 'sonner';
import { BookmarkButton } from '../components/opportunities/BookmarkButton';
import { CategoryPill, DeadlinePill, VerifiedPill } from '../components/opportunities/OpportunityBadges';
import { errorMessage, useCollabSource } from '../hooks/useCollabSource';
import { CATEGORY_LABELS, MODE_LABELS } from '../lib/collab/kanban';
import { cn, pluralize } from '../lib/format';
import {
  OPPORTUNITY_CATEGORIES,
  type OpportunityCategory,
  type OpportunitySummary,
  type RankingStrategy,
} from '../types/collab';

const STRATEGIES: { key: RankingStrategy; label: string }[] = [
  { key: 'deadline', label: 'Nearest deadline' },
  { key: 'recent', label: 'Recently published' },
];

/** Debounce so typing in the search box does not fire one request per keystroke. */
function useDebounced<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}

/**
 * Contest & Challenge Board (Week 4, Day 1): ranked opportunity cards with deadline badges, category
 * chips, search, a ranking-strategy toggle and optimistic bookmarks.
 */
export function OpportunitiesPage() {
  const { source } = useCollabSource();
  const [category, setCategory] = useState<OpportunityCategory | null>(null);
  const [strategy, setStrategy] = useState<RankingStrategy>('deadline');
  const [bookmarkedOnly, setBookmarkedOnly] = useState(false);
  const [search, setSearch] = useState('');
  const query = useDebounced(search, 250);
  const [items, setItems] = useState<OpportunitySummary[] | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await source.browseOpportunities({ category: category ?? undefined, q: query, bookmarkedOnly, strategy });
      setItems(page.items);
    } catch (err) {
      toast.error(errorMessage(err, 'Could not load the Contest Board'));
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [source, category, query, bookmarkedOnly, strategy]);

  useEffect(() => {
    void load();
  }, [load]);

  const toggleBookmark = async (item: OpportunitySummary) => {
    const next = !item.bookmarked;
    // Optimistic: flip immediately, roll back if the server refuses.
    setItems((current) => current?.map((o) => (o.id === item.id ? { ...o, bookmarked: next } : o)) ?? current);
    try {
      await source.setBookmark(item.id, next);
      if (bookmarkedOnly && !next) {
        setItems((current) => current?.filter((o) => o.id !== item.id) ?? current);
      }
      toast.success(next ? `Bookmarked ${item.title}` : `Removed bookmark for ${item.title}`);
    } catch (err) {
      setItems((current) => current?.map((o) => (o.id === item.id ? { ...o, bookmarked: item.bookmarked } : o)) ?? current);
      toast.error(errorMessage(err, 'Could not update bookmark'));
    }
  };

  return (
    <div className="space-y-5">
      <section className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          <h2 className="text-2xl font-bold tracking-tight text-white">Contest &amp; Challenge Board</h2>
          <p className="mt-1 max-w-[65ch] text-sm text-slate-400">
            Hackathons, programming contests, robotics, research calls and case competitions. Separate from the CT Hub.
          </p>
        </div>
        <div role="group" aria-label="Ranking strategy" className="inline-flex rounded-lg border border-slate-800 bg-slate-900 p-1">
          {STRATEGIES.map((option) => (
            <button
              key={option.key}
              type="button"
              aria-pressed={strategy === option.key}
              onClick={() => setStrategy(option.key)}
              className={cn(
                'rounded-md px-3 py-1.5 text-xs font-medium transition-colors duration-150 ease-out',
                strategy === option.key ? 'bg-emerald-600 text-white' : 'text-slate-300 hover:bg-slate-800',
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
      </section>

      <section className="space-y-3">
        <label className="relative block">
          <span className="sr-only">Search opportunities</span>
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" aria-hidden="true" />
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search by title or organizer"
            className="w-full rounded-lg border border-slate-800 bg-slate-900 py-2 pl-9 pr-3 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none"
          />
        </label>
        <div className="flex flex-wrap gap-2" role="group" aria-label="Filter by category">
          <Chip active={category === null} onClick={() => setCategory(null)}>All</Chip>
          {OPPORTUNITY_CATEGORIES.map((key) => (
            <Chip key={key} active={category === key} onClick={() => setCategory(category === key ? null : key)}>
              {CATEGORY_LABELS[key]}
            </Chip>
          ))}
          <span className="mx-1 hidden w-px bg-slate-800 sm:block" aria-hidden="true" />
          <Chip active={bookmarkedOnly} onClick={() => setBookmarkedOnly((v) => !v)}>Bookmarked</Chip>
        </div>
      </section>

      <p className="text-xs text-slate-500 tabular-nums" aria-live="polite">
        {loading && items === null ? 'Loading…' : pluralize(items?.length ?? 0, 'opportunity', 'opportunities')}
      </p>

      {loading && items === null ? (
        <div className="flex items-center gap-2 text-sm text-slate-400">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Loading contests…
        </div>
      ) : items && items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-800 bg-slate-900/40 p-10 text-center">
          <Trophy className="mx-auto mb-3 h-8 w-8 text-slate-500" aria-hidden="true" />
          <p className="text-sm font-medium text-slate-200">No opportunities match these filters</p>
          <p className="mt-1 text-xs text-slate-500">Try another category or clear the search.</p>
        </div>
      ) : (
        <ul className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {items?.map((item) => (
            <li key={item.id}>
              <OpportunityCard item={item} onToggleBookmark={() => void toggleBookmark(item)} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        'rounded-full border px-3 py-1 text-xs font-medium transition-colors duration-150 ease-out',
        active
          ? 'border-emerald-500/40 bg-emerald-500/15 text-emerald-200'
          : 'border-slate-800 bg-slate-900 text-slate-300 hover:border-slate-700 hover:text-white',
      )}
    >
      {children}
    </button>
  );
}

function OpportunityCard({ item, onToggleBookmark }: { item: OpportunitySummary; onToggleBookmark: () => void }) {
  return (
    <Link
      to={`/opportunities/${item.id}`}
      className={cn(
        'flex h-full flex-col gap-3 rounded-xl border border-slate-800 bg-slate-900/60 p-4 transition-colors duration-150 ease-out hover:border-slate-700',
        item.expired && 'opacity-70',
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="line-clamp-2 break-words text-sm font-semibold text-white" title={item.title}>
            {item.title}
          </h3>
          <p className="mt-0.5 truncate text-xs text-slate-400" title={item.organizer}>{item.organizer}</p>
        </div>
        <BookmarkButton bookmarked={item.bookmarked} title={item.title} onToggle={onToggleBookmark} />
      </div>
      <div className="flex flex-wrap items-center gap-1.5">
        <DeadlinePill deadline={item.registrationDeadline} />
        <CategoryPill category={item.category} />
        <VerifiedPill status={item.status} />
      </div>
      <div className="mt-auto flex items-center gap-3 text-[11px] text-slate-400">
        <span>{MODE_LABELS[item.mode]}</span>
        <span className="inline-flex items-center gap-1 tabular-nums">
          <Users className="h-3 w-3 shrink-0" aria-hidden="true" />
          {item.teamSizeMin === item.teamSizeMax
            ? pluralize(item.teamSizeMin, 'member', 'members')
            : `${item.teamSizeMin}–${item.teamSizeMax} members`}
        </span>
      </div>
    </Link>
  );
}
