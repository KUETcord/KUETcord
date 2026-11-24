import { fireEvent, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { VirtuosoMockContext } from 'react-virtuoso';
import { describe, expect, it, vi } from 'vitest';
import type { ChatMessage, DeliveryState } from '../../lib/chat/messageStore';
import { VirtualizedChatFeed } from './VirtualizedChatFeed';

const ME = 1;

function msg(id: number, overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    clientKey: `k-${id}`,
    id,
    conversationId: 1,
    senderId: 2,
    senderName: 'Sadia Islam',
    content: `message ${id}`,
    createdAt: new Date(1_763_980_000_000 + id * 600_000).toISOString(),
    deleted: false,
    delivery: 'SENT' as DeliveryState,
    idempotencyKey: `k-${id}`,
    ...overrides,
  };
}

function renderFeed(messages: ChatMessage[], onRetry = vi.fn()) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <VirtuosoMockContext.Provider value={{ viewportHeight: 600, itemHeight: 40 }}>{children}</VirtuosoMockContext.Provider>
  );
  const view = render(
    <VirtualizedChatFeed
      messages={messages}
      currentUserId={ME}
      prependedCount={0}
      hasMore={false}
      onLoadOlder={vi.fn()}
      onRetry={onRetry}
      startAtBottom={false}
    />,
    { wrapper },
  );
  return { ...view, onRetry };
}

describe('VirtualizedChatFeed: break-ui & virtualization', () => {
  it('wraps a 2,000-character unbroken word inside the bubble instead of blowing out the container', () => {
    renderFeed([msg(1, { content: 'a'.repeat(2_000), senderName: 'Aleksandra Wiśniewska-Kowalczyk' })]);

    const bubble = screen.getByText('a'.repeat(2_000));
    expect(bubble.className).toContain('[overflow-wrap:anywhere]');
    expect(bubble.className).toContain('max-w-full');
    expect(bubble.parentElement?.className).toContain('min-w-0');
    expect(screen.getByTitle('Aleksandra Wiśniewska-Kowalczyk')).toHaveClass('truncate');
  });

  it('keeps avatars circular (flex-shrink: 0) and timestamps on tabular-nums', () => {
    const { container } = renderFeed([msg(1, { senderName: 'Jo' })]);
    const avatar = screen.getByText('JO');
    expect(avatar).toHaveClass('shrink-0', 'rounded-full');
    expect(container.querySelector('time')).toHaveClass('tabular-nums');
  });

  it('renders SENDING / SENT / FAILED states for own messages and Retry calls back with the clientKey', () => {
    const { onRetry } = renderFeed([
      msg(1, { senderId: ME, senderName: 'Me', delivery: 'SENT' }),
      msg(2, { senderId: ME, senderName: 'Me', id: null, clientKey: 'pending', delivery: 'SENDING', createdAt: new Date(1_763_990_000_000).toISOString() }),
      msg(3, { senderId: ME, senderName: 'Me', id: null, clientKey: 'broken', delivery: 'FAILED', createdAt: new Date(1_763_999_000_000).toISOString() }),
    ]);

    const rows = screen.getAllByTestId('chat-message');
    expect(rows.map((row) => row.dataset.delivery)).toEqual(['SENT', 'SENDING', 'FAILED']);
    expect(screen.getByText(/Sending…/)).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(/Not delivered/);

    fireEvent.click(screen.getByRole('button', { name: /Retry/i }));
    expect(onRetry).toHaveBeenCalledWith('broken');
  });

  it('virtualizes 5,000 messages: only a viewport-sized window is in the DOM', () => {
    const messages = Array.from({ length: 5_000 }, (_, i) => msg(i + 1));
    renderFeed(messages);

    const rendered = screen.getAllByTestId('chat-message').length;
    expect(rendered).toBeGreaterThan(0);
    expect(rendered).toBeLessThan(60);
  });

  it('shows a helpful empty state for a brand-new conversation', () => {
    renderFeed([]);
    expect(screen.getByText(/No messages yet/i)).toBeInTheDocument();
  });
});
