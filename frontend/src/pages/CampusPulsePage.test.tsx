import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { resetDemoStudyStore } from '../lib/study/demoStudySource';
import { renderApp } from '../test/renderApp';

describe('Campus Pulse feed (Week 5, Day 3)', () => {
  beforeEach(() => resetDemoStudyStore());

  it('lists announcements newest first and filters by Club, Seminar and Department chips', async () => {
    renderApp('/campus-pulse');
    const feed = await screen.findByRole('feed', { name: 'Announcements' });
    const titles = within(feed).getAllByRole('heading').map((h) => h.textContent);
    expect(titles[0]).toBe('Intra-KUET Programming Contest: registration open');
    expect(titles).toHaveLength(6);

    fireEvent.click(screen.getByRole('button', { name: 'Department' }));
    expect(screen.getByRole('button', { name: 'Department' })).toHaveAttribute('aria-pressed', 'true');
    await waitFor(() => expect(within(screen.getByRole('feed')).getAllByRole('article')).toHaveLength(1));
    expect(screen.getByText('CSE 3103 CT-2 moved to Sunday, 10:00')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Seminar' }));
    await waitFor(() => expect(within(screen.getByRole('feed')).getAllByRole('article')).toHaveLength(2));
    expect(screen.getByText('2 announcements')).toBeInTheDocument();
  });

  it('students see no composer; a Teacher publishes and the post appears at the top', async () => {
    renderApp('/campus-pulse');
    await screen.findByRole('feed');
    expect(screen.queryByRole('form', { name: 'Publish an announcement' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Teacher' }));
    const form = await screen.findByRole('form', { name: 'Publish an announcement' });
    fireEvent.change(within(form).getByLabelText('Organization'), { target: { value: 'Department of CSE' } });
    fireEvent.change(within(form).getByLabelText('Announcement title'), { target: { value: 'Lab 3 closed on Friday' } });
    fireEvent.change(within(form).getByLabelText('Announcement body'), { target: { value: 'Maintenance work.' } });
    fireEvent.click(within(form).getByRole('button', { name: 'Publish' }));

    await waitFor(() => expect(within(screen.getByRole('feed')).getAllByRole('heading')[0]).toHaveTextContent('Lab 3 closed on Friday'));
  });
});
