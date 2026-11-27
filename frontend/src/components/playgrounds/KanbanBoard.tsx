import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react';
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import { Eye, Loader2, Plus, Users } from 'lucide-react';
import { toast } from 'sonner';
import { ApiError } from '../../lib/api';
import type { CollabSource } from '../../lib/collab/collabSource';
import type { DemoCollabSource } from '../../lib/collab/demoCollabSource';
import { addCard, COLUMN_LABELS, EMPTY_COLUMN_TEXT, findCard, moveCard, replaceCard } from '../../lib/collab/kanban';
import { cn } from '../../lib/format';
import {
  TASK_PRIORITIES,
  TASK_STATUSES,
  type KanbanBoard as Board,
  type PlaygroundMember,
  type TaskCard,
  type TaskPriority,
  type TaskStatus,
} from '../../types/collab';
import { ConflictBanner } from './ConflictBanner';
import { TaskCardView } from './TaskCardView';

interface KanbanBoardProps {
  playgroundId: number;
  members: PlaygroundMember[];
  canEdit: boolean;
  source: CollabSource;
  demo?: DemoCollabSource | null;
  onChanged?: () => void;
}

/**
 * Interactive Kanban board (Week 4, Days 3-4).
 * <p>
 * Every move is optimistic: the card jumps to its new column at once, then the PATCH carries the card's
 * version as {@code expectedVersion}. On success the card takes the server's new version; on 409 it snaps
 * back and a non-modal banner offers to load the teammate's version. Drag-and-drop (pointer or keyboard)
 * and the per-card status dropdown share this single path.
 */
export function KanbanBoard({ playgroundId, members, canEdit, source, demo, onChanged }: KanbanBoardProps) {
  const [board, setBoard] = useState<Board | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const [reloading, setReloading] = useState(false);
  const [pending, setPending] = useState<Set<number>>(new Set());
  const [activeId, setActiveId] = useState<number | null>(null);

  const sensors = useSensors(
    // A small distance keeps clicks on the card's dropdown from starting a drag.
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor),
  );

  const load = useCallback(async () => {
    const fresh = await source.getBoard(playgroundId);
    setBoard(fresh);
    return fresh;
  }, [source, playgroundId]);

  useEffect(() => {
    load().catch((err) => toast.error(err instanceof Error ? err.message : 'Could not load the board'));
  }, [load]);

  const setPendingFor = (taskId: number, on: boolean) =>
    setPending((current) => {
      const next = new Set(current);
      if (on) next.add(taskId);
      else next.delete(taskId);
      return next;
    });

  const moveTask = useCallback(async (taskId: number, toStatus: TaskStatus) => {
    if (!board) return;
    const original = findCard(board, taskId);
    if (!original || original.status === toStatus) return;

    setBoard((current) => (current ? moveCard(current, taskId, toStatus) : current));
    setPendingFor(taskId, true);
    try {
      const saved = await source.updateTask(playgroundId, taskId, { status: toStatus, expectedVersion: original.version });
      setBoard((current) => (current ? replaceCard(current, saved) : current));
      onChanged?.();
    } catch (err) {
      // Roll back only this card; other in-flight moves keep their optimistic state.
      setBoard((current) => (current ? replaceCard(current, original) : current));
      if (err instanceof ApiError && err.status === 409) {
        setConflict(err.message);
      } else {
        toast.error(err instanceof Error ? err.message : 'Could not move the task');
      }
    } finally {
      setPendingFor(taskId, false);
    }
  }, [board, source, playgroundId, onChanged]);

  const reload = async () => {
    setReloading(true);
    try {
      await load();
      setConflict(null);
      onChanged?.();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Could not reload the board');
    } finally {
      setReloading(false);
    }
  };

  const onDragStart = (event: DragStartEvent) => setActiveId(Number(event.active.id));
  const onDragEnd = (event: DragEndEvent) => {
    setActiveId(null);
    const toStatus = event.over?.id as TaskStatus | undefined;
    if (toStatus) void moveTask(Number(event.active.id), toStatus);
  };

  if (!board) {
    return (
      <div className="flex items-center gap-2 text-sm text-slate-400">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Loading board…
      </div>
    );
  }

  const activeCard = activeId !== null ? findCard(board, activeId) : undefined;

  return (
    <div className="space-y-4">
      {conflict && <ConflictBanner message={conflict} reloading={reloading} onReload={() => void reload()} />}

      {canEdit ? (
        <NewTaskForm
          members={members}
          onCreate={async (payload) => {
            const card = await source.createTask(playgroundId, payload);
            setBoard((current) => (current ? addCard(current, card) : current));
            onChanged?.();
            toast.success(`Added "${card.title}"`);
          }}
        />
      ) : (
        <p className="inline-flex items-center gap-2 rounded-lg border border-slate-800 bg-slate-900/60 px-3 py-2 text-xs text-slate-400">
          <Eye className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          Read-only: only LEAD and CONTRIBUTOR members can move cards.
        </p>
      )}

      {demo && canEdit && (
        <button
          type="button"
          onClick={async () => toast.info(await demo.simulateTeammateEdit(playgroundId))}
          className="inline-flex items-center gap-1.5 rounded-lg border border-dashed border-slate-700 px-3 py-1.5 text-xs text-slate-300 hover:border-slate-500"
        >
          <Users className="h-3.5 w-3.5" aria-hidden="true" />
          Preview: simulate a teammate edit
        </button>
      )}

      <DndContext sensors={sensors} onDragStart={onDragStart} onDragEnd={onDragEnd} onDragCancel={() => setActiveId(null)}>
        <div className="-mx-1 flex gap-3 overflow-x-auto px-1 pb-2">
          {board.columns.map((column) => (
            <Column key={column.status} status={column.status} count={column.tasks.length}>
              {column.tasks.length === 0 ? (
                <p className="rounded-lg border border-dashed border-slate-700/80 px-3 py-6 text-center text-xs text-slate-500">
                  {EMPTY_COLUMN_TEXT[column.status]}
                </p>
              ) : (
                column.tasks.map((task) => (
                  <DraggableCard
                    key={task.id}
                    task={task}
                    canEdit={canEdit}
                    pending={pending.has(task.id)}
                    onMove={(status) => void moveTask(task.id, status)}
                  />
                ))
              )}
            </Column>
          ))}
        </div>
        <DragOverlay>
          {activeCard ? <TaskCardView task={activeCard} canEdit={false} overlay /> : null}
        </DragOverlay>
      </DndContext>
    </div>
  );
}

