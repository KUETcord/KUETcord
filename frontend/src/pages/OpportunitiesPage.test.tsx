import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { resetDemoCollabStore } from '../lib/collab/demoCollabSource';
import { renderApp } from '../test/renderApp';

function cardTitles(): string[] {
  return screen.getAllByRole('listitem').map((li) => within(li).getByRole('heading').textContent ?? '');
}

describe('Contest & Challenge Board (Week 4, Day 1)', () => {
  beforeEach(() => resetDemoCollabStore());

  it('renders ranked cards with deadline, category and verification badges', async () => {
    renderApp('/opportunities');

    expect(await screen.findByText('ICPC Asia Dhaka Regional 2026')).toBeInTheDocument();
    expect(screen.getByText('5 opportunities')).toBeInTheDocument();
    expect(cardTitles()[0]).toBe('ICPC Asia Dhaka Regional 2026');
    expect(cardTitles().at(-1)).toBe('Hult Prize Campus Round');
    expect(screen.getByText('2 days left')).toBeInTheDocument();
    expect(screen.getByText('Closed')).toBeInTheDocument();
    expect(screen.getAllByText('Unverified')).toHaveLength(1);
  });

  it('toggles the ranking strategy to "Recently published"', async () => {
    renderApp('/opportunities');
    await screen.findByText('ICPC Asia Dhaka Regional 2026');

    fireEvent.click(screen.getByRole('button', { name: 'Recently published' }));
    await waitFor(() => expect(cardTitles()[0]).toMatch(/Research Grant/));
    expect(screen.getByRole('button', { name: 'Recently published' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('filters by category chip and by search text, with a helpful empty state', async () => {
    renderApp('/opportunities');
    await screen.findByText('ICPC Asia Dhaka Regional 2026');

    fireEvent.click(screen.getByRole('button', { name: 'Hackathon' }));
    await waitFor(() => expect(screen.getByText('1 opportunity')).toBeInTheDocument());
    expect(cardTitles()).toEqual(['KUET Hackathon 2026: Tech for Impact']);

    fireEvent.click(screen.getByRole('button', { name: 'All' }));
    fireEvent.change(screen.getByPlaceholderText('Search by title or organizer'), { target: { value: 'robotics society' } });
    await waitFor(() => expect(cardTitles()).toEqual(['National Robotics Olympiad: Autonomous Line Follower']));

    fireEvent.change(screen.getByPlaceholderText('Search by title or organizer'), { target: { value: 'no such contest' } });
    expect(await screen.findByText('No opportunities match these filters')).toBeInTheDocument();
  });

  it('bookmarks optimistically and the Bookmarked chip shows only saved contests', async () => {
    renderApp('/opportunities');
    await screen.findByText('ICPC Asia Dhaka Regional 2026');

    const bookmark = screen.getByRole('button', { name: 'Bookmark ICPC Asia Dhaka Regional 2026' });
    fireEvent.click(bookmark);
    expect(await screen.findByRole('button', { name: 'Remove bookmark for ICPC Asia Dhaka Regional 2026' }))
      .toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(screen.getByRole('button', { name: 'Bookmarked' }));
    // The demo CR user had already saved the hackathon; now the ICPC listing too.
    await waitFor(() => expect(cardTitles().sort()).toEqual([
      'ICPC Asia Dhaka Regional 2026',
      'KUET Hackathon 2026: Tech for Impact',
    ]));
  });
});
