import { useMemo } from 'react';
import { useAuth } from '../context/AuthContext';
import { createDemoStudySource } from '../lib/study/demoStudySource';
import { liveStudySource, type StudySource } from '../lib/study/studySource';

export interface StudySourceHandle {
  source: StudySource;
  demo: boolean;
  viewerId: number | null;
  /** Teachers, moderators and admins may publish to Campus Pulse (mirrors the @PreAuthorize rule). */
  canPublish: boolean;
}

/** Spring API when signed in, the preview store scoped to the preview role otherwise. */
export function useStudySource(): StudySourceHandle {
  const { user, isDemoMode } = useAuth();
  const viewerId = user?.id ?? null;
  const displayName = user?.displayName ?? 'Guest';
  const roles = user?.roles;

  return useMemo(() => {
    const roleList = roles ?? [];
    const canPublish = roleList.some((r) => ['ROLE_TEACHER', 'ROLE_MODERATOR', 'ROLE_ADMIN'].includes(r));
    if (isDemoMode && viewerId !== null) {
      return { source: createDemoStudySource({ id: viewerId, displayName }, roleList), demo: true, viewerId, canPublish };
    }
    return { source: liveStudySource, demo: false, viewerId, canPublish };
  }, [isDemoMode, viewerId, displayName, roles]);
}