function Column({ status, count, children }: { status: TaskStatus; count: number; children: ReactNode }) {
  const { setNodeRef, isOver } = useDroppable({ id: status });
  return (
    <section
      ref={setNodeRef}
      aria-label={`${COLUMN_LABELS[status]} column`}
      className={cn(
        'flex w-64 shrink-0 flex-col gap-2 rounded-xl border bg-slate-900/40 p-2.5 transition-colors duration-150 ease-out',
        isOver ? 'border-emerald-500/50 bg-emerald-500/5' : 'border-slate-800',
      )}
    >
      <header className="flex items-center justify-between px-1">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-300">{COLUMN_LABELS[status]}</h3>
        <span className="rounded-full bg-slate-800 px-2 text-[11px] text-slate-400 tabular-nums">{count}</span>
      </header>
      {children}
    </section>
  );
}

function DraggableCard({
  task,
  canEdit,
  pending,
  onMove,
}: {
  task: TaskCard;
  canEdit: boolean;
  pending: boolean;
  onMove: (status: TaskStatus) => void;
}) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: task.id, disabled: !canEdit || pending });
  return (
    <TaskCardView
      ref={setNodeRef}
      task={task}
      canEdit={canEdit}
      pending={pending}
      dragging={isDragging}
      onMove={onMove}
      {...(canEdit ? { ...attributes, ...listeners } : {})}
    />
  );
}

function NewTaskForm({
  members,
  onCreate,
}: {
  members: PlaygroundMember[];
  onCreate: (payload: { title: string; status: TaskStatus; priority: TaskPriority; assigneeId: number | null }) => Promise<void>;
}) {
  const [title, setTitle] = useState('');
  const [status, setStatus] = useState<TaskStatus>('BACKLOG');
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM');
  const [assigneeId, setAssigneeId] = useState<string>('');
  const [saving, setSaving] = useState(false);
  const assignable = members.filter((m) => m.role !== 'VIEWER');

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim()) return;
    setSaving(true);
    try {
      await onCreate({ title: title.trim(), status, priority, assigneeId: assigneeId ? Number(assigneeId) : null });
      setTitle('');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Could not add the task');
    } finally {
      setSaving(false);
    }
  };

  const selectClass = 'rounded-lg border border-slate-800 bg-slate-950 px-2 py-2 text-xs text-slate-200 focus:border-emerald-500/50 focus:outline-none';

  return (
    <form onSubmit={submit} className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-800 bg-slate-900/60 p-2.5">
      <input
        value={title}
        onChange={(event) => setTitle(event.target.value)}
        maxLength={150}
        placeholder="New task title"
        aria-label="New task title"
        className="min-w-[12rem] flex-1 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none"
      />
      <select aria-label="Column" value={status} onChange={(e) => setStatus(e.target.value as TaskStatus)} className={selectClass}>
        {TASK_STATUSES.map((s) => <option key={s} value={s}>{COLUMN_LABELS[s]}</option>)}
      </select>
      <select aria-label="Priority" value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)} className={selectClass}>
        {TASK_PRIORITIES.map((p) => <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>)}
      </select>
      <select aria-label="Assignee" value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)} className={selectClass}>
        <option value="">Unassigned</option>
        {assignable.map((m) => <option key={m.userId} value={m.userId}>{m.displayName}</option>)}
      </select>
      <button
        type="submit"
        disabled={saving || !title.trim()}
        className="inline-flex min-w-[6.5rem] items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <Plus className="h-3.5 w-3.5" aria-hidden="true" />}
        Add task
      </button>
    </form>
  );
}
