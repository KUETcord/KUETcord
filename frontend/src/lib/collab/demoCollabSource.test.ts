import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../api';
import { createDemoCollabSource, resetDemoCollabStore } from './demoCollabSource';

const LEAD = { id: 2, displayName: 'Karim Rahman' };
const VIEWER = { id: 10, displayName: 'Prof. Dr. Hasan Mahmud' };

async function expectApiError(promise: Promise<unknown>, status: number) {
  await expect(promise).rejects.toBeInstanceOf(ApiError);
  await promise.catch((err: ApiError) => expect(err.status).toBe(status));
}

describe('Demo collaboration store (mirrors the Spring rules)', () => {
  beforeEach(() => resetDemoCollabStore());

  it('ranks open contests by nearest deadline with closed ones last, and recent by creation', async () => {
    const source = createDemoCollabSource(LEAD);
    const byDeadline = await source.browseOpportunities({ strategy: 'deadline' });
    expect(byDeadline.items[0].title).toMatch(/ICPC/);
    expect(byDeadline.items.at(-1)?.expired).toBe(true);

    const recent = await source.browseOpportunities({ strategy: 'recent' });
    expect(recent.items[0].title).toMatch(/Research Grant/);
  });

  it('bumps the card version on every write and answers a stale version with 409', async () => {
    const source = createDemoCollabSource(LEAD);
    const moved = await source.updateTask(1, 3, { status: 'IN_PROGRESS', expectedVersion: 0 });
    expect(moved).toMatchObject({ status: 'IN_PROGRESS', version: 1 });

    await expectApiError(source.updateTask(1, 3, { status: 'DONE', expectedVersion: 0 }), 409);
    const board = await source.getBoard(1);
    expect(board.columns.find((c) => c.status === 'IN_PROGRESS')?.tasks.some((t) => t.id === 3)).toBe(true);
  });

  it('a simulated teammate edit makes the viewer\'s next write on that card conflict', async () => {
    const source = createDemoCollabSource(LEAD);
    const board = await source.getBoard(1);
    const first = board.columns.flatMap((c) => c.tasks).find((t) => t.status !== 'DONE')!;

    await source.simulateTeammateEdit(1);
    await expectApiError(source.updateTask(1, first.id, { status: 'DONE', expectedVersion: first.version }), 409);
  });

  it('enforces playground roles: a VIEWER cannot create or move cards', async () => {
    const viewer = createDemoCollabSource(VIEWER);
    expect((await viewer.getPlayground(1)).myRole).toBe('VIEWER');
    await expectApiError(viewer.createTask(1, { title: 'Sneaky' }), 403);
    await expectApiError(viewer.updateTask(1, 3, { status: 'DONE', expectedVersion: 0 }), 403);
  });

  it('starting a playground from a contest makes the caller LEAD and links the contest', async () => {
    const source = createDemoCollabSource(LEAD);
    const link = await source.startPlayground(101);
    expect(link.playground.title).toBe('ICPC Asia Dhaka Regional 2026 Team');
    expect(link.playground.myRole).toBe('LEAD');
    expect(link.playground.linkedOpportunities.map((o) => o.id)).toEqual([101]);
    expect((await source.listTeams(101)).map((t) => t.id)).toContain(link.playground.id);
  });

  it('bookmarks are idempotent per viewer and keep the shared count consistent', async () => {
    const source = createDemoCollabSource(LEAD);
    const before = (await source.getOpportunity(101)).bookmarkCount;
    await source.setBookmark(101, true);
    const twice = await source.setBookmark(101, true);
    expect(twice.bookmarkCount).toBe(before + 1);
    expect((await source.setBookmark(101, false)).bookmarkCount).toBe(before);
  });
});
