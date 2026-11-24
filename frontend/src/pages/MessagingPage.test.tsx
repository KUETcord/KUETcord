import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../App';
import { AuthProvider } from '../context/AuthContext';
import { RealtimeProvider } from '../context/RealtimeContext';

// Page-level behaviour only: render every row. Windowing itself is covered by VirtualizedChatFeed.test.tsx.
vi.mock('react-virtuoso', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-virtuoso')>();
  return {
    ...actual,
    Virtuoso: ({ data, itemContent, firstItemIndex = 0 }: {
      data: unknown[];
      itemContent: (index: number, item: unknown) => React.ReactNode;
      firstItemIndex?: number;
    }) => <div>{data.map((item, i) => <div key={i}>{itemContent(firstItemIndex + i, item)}</div>)}</div>,
  };
});

function renderAt(path: string) {
  return render(
    <AuthProvider>
      <RealtimeProvider>
        <MemoryRouter initialEntries={[path]}>
          <AppRoutes />
        </MemoryRouter>
      </RealtimeProvider>
    </AuthProvider>,
  );
}

beforeAll(() => {
  // jsdom lacks ResizeObserver/matchMedia, which react-virtuoso and the responsive layout use.
  globalThis.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
  window.matchMedia ??= ((query: string) => ({
    matches: true, media: query, onchange: null,
    addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {}, dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
});

afterEach(() => {
  vi.useRealTimers();
});

describe('Core Messaging page (demo preview, no backend)', () => {
  it('replaces the module placeholder with the conversation list and an open chat', () => {
    renderAt('/messaging?data=demo');

    const list = screen.getByRole('list', { name: /Conversations/i });
    expect(within(list).getByText('CSE 3220 Software Engineering Lab Team')).toBeInTheDocument();
    expect(within(list).getByText('Sadia Islam')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(/Demo preview/i);
    expect(screen.getAllByText('3 members').length).toBeGreaterThan(0);
  });

  it('sends optimistically: SENDING immediately, then SENT after the simulated commit', () => {
    vi.useFakeTimers();
    renderAt('/messaging?data=demo');

    const composer = screen.getByLabelText('Message');
    fireEvent.change(composer, { target: { value: 'Pushing the STOMP PR now' } });
    fireEvent.keyDown(composer, { key: 'Enter' });

    expect(screen.getByText(/Sending…/)).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(400);
    });
    expect(screen.queryByText(/Sending…/)).not.toBeInTheDocument();
    expect(screen.getAllByLabelText('Sent').length).toBeGreaterThan(0);
  });

  it('survives worst-case data: 80-char emoji title (with tooltip), 1,284 unread -> 99+, long Unicode names', () => {
    renderAt('/messaging?data=worst');

    const title = '🎓📚 CSE 3220 Software Engineering Lab — Final Viva Prep, Demo Rehearsal & Docs 🚀🔥';
    const listEntry = within(screen.getByRole('list', { name: /Conversations/i })).getByTitle(title);
    expect(listEntry).toHaveClass('truncate');
    expect(screen.getByLabelText('1284 unread')).toHaveTextContent('99+');
    expect(screen.getByLabelText('1284 unread')).toHaveClass('tabular-nums');
    expect(screen.getAllByTitle('Aleksandra Wiśniewska-Kowalczyk').every((el) => el.classList.contains('truncate'))).toBe(true);
    expect(screen.getByText('a'.repeat(2_000))).toHaveClass('[overflow-wrap:anywhere]');
  });

  it('pluralizes exactly and renders helpful empty states', () => {
    const { unmount } = renderAt('/messaging?data=one');
    expect(screen.getAllByText('1 member').length).toBeGreaterThan(0);
    expect(screen.getByText('1 conversation · 1 unread message')).toBeInTheDocument();
    unmount();

    renderAt('/messaging?data=empty');
    expect(screen.getByText(/No conversations yet/i)).toBeInTheDocument();
    expect(screen.getByText('0 conversations')).toBeInTheDocument();
  });

  it('blocks sends over 2,000 characters and shows a tabular counter', () => {
    renderAt('/messaging?data=demo');
    fireEvent.change(screen.getByLabelText('Message'), { target: { value: 'x'.repeat(2_001) } });

    expect(screen.getByRole('button', { name: /Send message/i })).toBeDisabled();
    expect(screen.getByText('2,001 / 2,000')).toHaveClass('tabular-nums');
  });
});
