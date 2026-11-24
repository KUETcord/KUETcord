import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

const pluralRules = new Intl.PluralRules('en-US');

/**
 * Pluralizes countable labels using Intl.PluralRules so UI never displays "1 CTs" or "1 followers".
 */
export function pluralize(count: number, singular: string, plural: string): string {
  const category = pluralRules.select(count);
  const formatted = new Intl.NumberFormat('en-US').format(count);
  return `${formatted} ${category === 'one' ? singular : plural}`;
}

/**
 * Formats timestamps per DESIGN_GUIDELINES.md:
 * - Within 7 days: relative phrasing ("in 2 days", "3 hours ago")
 * - 7 days or further: crisp absolute date ("Nov 28, 2026")
 */
export function formatSmartTimestamp(isoDate: string, now: Date = new Date()): string {
  const target = new Date(isoDate);
  if (Number.isNaN(target.getTime())) {
    return 'Invalid date';
  }

  const diffMs = target.getTime() - now.getTime();
  const absDiffMs = Math.abs(diffMs);
  const sevenDaysMs = 7 * 24 * 60 * 60 * 1000;

  if (absDiffMs >= sevenDaysMs) {
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    }).format(target);
  }

  const rtf = new Intl.RelativeTimeFormat('en-US', { numeric: 'auto' });
  const diffMinutes = Math.round(diffMs / (60 * 1000));
  if (Math.abs(diffMinutes) < 60) {
    return rtf.format(diffMinutes, 'minute');
  }
  const diffHours = Math.round(diffMs / (60 * 60 * 1000));
  if (Math.abs(diffHours) < 24) {
    return rtf.format(diffHours, 'hour');
  }
  const diffDays = Math.round(diffMs / (24 * 60 * 60 * 1000));
  return rtf.format(diffDays, 'day');
}

export function formatExactDateTime(isoDate: string): string {
  const target = new Date(isoDate);
  if (Number.isNaN(target.getTime())) {
    return '—';
  }
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(target);
}

export function getInitials(displayName: string): string {
  const trimmed = displayName.trim();
  if (!trimmed) return '?';
  const parts = trimmed.split(/\s+/);
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}
