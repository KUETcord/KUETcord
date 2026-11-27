import { useEffect, useState, type FormEvent } from 'react';
import { ExternalLink, Loader2, Megaphone, Send } from 'lucide-react';
import { toast } from 'sonner';
import { errorMessage } from '../hooks/useCollabSource';
import { useStudySource } from '../hooks/useStudySource';
import { cn, formatExactDateTime, formatSmartTimestamp, pluralize } from '../lib/format';
import { ANNOUNCEMENT_CATEGORIES, type Announcement, type AnnouncementCategory } from '../types/study';

/** ACADEMIC is what departments publish, so the feed calls it "Department". */
export const ANNOUNCEMENT_LABELS: Record<AnnouncementCategory, string> = {
  CLUB: 'Club',
  SEMINAR: 'Seminar',
  ACADEMIC: 'Department',
  CAREER: 'Career',
};

const CATEGORY_STYLES: Record<AnnouncementCategory, string> = {
  CLUB: 'border-violet-500/30 bg-violet-500/10 text-violet-300',
  SEMINAR: 'border-sky-500/30 bg-sky-500/10 text-sky-300',
  ACADEMIC: 'border-amber-500/30 bg-amber-500/10 text-amber-300',
  CAREER: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300',
};

function AnnouncementCard({ item }: { item: Announcement }) {
  const [expanded, setExpanded] = useState(false);
  const long = item.body.length > 220;
  const bodyId = `announcement-body-${item.id}`;
  return (
    <article className="rounded-xl border border-slate-800 bg-slate-900/60 p-4" aria-labelledby={`announcement-${item.id}`}>
      <div className="flex flex-wrap items-center gap-2 text-[11px]">
        <span className={cn('rounded-full border px-2 py-0.5 font-medium', CATEGORY_STYLES[item.category])}>
          {ANNOUNCEMENT_LABELS[item.category]}
        </span>
        <span className="min-w-0 truncate text-slate-300">{item.organization}</span>
        <span className="text-slate-600">·</span>
        <time dateTime={item.publishedAt} title={formatExactDateTime(item.publishedAt)} className="text-slate-500 tabular-nums">
          {formatSmartTimestamp(item.publishedAt)}
        </time>
      </div>
      <h3 id={`announcement-${item.id}`} className="mt-2 break-words text-sm font-semibold text-white">{item.title}</h3>
      <p id={bodyId} className={cn('mt-1.5 whitespace-pre-line break-words text-sm text-slate-300', !expanded && 'line-clamp-3')}>
        {item.body}
      </p>
      <div className="mt-2 flex items-center gap-3 text-xs">
        {long && (
          <button
            type="button"
            aria-expanded={expanded}
            aria-controls={bodyId}
            onClick={() => setExpanded((v) => !v)}
            className="text-slate-400 hover:text-white"
          >
            {expanded ? 'Show less' : 'Read more'}
          </button>
        )}
        {item.sourceUrl && (
          <a href={item.sourceUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-emerald-300 hover:underline">
            Source <ExternalLink className="h-3 w-3" aria-hidden="true" />
          </a>
        )}
      </div>
    </article>
  );
}

function Composer({ onPublished }: { onPublished: (item: Announcement) => void }) {
  const { source } = useStudySource();
  const [organization, setOrganization] = useState('');
  const [category, setCategory] = useState<AnnouncementCategory>('ACADEMIC');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [saving, setSaving] = useState(false);

  const publish = async (event: FormEvent) => {
    event.preventDefault();
    if (!organization.trim() || !title.trim() || !body.trim()) return;
    setSaving(true);
    try {
      const created = await source.publishAnnouncement({ organization, category, title, body });
      onPublished(created);
      setTitle('');
      setBody('');
      toast.success('Announcement published');
    } catch (err) {
      toast.error(errorMessage(err, 'Could not publish the announcement'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={publish} aria-label="Publish an announcement" className="grid gap-2 rounded-xl border border-slate-800 bg-slate-900/60 p-3 sm:grid-cols-[1fr_auto]">
      <input
        value={organization}
        onChange={(e) => setOrganization(e.target.value)}
        maxLength={100}
        placeholder="Organization, e.g. Department of CSE"
        aria-label="Organization"
        className="min-w-0 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none"
      />
      <select
        aria-label="Category"
        value={category}
        onChange={(e) => setCategory(e.target.value as AnnouncementCategory)}
        className="rounded-lg border border-slate-800 bg-slate-950 px-2 py-2 text-sm text-slate-200"
      >
        {ANNOUNCEMENT_CATEGORIES.map((c) => <option key={c} value={c}>{ANNOUNCEMENT_LABELS[c]}</option>)}
      </select>
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        maxLength={200}
        placeholder="Title"
        aria-label="Announcement title"
        className="min-w-0 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none sm:col-span-2"
      />
      <textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        maxLength={10000}
        rows={3}
        placeholder="What should students know?"
        aria-label="Announcement body"
        className="min-w-0 resize-y rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none sm:col-span-2"
      />
      <button
        type="submit"
        disabled={saving || !organization.trim() || !title.trim() || !body.trim()}
        className="inline-flex min-w-[7rem] items-center justify-center gap-1.5 justify-self-end rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50 sm:col-span-2"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <Send className="h-3.5 w-3.5" aria-hidden="true" />}
        Publish
      </button>
    </form>
  );
}

/** Campus Pulse: club, seminar, department and career announcements, newest first, filterable by category. */
export function CampusPulsePage() {
  const { source, canPublish } = useStudySource();
  const [category, setCategory] = useState<AnnouncementCategory | null>(null);
  const [items, setItems] = useState<Announcement[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setItems(null);
    source.listAnnouncements({ category: category ?? undefined, page: 0 })
      .then((result) => {
        if (cancelled) return;
        setItems(result.items);
        setPage(0);
        setTotalPages(result.totalPages);
        setTotalItems(result.totalItems);
      })
      .catch((err) => {
        if (cancelled) return;
        setItems([]);
        toast.error(errorMessage(err, 'Could not load Campus Pulse'));
      });
    return () => { cancelled = true; };
  }, [source, category]);

  const loadMore = async () => {
    setLoadingMore(true);
    try {
      const result = await source.listAnnouncements({ category: category ?? undefined, page: page + 1 });
      setItems((prev) => [...(prev ?? []), ...result.items.filter((n) => !(prev ?? []).some((p) => p.id === n.id))]);
      setPage(result.page);
      setTotalPages(result.totalPages);
    } catch (err) {
      toast.error(errorMessage(err, 'Could not load more announcements'));
    } finally {
      setLoadingMore(false);
    }
  };

  const published = (item: Announcement) => {
    if (category === null || category === item.category) {
      setItems((prev) => [item, ...(prev ?? [])]);
      setTotalItems((n) => n + 1);
    }
  };

  const chips: { label: string; value: AnnouncementCategory | null }[] = [
    { label: 'All', value: null },
    ...ANNOUNCEMENT_CATEGORIES.map((c) => ({ label: ANNOUNCEMENT_LABELS[c], value: c })),
  ];

  return (
    <div className="space-y-5">
      <div>
        <h2 className="flex items-center gap-2 text-2xl font-bold tracking-tight text-white">
          <Megaphone className="h-6 w-6 text-emerald-300" aria-hidden="true" /> Campus Pulse
        </h2>
        <p className="mt-1 max-w-[65ch] text-sm text-slate-400">Announcements from clubs, seminars, departments and the career cell.</p>
      </div>

      <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter by category">
        {chips.map((chip) => (
          <button
            key={chip.label}
            type="button"
            aria-pressed={category === chip.value}
            onClick={() => setCategory(chip.value)}
            className={cn('rounded-full border px-3 py-1 text-xs font-medium',
              category === chip.value
                ? 'border-emerald-500/50 bg-emerald-500/15 text-emerald-200'
                : 'border-slate-800 text-slate-400 hover:border-slate-700 hover:text-slate-200')}
          >
            {chip.label}
          </button>
        ))}
      </div>

      {canPublish && <Composer onPublished={published} />}

      {items === null ? (
        <Loader2 className="h-4 w-4 animate-spin text-slate-400" aria-label="Loading announcements" />
      ) : items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-800 p-10 text-center">
          <Megaphone className="mx-auto mb-3 h-8 w-8 text-slate-500" aria-hidden="true" />
          <p className="text-sm text-slate-300">No announcements in this category yet.</p>
        </div>
      ) : (
        <>
          <p className="text-xs text-slate-500 tabular-nums">{pluralize(totalItems, 'announcement', 'announcements')}</p>
          <div className="space-y-3" aria-label="Announcements" role="feed" aria-busy={loadingMore}>
            {items.map((item) => <AnnouncementCard key={item.id} item={item} />)}
          </div>
          {page + 1 < totalPages && (
            <button
              type="button"
              onClick={loadMore}
              disabled={loadingMore}
              className="mx-auto flex min-w-[8rem] items-center justify-center gap-1.5 rounded-lg border border-slate-700 px-3 py-2 text-xs text-slate-300 hover:bg-slate-800 disabled:opacity-50"
            >
              {loadingMore && <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />}
              Load more
            </button>
          )}
        </>
      )}
    </div>
  );
}
