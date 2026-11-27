import { describe, expect, it } from 'vitest';
import type { KanbanBoard, TaskCard } from '../../types/collab';
import { addCard, deadlineBadge, findCard, moveCard, replaceCard } from './kanban';

function card(id: number, status: TaskCard['status'], position: number, version = 0): TaskCard {
  return { id, title: `Task ${id}`, status, priority: 'MEDIUM', position, dueDate: null, assignee: null, version };
}

function board(): KanbanBoard {
  return {
    playgroundId: 1,
    totalTasks: 3,
    columns: [
      { status: 'BACKLOG', tasks: [card(1, 'BACKLOG', 0), card(2, 'BACKLOG', 1)] },
      { status: 'TODO', tasks: [card(3, 'TODO', 4)] },
      { status: 'IN_PROGRESS', tasks: [] },
      { status: 'REVIEW', tasks: [] },
      { status: 'DONE', tasks: [] },
    ],
  };
}

describe('Kanban board helpers', () => {
  it('moves a card to the bottom of the target column without mutating the snapshot', () => {
    const before = board();
    const after = moveCard(before, 1, 'TODO');

    expect(after.columns[0].tasks.map((t) => t.id)).toEqual([2]);
    expect(after.columns[1].tasks.map((t) => t.id)).toEqual([3, 1]);
    expect(findCard(after, 1)).toMatchObject({ status: 'TODO', position: 5 });
    // The rollback snapshot is untouched.
    expect(before.columns[0].tasks.map((t) => t.id)).toEqual([1, 2]);
    expect(findCard(before, 1)?.status).toBe('BACKLOG');
  });

  it('starts an empty column at position 0 and treats a same-column move as a no-op', () => {
    const before = board();
    expect(findCard(moveCard(before, 3, 'REVIEW'), 3)?.position).toBe(0);
    expect(moveCard(before, 3, 'TODO')).toBe(before);
    expect(moveCard(before, 999, 'DONE')).toBe(before);
  });

  it('replaceCard restores the original card (rollback) and applies the server version (commit)', () => {
    const original = findCard(board(), 1)!;
    const optimistic = moveCard(board(), 1, 'DONE');

    const rolledBack = replaceCard(optimistic, original);
    expect(rolledBack.columns[0].tasks.map((t) => t.id)).toEqual([1, 2]);
    expect(rolledBack.columns[4].tasks).toHaveLength(0);

    const committed = replaceCard(optimistic, { ...original, status: 'DONE', position: 0, version: 1 });
    expect(findCard(committed, 1)).toMatchObject({ status: 'DONE', version: 1 });
  });

  it('addCard appends to its column and bumps the total', () => {
    const next = addCard(board(), card(9, 'REVIEW', 0));
    expect(next.totalTasks).toBe(4);
    expect(next.columns[3].tasks.map((t) => t.id)).toEqual([9]);
  });
});

describe('deadlineBadge', () => {
  const now = new Date('2026-11-20T12:00:00Z');
  const at = (hours: number) => new Date(now.getTime() + hours * 3600_000).toISOString();

  it('labels closed, today, urgent, soon and far deadlines', () => {
    expect(deadlineBadge(at(-1), now)).toEqual({ label: 'Closed', tone: 'closed' });
    expect(deadlineBadge(at(5), now)).toEqual({ label: 'Closes today', tone: 'urgent' });
    expect(deadlineBadge(at(30), now)).toEqual({ label: '1 day left', tone: 'urgent' });
    expect(deadlineBadge(at(60), now)).toEqual({ label: '2 days left', tone: 'urgent' });
    expect(deadlineBadge(at(24 * 5), now)).toEqual({ label: '5 days left', tone: 'soon' });
    expect(deadlineBadge(at(24 * 20), now)).toEqual({ label: 'Closes Dec 10', tone: 'open' });
  });

  it('never crashes on a malformed date', () => {
    expect(deadlineBadge('not-a-date', now)).toEqual({ label: 'Deadline TBA', tone: 'open' });
  });
});
