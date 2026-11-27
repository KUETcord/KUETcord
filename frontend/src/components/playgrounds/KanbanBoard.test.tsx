import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../lib/api';
import type { CollabSource } from '../../lib/collab/collabSource';
import type { KanbanBoard as Board, PlaygroundMember, TaskCard } from '../../types/collab';
import { KanbanBoard } from './KanbanBoard';

const MEMBERS: PlaygroundMember[] = [
  { userId: 2, displayName: 'Karim Rahman', username: 'karim', role: 'LEAD', joinedAt: '2026-11-01T00:00:00Z' },
  { userId: 10, displayName: 'Prof. Hasan', username: 'hasan', role: 'VIEWER', joinedAt: '2026-11-01T00:00:00Z' },
];

const card = (id: number, status: TaskCard['status'], version = 0, title = `Task ${id}`): TaskCard => ({
  id, title, status, priority: 'HIGH', position: 0, dueDate: null, assignee: null, version,
});

function boardWith(cards: TaskCard[]): Board {
  const statuses = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'] as const;
  return {
    playgroundId: 1,
    totalTasks: cards.length,
    columns: statuses.map((status) => ({ status, tasks: cards.filter((c) => c.status === status) })),
  };
}

function fakeSource(overrides: Partial<CollabSource>): CollabSource {
  return { getBoard: vi.fn(), updateTask: vi.fn(), createTask: vi.fn(), ...overrides } as unknown as CollabSource;
}

function column(name: string) {
  return screen.getByRole('region', { name: `${name} column` });
}

describe('Kanban board (Week 4, Days 3-4)', () => {
  it('renders the five columns in board order with counts and dotted empty placeholders', async () => {
    const source = fakeSource({ getBoard: vi.fn().mockResolvedValue(boardWith([card(1, 'TODO')])) });
    render(<KanbanBoard playgroundId={1} members={MEMBERS} canEdit source={source} />);

    await screen.findByText('Task 1');
    const headings = screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent);
    expect(headings).toEqual(['Backlog', 'To do', 'In progress', 'Review', 'Done']);
    expect(within(column('To do')).getByText('1')).toBeInTheDocument();
    expect(screen.getByText('No tasks in review')).toBeInTheDocument();
    expect(screen.getByText('HIGH')).toBeInTheDocument();
  });

  it('moves a card optimistically and sends its version as expectedVersion', async () => {
    let resolveUpdate: (value: TaskCard) => void = () => {};
    const updateTask = vi.fn(() => new Promise<TaskCard>((resolve) => { resolveUpdate = resolve; }));
    const source = fakeSource({ getBoard: vi.fn().mockResolvedValue(boardWith([card(1, 'TODO', 3)])), updateTask });
    render(<KanbanBoard playgroundId={1} members={MEMBERS} canEdit source={source} />);

    fireEvent.change(await screen.findByLabelText('Move Task 1'), { target: { value: 'IN_PROGRESS' } });

    // Before the server answers, the card is already in its new column.
    expect(within(column('In progress')).getByText('Task 1')).toBeInTheDocument();
    expect(updateTask).toHaveBeenCalledWith(1, 1, { status: 'IN_PROGRESS', expectedVersion: 3 });

    resolveUpdate(card(1, 'IN_PROGRESS', 4));
    await waitFor(() => expect(screen.getByLabelText('Move Task 1')).not.toBeDisabled());
    expect(within(column('In progress')).getByText('Task 1')).toBeInTheDocument();
  });

  it('on 409 rolls the card back and shows the non-modal banner; "Load Latest Changes" reloads', async () => {
    const getBoard = vi.fn()
      .mockResolvedValueOnce(boardWith([card(1, 'TODO', 0)]))
      .mockResolvedValueOnce(boardWith([card(1, 'REVIEW', 1, 'Task 1 (moved by Arif)')]));
    const updateTask = vi.fn().mockRejectedValue(
      new ApiError(409, 'Resource was modified by another concurrent action. Please reload.'));
    render(<KanbanBoard playgroundId={1} members={MEMBERS} canEdit source={fakeSource({ getBoard, updateTask })} />);

    fireEvent.change(await screen.findByLabelText('Move Task 1'), { target: { value: 'DONE' } });

    const banner = await screen.findByRole('alert');
    expect(banner).toHaveTextContent('A teammate changed this card first');
    expect(within(column('To do')).getByText('Task 1')).toBeInTheDocument();
    expect(within(column('Done')).queryByText('Task 1')).not.toBeInTheDocument();

    fireEvent.click(within(banner).getByRole('button', { name: /Load Latest Changes/i }));
    expect(await within(column('Review')).findByText('Task 1 (moved by Arif)')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });

  it('read-only for VIEWERs: no move dropdowns and no new-task form', async () => {
    const source = fakeSource({ getBoard: vi.fn().mockResolvedValue(boardWith([card(1, 'TODO')])) });
    render(<KanbanBoard playgroundId={1} members={MEMBERS} canEdit={false} source={source} />);

    await screen.findByText('Task 1');
    expect(screen.queryByLabelText('Move Task 1')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('New task title')).not.toBeInTheDocument();
    expect(screen.getByText(/Read-only/)).toBeInTheDocument();
  });

  it('adds a task; VIEWER members are not offered as assignees', async () => {
    const createTask = vi.fn().mockResolvedValue({ ...card(7, 'BACKLOG'), title: 'Write the pitch deck' });
    const source = fakeSource({ getBoard: vi.fn().mockResolvedValue(boardWith([])), createTask });
    render(<KanbanBoard playgroundId={1} members={MEMBERS} canEdit source={source} />);

    const assignee = await screen.findByLabelText('Assignee');
    expect(within(assignee).queryByText('Prof. Hasan')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('New task title'), { target: { value: 'Write the pitch deck' } });
    fireEvent.change(assignee, { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: /Add task/i }));

    expect(await within(column('Backlog')).findByText('Write the pitch deck')).toBeInTheDocument();
    expect(createTask).toHaveBeenCalledWith(1, { title: 'Write the pitch deck', status: 'BACKLOG', priority: 'MEDIUM', assigneeId: 2 });
  });
});
