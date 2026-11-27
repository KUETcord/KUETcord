import { useMemo } from 'react';
import { useAuth } from '../context/AuthContext';
import { liveCollabSource, type CollabSource } from '../lib/collab/collabSource';
import { createDemoCollabSource, type DemoCollabSource } from '../lib/collab/demoCollabSource';

export interface CollabSourceHandle {
  source: CollabSource;
  /** Present only in preview mode: lets a demo provoke a real 409 from a "teammate". */
  demo: DemoCollabSource | null;
  viewerId: number | null;
}

/**
 * Picks the data source for Member C's screens: the Spring API when signed in, the in-memory demo store
 * (scoped to the preview role) in demo mode. Switching preview role re-scopes the store, so a Teacher sees
 * the board as a read-only VIEWER and a Student as a CONTRIBUTOR.
 */
export function useCollabSource(): CollabSourceHandle {
  const { user, isDemoMode } = useAuth();
  const viewerId = user?.id ?? null;
  const displayName = user?.displayName ?? 'Guest';

  return useMemo(() => {
    if (isDemoMode && viewerId !== null) {
      const demo = createDemoCollabSource({ id: viewerId, displayName });
      return { source: demo, demo, viewerId };
    }
    return { source: liveCollabSource, demo: null, viewerId };
  }, [isDemoMode, viewerId, displayName]);
}

export function errorMessage(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}
