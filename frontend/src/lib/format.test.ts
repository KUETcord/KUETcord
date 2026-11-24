import { describe, expect, it } from 'vitest';
import { formatSmartTimestamp, getInitials, pluralize } from './format';

describe('UI Formatting Utilities (DESIGN_GUIDELINES.md)', () => {
  it('pluralizes counts accurately using Intl.PluralRules', () => {
    expect(pluralize(0, 'CT', 'CTs')).toBe('0 CTs');
    expect(pluralize(1, 'CT', 'CTs')).toBe('1 CT');
    expect(pluralize(2, 'CT', 'CTs')).toBe('2 CTs');
    expect(pluralize(1284, 'member', 'members')).toBe('1,284 members');
  });

  it('uses relative phrasing under 7 days and absolute dates at 7+ days', () => {
    const now = new Date('2026-11-24T10:00:00Z');
    const inTwoDays = '2026-11-26T10:00:00Z';
    const inThreeWeeks = '2026-12-19T10:00:00Z';

    expect(formatSmartTimestamp(inTwoDays, now)).toMatch(/in 2 days/i);
    expect(formatSmartTimestamp(inThreeWeeks, now)).toBe('Dec 19, 2026');
  });

  it('derives initials safely for short, hyphenated, and empty names', () => {
    expect(getInitials('Jo')).toBe('JO');
    expect(getInitials('Aleksandra Wiśniewska-Kowalczyk')).toBe('AW');
    expect(getInitials('   ')).toBe('?');
  });
});
