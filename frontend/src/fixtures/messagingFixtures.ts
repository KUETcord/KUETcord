import type { ChatMessageDto, ConversationMember, ConversationSummary } from '../types/messaging';

/**
 * Demo / break-ui fixtures for Core Messaging (dev preview only; selected with ?data=).
 *   demo   realistic KUET conversations
 *   worst  80-char emoji title, 2,000-char unbroken word, long Unicode name, "Jo", 1,284 unread
 *   empty  no conversations at all
 *   one    a single conversation with exactly 1 member and 1 message (pluralization)
 *   huge   5,000 messages in one conversation (virtualization stress)
 */
export type ChatFixtureMode = 'demo' | 'worst' | 'empty' | 'one' | 'huge';

export interface ChatFixture {
  conversations: ConversationSummary[];
  members: Record<number, ConversationMember[]>;
  messages: Record<number, ChatMessageDto[]>;
}

const NOW = Date.parse('2026-11-24T10:15:00Z');
const minutesAgo = (m: number) => new Date(NOW - m * 60_000).toISOString();

function member(userId: number, displayName: string, username: string, groupRole: ConversationMember['groupRole'] = 'GROUP_MEMBER'): ConversationMember {
  return { userId, displayName, username, groupRole, joinedAt: minutesAgo(10_000) };
}

function message(id: number, conversationId: number, senderId: number, senderName: string, content: string, minutes: number): ChatMessageDto {
  return {
    id,
    conversationId,
    senderId,
    senderName,
    content,
    idempotencyKey: `fixture-${conversationId}-${id}`,
    createdAt: minutesAgo(minutes),
    editedAt: null,
    deleted: false,
  };
}

function summary(id: number, type: ConversationSummary['type'], title: string | null, displayTitle: string, memberCount: number,
                 unreadCount: number, minutes: number, peerUserId: number | null = null): ConversationSummary {
  return { id, type, title, displayTitle, peerUserId, memberCount, unreadCount, updatedAt: minutesAgo(minutes) };
}

export function buildChatFixture(mode: ChatFixtureMode, me: { id: number; displayName: string }): ChatFixture {
  const meMember = member(me.id, me.displayName, 'me', 'GROUP_OWNER');

  if (mode === 'empty') {
    return { conversations: [], members: {}, messages: {} };
  }

  if (mode === 'one') {
    return {
      conversations: [summary(1, 'GROUP_DM', 'Solo notes', 'Solo notes', 1, 1, 3)],
      members: { 1: [meMember] },
      messages: { 1: [message(1, 1, me.id, me.displayName, 'Reminder: revise DFA minimization before CT-2.', 3)] },
    };
  }

  if (mode === 'huge') {
    const messages: ChatMessageDto[] = [];
    for (let i = 1; i <= 5_000; i++) {
      const fromMe = i % 3 === 0;
      messages.push(message(i, 1, fromMe ? me.id : 102, fromMe ? me.displayName : 'Sadia Islam',
        `Message #${i}: discussing Kanban column limits and CT-${(i % 4) + 1} revision plan.`, 5_000 - i));
    }
    return {
      conversations: [summary(1, 'CHANNEL', 'cse-3103-chat', '#cse-3103-chat', 214, 5_000, 0)],
      members: { 1: [meMember, member(102, 'Sadia Islam', 'sadia')] },
      messages: { 1: messages },
    };
  }

  if (mode === 'worst') {
    const longTitle = '🎓📚 CSE 3220 Software Engineering Lab — Final Viva Prep, Demo Rehearsal & Docs 🚀🔥';
    return {
      conversations: [
        summary(1, 'GROUP_DM', longTitle, longTitle, 25, 1_284, 1),
        summary(2, 'DIRECT', null, 'Aleksandra Wiśniewska-Kowalczyk', 2, 1, 4, 201),
        summary(3, 'DIRECT', null, 'Jo', 2, 0, 60 * 24 * 9, 202),
      ],
      members: {
        1: [meMember, member(201, 'Aleksandra Wiśniewska-Kowalczyk', 'aleksandra.wk'), member(202, 'Jo', 'jo')],
        2: [meMember, member(201, 'Aleksandra Wiśniewska-Kowalczyk', 'aleksandra.wk')],
        3: [meMember, member(202, 'Jo', 'jo')],
      },
      messages: {
        1: [
          message(1, 1, 201, 'Aleksandra Wiśniewska-Kowalczyk', 'a'.repeat(2_000), 30),
          message(2, 1, 202, 'Jo', 'ok', 20),
          message(3, 1, me.id, me.displayName,
            'Link: https://github.com/KUETcord/KUETcord/pull/7/files#diff-websocketconfig-heartbeat-negotiation-very-long-anchor', 10),
          message(4, 1, 201, 'Aleksandra Wiśniewska-Kowalczyk', '😀'.repeat(300), 1),
        ],
        2: [message(5, 2, 201, 'Aleksandra Wiśniewska-Kowalczyk', 'Cześć! Can you share the CT-2 notes?', 4)],
        3: [],
      },
    };
  }

  // demo
  return {
    conversations: [
      summary(1, 'GROUP_DM', 'CSE 3220 Software Engineering Lab Team', 'CSE 3220 Software Engineering Lab Team', 3, 2, 2),
      summary(2, 'DIRECT', null, 'Sadia Islam', 2, 0, 45, 102),
      summary(3, 'CHANNEL', 'cse-3103-chat', '#cse-3103-chat', 48, 0, 60 * 26),
    ],
    members: {
      1: [meMember, member(102, 'Sadia Islam', 'sadia', 'GROUP_ADMIN'), member(103, 'Rahim Ahmed', 'rahim')],
      2: [meMember, member(102, 'Sadia Islam', 'sadia')],
      3: [meMember, member(102, 'Sadia Islam', 'sadia'), member(103, 'Rahim Ahmed', 'rahim')],
    },
    messages: {
      1: [
        message(1, 1, me.id, me.displayName, 'Hey team, did everyone clone the KUETcord repo?', 15),
        message(2, 1, 102, 'Sadia Islam', 'Yes! Docker compose builds cleanly and PostgreSQL starts up.', 12),
        message(3, 1, 103, 'Rahim Ahmed', 'Tested the STOMP WebSocket connection too. Heartbeats are running 10s/10s.', 8),
        message(4, 1, 102, 'Sadia Islam', 'Hey team, the revision notes for CT 2 are ready! @rahim can you review?', 2),
      ],
      2: [
        message(5, 2, 102, 'Sadia Islam', 'Can someone share the link?', 50),
        message(6, 2, me.id, me.displayName, 'Posted in the study circle resources!', 45),
      ],
      3: [message(7, 3, 103, 'Rahim Ahmed', 'Microprocessors CT-2 moved to Thursday 10 AM, room 204.', 60 * 26)],
    },
  };
}

export function parseChatFixtureMode(search: string): ChatFixtureMode {
  const value = new URLSearchParams(search).get('data');
  return value === 'worst' || value === 'empty' || value === 'one' || value === 'huge' ? value : 'demo';
}
