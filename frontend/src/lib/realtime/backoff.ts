/**
 * Exponential backoff with ±20 % randomized jitter for WebSocket reconnects.
 *
 *   delay(attempt) = min(30 s, 1 s × 2^attempt) × (0.8 + 0.4 × random)
 *
 * Why jitter: if the backend restarts, every open tab loses its socket at the same instant.
 * Pure exponential backoff would make all of them retry at exactly 1 s, 2 s, 4 s, … (a
 * thundering herd hitting the freshly booted 512 MB instance in lockstep). Jitter spreads each
 * wave over a ±20 % window so reconnects arrive as a smooth ramp instead of spikes.
 */
export const BASE_DELAY_MS = 1_000;
export const MAX_DELAY_MS = 30_000;
export const JITTER_RATIO = 0.2;

export function computeBackoffDelay(attempt: number, random: () => number = Math.random): number {
  const safeAttempt = Math.max(0, Math.floor(attempt));
  // Cap the exponent too, so very large attempt counts cannot overflow to Infinity.
  const exponential = Math.min(MAX_DELAY_MS, BASE_DELAY_MS * 2 ** Math.min(safeAttempt, 16));
  const jitterFactor = 1 - JITTER_RATIO + 2 * JITTER_RATIO * clamp01(random());
  return Math.round(exponential * jitterFactor);
}

function clamp01(value: number): number {
  if (Number.isNaN(value)) return 0.5;
  return Math.min(1, Math.max(0, value));
}
