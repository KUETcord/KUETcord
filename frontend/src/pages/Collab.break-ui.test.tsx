import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Avatar } from '../components/collab/Avatar';
import { KanbanBoard } from '../components/playgrounds/KanbanBoard';
import type { CollabSource } from '../lib/collab/collabSource';
import { pluralize } from '../lib/format';

const LONG_TITLE =
  'CSE 3103: Advanced Algorithms, Graph Traversals, Dynamic Programming & NP-Completeness Revision Sprint Before CT-2';

describe('Member C adversarial break-ui checks (DESIGN_GUIDELINES section 6)', () => {
  it('long card titles are clamped (with the full title discoverable) and long Unicode names still get initials', async () => {
    const source = {
      getBoard: vi.fn().mockResolvedValue({
        playgroundId: 1,
        totalTasks: 1,
        columns: ['BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'].map((status) => ({
          status,
          tasks: status === 'TODO'
            ? [{ id: 1, title: LONG_TITLE, status, priority: 'CRITICAL', position: 0, dueDate: null, version: 0,
                assignee: { id: 9, displayName: 'Aleksandra Wiśniewska-Kowalczyk' } }]
            : [],
        })),
      }),
    } as unknown as CollabSource;
    render(<KanbanBoard playgroundId={1} members={[]} canEdit={false} source={source} />);

    const title = await screen.findByText(LONG_TITLE);
    expect(title).toHaveClass('line-clamp-3', 'break-words');
    expect(title).toHaveAttribute('title', LONG_TITLE);
    expect(screen.getByLabelText('Aleksandra Wiśniewska-Kowalczyk')).toHaveTextContent('AW');
    expect(screen.getByText('No tasks in review')).toBeInTheDocument();
  });

  it('one-word and two-letter names render clean initials on a non-shrinking avatar', () => {
    render(<><Avatar name="Jo" /><Avatar name="Rahim" /></>);
    expect(screen.getByLabelText('Jo')).toHaveTextContent('JO');
    expect(screen.getByLabelText('Jo')).toHaveClass('shrink-0');
    expect(screen.getByLabelText('Rahim')).toHaveTextContent('RA');
  });

  it('counts are pluralized exactly', () => {
    expect(pluralize(1, 'opportunity', 'opportunities')).toBe('1 opportunity');
    expect(pluralize(0, 'team', 'teams')).toBe('0 teams');
    expect(pluralize(1, 'member', 'members')).toBe('1 member');
  });
});
