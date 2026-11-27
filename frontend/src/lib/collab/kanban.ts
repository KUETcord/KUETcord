import type { KanbanBoard, TaskCard, TaskPriority, TaskStatus } from '../../types/collab';

export const COLUMN_LABELS: Record<TaskStatus, string> = {
  BACKLOG: 'Backlog',
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  REVIEW: 'Review',
  DONE: 'Done',
};

/** Empty-column placeholder copy (DESIGN_GUIDELINES section 6: "No tasks in review"). */
export const EMPTY_COLUMN_TEXT: Record<TaskStatus, string> = {
  BACKLOG: 'No tasks in backlog',
  TODO: 'No tasks to do',
  IN_PROGRESS: 'No tasks in progress',
  REVIEW: 'No tasks in review',
  DONE: 'No tasks done yet',
};

export const PRIORITY_STYLES: Record<TaskPriority, string> = {
  LOW: 'border-slate-600/50 bg-slate-700/30 text-slate-300',
  MEDIUM: 'border-sky-500/30 bg-sky-500/10 text-sky-300',
  HIGH: 'border-amber-500/30 bg-amber-500/10 text-amber-300',
  CRITICAL: 'border-rose-500/40 bg-rose-500/15 text-rose-300',
};

export function findCard(board: KanbanBoard, taskId: number): TaskCard | undefined {
  for (const column of board.columns) {
    const card = column.tasks.find((task) => task.id === taskId);
    if (card) return card;
  }
  return undefined;
}

/**
 * Optimistic move, matching the server rule: a card that changes column goes to the bottom of the
 * target column. Returns a new board; the input is never mutated, so the caller can keep the previous
 * board as the rollback snapshot. Moving a card to its own column is a no-op (same object returned).
 */
export function moveCard(board: KanbanBoard, taskId: number, toStatus: TaskStatus): KanbanBoard {
  const card = findCard(board, taskId);
  if (!card || card.status === toStatus) return board;

  const target = board.columns.find((column) => column.status === toStatus);
  const nextPosition = target && target.tasks.length > 0
    ? Math.max(...target.tasks.map((task) => task.position)) + 1
    : 0;
  const moved: TaskCard = { ...card, status: toStatus, position: nextPosition };

  return {
    ...board,
    columns: board.columns.map((column) => {
      if (column.status === card.status) {
        return { ...column, tasks: column.tasks.filter((task) => task.id !== taskId) };
      }
      if (column.status === toStatus) {
        return { ...column, tasks: [...column.tasks, moved] };
      }
      return column;
    }),
  };
}

/** Replaces one card with the server's version of it (new version number, canonical position). */
export function replaceCard(board: KanbanBoard, updated: TaskCard): KanbanBoard {
  const withoutCard = board.columns.map((column) => ({
    ...column,
    tasks: column.tasks.filter((task) => task.id !== updated.id),
  }));
  return {
    ...board,
    columns: withoutCard.map((column) =>
      column.status === updated.status
        ? { ...column, tasks: [...column.tasks, updated].sort((a, b) => a.position - b.position || a.id - b.id) }
        : column,
    ),
  };
}

export function addCard(board: KanbanBoard, card: TaskCard): KanbanBoard {
  return { ...replaceCard(board, card), totalTasks: board.totalTasks + 1 };
}

export type DeadlineTone = 'closed' | 'urgent' | 'soon' | 'open';

export interface DeadlineBadge {
  label: string;
  tone: DeadlineTone;
}

/**
 * Registration deadline badge for contest cards: "Closed", "Closes today", "3 days left" (urgent under
 * 3 days, soon under 7) or the absolute date further out.
 */
export function deadlineBadge(deadlineIso: string, now: Date = new Date()): DeadlineBadge {
  const deadline = new Date(deadlineIso);
  const msLeft = deadline.getTime() - now.getTime();
  if (Number.isNaN(msLeft)) return { label: 'Deadline TBA', tone: 'open' };
  if (msLeft <= 0) return { label: 'Closed', tone: 'closed' };

  const dayMs = 24 * 60 * 60 * 1000;
  const daysLeft = Math.floor(msLeft / dayMs);
  if (daysLeft === 0) return { label: 'Closes today', tone: 'urgent' };
  if (daysLeft < 3) return { label: `${daysLeft} ${daysLeft === 1 ? 'day' : 'days'} left`, tone: 'urgent' };
  if (daysLeft < 7) return { label: `${daysLeft} days left`, tone: 'soon' };
  return {
    label: `Closes ${new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' }).format(deadline)}`,
    tone: 'open',
  };
}

export const DEADLINE_TONE_STYLES: Record<DeadlineTone, string> = {
  closed: 'border-slate-700 bg-slate-800/60 text-slate-400',
  urgent: 'border-rose-500/40 bg-rose-500/15 text-rose-300',
  soon: 'border-amber-500/30 bg-amber-500/10 text-amber-300',
  open: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300',
};

export const CATEGORY_LABELS: Record<string, string> = {
  PROGRAMMING_CONTEST: 'Programming contest',
  HACKATHON: 'Hackathon',
  ROBOTICS: 'Robotics',
  RESEARCH: 'Research',
  CASE_COMPETITION: 'Case competition',
};

export const MODE_LABELS: Record<string, string> = {
  ONLINE: 'Online',
  ON_SITE: 'On-site',
  HYBRID: 'Hybrid',
};
