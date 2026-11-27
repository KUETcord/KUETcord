import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { resetDemoStudyStore } from '../lib/study/demoStudySource';
import { renderApp } from '../test/renderApp';

describe('Study Circles hub and circle page (Week 5, Days 1-2)', () => {
  beforeEach(() => resetDemoStudyStore());

  it('hub: upcoming session calendar cards for my circles, and Join turns a card into a membership', async () => {
    renderApp('/study-circles');

    const upcoming = await screen.findByRole('list', { name: 'Upcoming sessions' });
    expect(within(upcoming).getByText('CT-2 Graph Algorithms Sprint')).toBeInTheDocument();
    expect(within(upcoming).queryByText('Design Patterns Case Drill')).not.toBeInTheDocument();

    const circles = await screen.findByRole('list', { name: 'Study circles' });
    fireEvent.click(within(circles).getByRole('button', { name: 'Join Design Patterns Case Drill' }));
    await waitFor(() => expect(within(circles).queryByRole('button', { name: 'Join Design Patterns Case Drill' })).not.toBeInTheDocument());
    // Karim now attends it, and its next session joins his calendar strip.
    expect(await within(screen.getByRole('list', { name: 'Upcoming sessions' })).findByText('Design Patterns Case Drill')).toBeInTheDocument();
  });

  it('hub honours the CT Hub deep link (?courseId=) and can clear it', async () => {
    renderApp('/study-circles?courseId=101&courseCode=CSE%203201');
    const circles = await screen.findByRole('list', { name: 'Study circles' });
    await waitFor(() => expect(within(circles).getAllByRole('listitem')).toHaveLength(1));
    expect(within(circles).getByText('Design Patterns Case Drill')).toBeInTheDocument();
    expect(screen.getByText('CSE 3201', { selector: 'span' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Show all courses' }));
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Study circles' })).getAllByRole('listitem')).toHaveLength(3));
  });

  it('circle page: ticking a topic updates progress at once; new topics append; resources open safely', async () => {
    renderApp('/study-circles/1');
    expect(await screen.findByRole('heading', { name: 'CT-2 Graph Algorithms Sprint' })).toBeInTheDocument();
    expect(await screen.findByText('1 of 4 revised')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('checkbox', { name: /Dijkstra with a binary heap/ }));
    expect(screen.getByText('2 of 4 revised')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Revision progress' })).toHaveAttribute('aria-valuenow', '50');
    expect(await screen.findByText('ticked by Karim Rahman')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('New revision topic'), { target: { value: 'Edmonds-Karp max flow' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(await screen.findByText('2 of 5 revised')).toBeInTheDocument();
    const topics = within(screen.getByRole('list', { name: 'Revision topics' })).getAllByRole('listitem');
    expect(topics[topics.length - 1]).toHaveTextContent('Edmonds-Karp max flow');

    const link = await screen.findByRole('link', { name: /CP-Algorithms: Dijkstra on sparse graphs/ });
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(screen.getByRole('timer')).toHaveTextContent('25:00');
  });

  it('circle page for an outsider hides members-only content behind Join, then reveals it', async () => {
    renderApp('/study-circles/2');
    expect(await screen.findByText(/Join this circle to see its sessions/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Revision checklist/ })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Join circle' }));
    expect(await screen.findByRole('heading', { name: /Revision checklist/ })).toBeInTheDocument();
    expect(await screen.findByRole('article', { name: 'Strategy vs State on the CT-1 case' })).toBeInTheDocument();
    expect(screen.getByText('ATTENDEE')).toBeInTheDocument();
  });
});
