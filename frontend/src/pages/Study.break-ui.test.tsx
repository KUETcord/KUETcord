import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { RevisionChecklist } from '../components/study/RevisionChecklist';
import { SessionCard } from '../components/study/SessionCard';
import type { StudySource } from '../lib/study/studySource';
import { CampusPulsePage } from './CampusPulsePage';

const { LONG_BODY, handle } = vi.hoisted(() => {
  const body = 'The Department of Computer Science and Engineering announces '.repeat(12);
  // One stable handle, as the real hook memoizes it; a fresh object per render would refetch forever.
  return {
    LONG_BODY: body,
    handle: {
      source: {
        listAnnouncements: () => Promise.resolve({
          items: [{ id: 1, organization: 'Department of Computer Science and Engineering, Khulna University of Engineering & Technology',
            category: 'ACADEMIC', title: 'Notice', body, sourceUrl: null, publishedAt: new Date().toISOString(), author: null }],
          page: 0, size: 10, totalItems: 1, totalPages: 1,
        }),
      },
      demo: true,
      viewerId: 2,
      canPublish: false,
    },
  };
});

vi.mock('../hooks/useStudySource', () => ({ useStudySource: () => handle }));

describe('Member C Week 5 break-ui checks (DESIGN_GUIDELINES section 6)', () => {
  it('a 700-character announcement is clamped to three lines with an accessible Read more toggle', async () => {
    render(<MemoryRouter><CampusPulsePage /></MemoryRouter>);
    const body = await screen.findByText(LONG_BODY.trim());
    expect(body).toHaveClass('line-clamp-3', 'break-words');
    expect(screen.getByText(/Department of Computer Science and Engineering, Khulna/)).toHaveClass('truncate');

    const toggle = screen.getByRole('button', { name: 'Read more' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(toggle);
    expect(body).not.toHaveClass('line-clamp-3');
    expect(screen.getByRole('button', { name: 'Show less' })).toHaveAttribute('aria-expanded', 'true');
  });

  it('a long unbroken session location truncates instead of overflowing; a meeting link opens safely', () => {
    const base = { id: 1, title: 'Night sprint', scheduledAt: '2026-12-01T12:00:00Z', durationMinutes: 90,
      endsAt: '2026-12-01T13:30:00Z', agenda: null, scheduledBy: null, createdAt: '2026-11-28T00:00:00Z' };
    const { rerender } = render(<SessionCard session={{ ...base, locationOrLink: 'Room-'.repeat(60) }} />);
    expect(screen.getByText('Room-'.repeat(60))).toHaveClass('truncate');
    expect(screen.getByText(/90 min/)).toBeInTheDocument();

    rerender(<SessionCard session={{ ...base, locationOrLink: 'https://meet.google.com/abc-defg-hij' }} />);
    expect(screen.getByRole('link', { name: 'Join online' })).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('a refused tick rolls back the checkbox and the progress count', async () => {
    const source = {
      listRevisionItems: vi.fn().mockResolvedValue([
        { id: 1, title: 'A'.repeat(200), done: false, createdBy: null, completedBy: null, completedAt: null, createdAt: '2026-11-01T00:00:00Z' },
      ]),
      setRevisionItemDone: vi.fn().mockRejectedValue(new Error('Join this study circle')),
    } as unknown as StudySource;
    render(<RevisionChecklist circleId={1} source={source} />);

    const box = await screen.findByRole('checkbox');
    expect(screen.getByText('A'.repeat(200))).toHaveClass('break-words');
    fireEvent.click(box);
    expect(screen.getByText('1 of 1 revised')).toBeInTheDocument();
    expect(await screen.findByText('0 of 1 revised')).toBeInTheDocument();
    expect(box).not.toBeChecked();
  });
});
