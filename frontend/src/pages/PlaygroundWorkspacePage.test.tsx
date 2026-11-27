import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { resetDemoCollabStore } from '../lib/collab/demoCollabSource';
import { renderApp } from '../test/renderApp';

/** Radix tabs activate on mousedown; a plain click is not enough in jsdom. */
function openTab(name: RegExp) {
  const tab = screen.getByRole('tab', { name });
  fireEvent.mouseDown(tab, { button: 0 });
  fireEvent.click(tab);
}

describe('Playground workspace (Week 4, Days 3-5)', () => {
  beforeEach(() => resetDemoCollabStore());

  it('end to end in preview: a teammate edit makes the next move conflict, and reloading shows their change', async () => {
    renderApp('/playgrounds/1');
    expect(await screen.findByRole('heading', { name: 'Team KUETcord: Campus Workflow System' })).toBeInTheDocument();
    const move = await screen.findByLabelText('Move Author k6 load test suite for 50 concurrent VUs');

    fireEvent.click(screen.getByRole('button', { name: /simulate a teammate edit/i }));
    fireEvent.change(move, { target: { value: 'DONE' } });

    const banner = await screen.findByRole('alert');
    fireEvent.click(within(banner).getByRole('button', { name: /Load Latest Changes/i }));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    // The teammate moved it one column right (BACKLOG -> TODO).
    expect(within(screen.getByRole('region', { name: 'To do column' }))
      .getByText('Author k6 load test suite for 50 concurrent VUs')).toBeInTheDocument();
  });

  it('milestone timeline: soonest first, overdue flagged, and "Mark reached" completes one', async () => {
    renderApp('/playgrounds/1');
    await screen.findByRole('heading', { name: 'Team KUETcord: Campus Workflow System' });
    openTab(/Milestones/);

    const timeline = await screen.findByRole('list', { name: 'Milestone timeline' });
    const items = within(timeline).getAllByRole('listitem');
    expect(items.map((li) => li.textContent)).toEqual([
      expect.stringContaining('Proposal submitted'),
      expect.stringContaining('Backend MVP'),
      expect.stringContaining('Final demo'),
    ]);
    expect(within(items[1]).getByText('Overdue')).toBeInTheDocument();

    fireEvent.click(within(items[1]).getByRole('button', { name: /Mark reached/i }));
    expect(await within(items[1]).findByRole('button', { name: /Reopen/i })).toBeInTheDocument();
    expect(within(items[1]).queryByText('Overdue')).not.toBeInTheDocument();
  });

  it('decision log accordion: newest first, rationale revealed on expand, new entries open at the top', async () => {
    renderApp('/playgrounds/1');
    await screen.findByRole('heading', { name: 'Team KUETcord: Campus Workflow System' });
    openTab(/Decision log/);

    const first = await screen.findByRole('button', { name: /Adopt @dnd-kit for drag and drop/i });
    expect(first).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getByText(/Accessible keyboard sensors/)).not.toBeVisible();
    fireEvent.click(first);
    expect(first).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText(/Accessible keyboard sensors/)).toBeVisible();

    fireEvent.change(screen.getByLabelText('Decision'), { target: { value: 'Ship the MVP behind a feature flag' } });
    fireEvent.click(screen.getByRole('button', { name: /Record decision/i }));
    const created = await screen.findByRole('button', { name: /Ship the MVP behind a feature flag/i });
    expect(created).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('No rationale recorded.')).toBeVisible();
  });

  it('a Teacher previewing the same playground is a read-only VIEWER', async () => {
    renderApp('/playgrounds/1');
    await screen.findByRole('heading', { name: 'Team KUETcord: Campus Workflow System' });
    fireEvent.click(screen.getByRole('button', { name: 'Teacher' }));

    expect(await screen.findByText(/Read-only/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Move /)).not.toBeInTheDocument();
    expect(screen.getByText('VIEWER')).toBeInTheDocument();
  });
});
