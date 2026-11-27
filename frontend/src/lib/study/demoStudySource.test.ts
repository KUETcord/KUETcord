import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../api';
import { createDemoStudySource, MEMBERS_ONLY_MESSAGE, resetDemoStudyStore } from './demoStudySource';

const karim = { id: 2, displayName: 'Karim Rahman' };
const sadia = { id: 3, displayName: 'Sadia Afrin' };

describe('demo study source (preview store mirrors the server rules)', () => {
  beforeEach(() => resetDemoStudyStore());

  it('sessions, resources and the checklist are members-only (403) until the viewer joins', async () => {
    const source = createDemoStudySource(karim);
    await expect(source.listRevisionItems(2)).rejects.toMatchObject({ status: 403, message: MEMBERS_ONLY_MESSAGE });
    await expect(source.listSessions(2)).rejects.toBeInstanceOf(ApiError);

    const first = await source.joinCircle(2);
    const again = await source.joinCircle(2);
    expect(first).toMatchObject({ role: 'ATTENDEE', newlyJoined: true, memberCount: 3 });
    expect(again).toMatchObject({ newlyJoined: false, memberCount: 3 });
    await expect(source.listRevisionItems(2)).resolves.toHaveLength(1);
  });

  it('re-ticking an item keeps the first "ticked by", like the server', async () => {
    await createDemoStudySource(sadia).setRevisionItemDone(1, 2, true);
    const item = await createDemoStudySource(karim).setRevisionItemDone(1, 2, true);
    expect(item.completedBy?.displayName).toBe('Sadia Afrin');

    const unticked = await createDemoStudySource(karim).setRevisionItemDone(1, 2, false);
    expect(unticked).toMatchObject({ done: false, completedBy: null, completedAt: null });
  });

  it('rejects sessions in the past or outside 15-480 minutes, and non-http resource links', async () => {
    const source = createDemoStudySource(karim);
    const past = new Date(Date.now() - 60_000).toISOString();
    const future = new Date(Date.now() + 86_400_000).toISOString();
    await expect(source.scheduleSession(1, { title: 'Late', scheduledAt: past, durationMinutes: 60 }))
      .rejects.toMatchObject({ status: 400 });
    await expect(source.scheduleSession(1, { title: 'Blink', scheduledAt: future, durationMinutes: 10 }))
      .rejects.toMatchObject({ status: 400 });
    await expect(source.shareResource(1, { title: 'Bad', url: 'javascript:alert(1)', category: 'NOTES' }))
      .rejects.toMatchObject({ status: 400 });
  });

  it('only teachers, moderators and admins can publish to Campus Pulse', async () => {
    const payload = { organization: 'Dept of CSE', category: 'ACADEMIC' as const, title: 'Lab closed', body: 'Friday.' };
    await expect(createDemoStudySource(karim, ['ROLE_STUDENT', 'ROLE_CR']).publishAnnouncement(payload))
      .rejects.toMatchObject({ status: 403 });
    const teacher = createDemoStudySource({ id: 10, displayName: 'Prof. Dr. Hasan Mahmud' }, ['ROLE_TEACHER']);
    await expect(teacher.publishAnnouncement(payload)).resolves.toMatchObject({ title: 'Lab closed' });
  });
});
