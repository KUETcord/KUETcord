import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PomodoroTimer } from './PomodoroTimer';

describe('<PomodoroTimer />', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('runs a 25-minute focus block, then announces the break politely', () => {
    render(<PomodoroTimer />);
    const timer = screen.getByRole('timer');
    expect(timer).toHaveTextContent('25:00');
    expect(timer).toHaveClass('tabular-nums');

    fireEvent.click(screen.getByRole('button', { name: /Start/ }));
    act(() => { vi.advanceTimersByTime(60_000); });
    expect(timer).toHaveTextContent('24:00');

    act(() => { vi.advanceTimersByTime(24 * 60_000); });
    expect(screen.getByTestId('pomodoro-phase')).toHaveTextContent('Short break');
    expect(timer).toHaveTextContent('05:00');
    expect(screen.getByText('Focus block done. Time for a 5-minute break.')).toBeInTheDocument();
    expect(screen.getByText(/1 focus block done/)).toBeInTheDocument();
  });

  it('pause freezes the clock and reset restores the full block', () => {
    render(<PomodoroTimer />);
    fireEvent.click(screen.getByRole('button', { name: /Start/ }));
    act(() => { vi.advanceTimersByTime(90_000); });
    fireEvent.click(screen.getByRole('button', { name: /Pause/ }));

    act(() => { vi.advanceTimersByTime(10 * 60_000); });
    expect(screen.getByRole('timer')).toHaveTextContent('23:30');

    fireEvent.click(screen.getByRole('button', { name: 'Reset timer' }));
    expect(screen.getByRole('timer')).toHaveTextContent('25:00');
  });

  it('every control is a labelled button reachable by keyboard', () => {
    render(<PomodoroTimer />);
    for (const name of [/Start/, /Reset timer/, /Skip to break/]) {
      const button = screen.getByRole('button', { name });
      button.focus();
      expect(button).toHaveFocus();
    }
    fireEvent.click(screen.getByRole('button', { name: 'Skip to break' }));
    expect(screen.getByRole('button', { name: 'Skip to focus' })).toBeInTheDocument();
  });
});
