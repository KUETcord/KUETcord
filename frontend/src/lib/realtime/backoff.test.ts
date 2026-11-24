import { describe, expect, it } from 'vitest';
import { BASE_DELAY_MS, MAX_DELAY_MS, computeBackoffDelay } from './backoff';

describe('computeBackoffDelay: exponential backoff with ±20% jitter', () => {
  it('doubles per attempt from 1 s and caps at 30 s (midpoint random = no jitter)', () => {
    const mid = () => 0.5;
    expect([0, 1, 2, 3, 4, 5, 6, 10].map((attempt) => computeBackoffDelay(attempt, mid))).toEqual([
      1_000, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000, 30_000,
    ]);
  });

  it('stays inside the ±20% jitter window for every attempt', () => {
    for (let attempt = 0; attempt < 12; attempt++) {
      const base = Math.min(MAX_DELAY_MS, BASE_DELAY_MS * 2 ** attempt);
      expect(computeBackoffDelay(attempt, () => 0)).toBe(Math.round(base * 0.8));
      expect(computeBackoffDelay(attempt, () => 1)).toBe(Math.round(base * 1.2));
    }
  });

  it('spreads a thundering herd: 1,000 clients on the same attempt do not retry at one instant', () => {
    const delays = Array.from({ length: 1_000 }, () => computeBackoffDelay(3));
    expect(new Set(delays).size).toBeGreaterThan(500);
    expect(Math.min(...delays)).toBeGreaterThanOrEqual(6_400);
    expect(Math.max(...delays)).toBeLessThanOrEqual(9_600);
  });

  it('is defensive against bad inputs', () => {
    expect(computeBackoffDelay(-3, () => 0.5)).toBe(1_000);
    expect(computeBackoffDelay(1_000_000, () => 0.5)).toBe(30_000);
    expect(computeBackoffDelay(0, () => Number.NaN)).toBe(1_000);
  });
});
