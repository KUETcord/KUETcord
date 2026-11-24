import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { AuthProvider } from '../context/AuthContext';

function renderAt(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('CT Hub UI & Adversarial break-ui Stress Tests', () => {
  it('renders worst-case 80+ char course titles, long Unicode names, and 1-word names without crashing', () => {
    renderAt('/ct-hub?data=worst');

    expect(
      screen.getAllByText(/CSE 3103: Advanced Algorithms, Graph Traversals, Dynamic Programming & NP-Completeness/i)
        .length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText(/Aleksandra Wiśniewska-Kowalczyk/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/^Jo$/i).length).toBeGreaterThan(0);
  });

  it('renders empty state gracefully when zero Class Tests are scheduled', () => {
    renderAt('/ct-hub?data=empty');

    expect(screen.getByText(/No upcoming Class Tests scheduled/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Propose First CT/i })).toBeInTheDocument();
    expect(screen.getByText(/0 CTs/i)).toBeInTheDocument();
  });

  it('uses exact singular pluralization for single-item fixture', () => {
    renderAt('/ct-hub?data=one');

    expect(screen.getByText(/1 course/i)).toBeInTheDocument();
    expect(screen.getByText(/1 CT\)/i)).toBeInTheDocument();
  });

  it('allows CR to verify an UNVERIFIED CT and hides moderation controls when switched to STUDENT', () => {
    renderAt('/ct-hub?data=demo');

    const verifyButtonsBefore = screen.getAllByRole('button', { name: /^Verify$/i });
    expect(verifyButtonsBefore.length).toBe(2); // UNVERIFIED + RESCHEDULED

    fireEvent.click(verifyButtonsBefore[0]);
    expect(screen.getAllByRole('button', { name: /^Verify$/i }).length).toBe(1);

    // Switch preview role to STUDENT -> Verify, Reschedule & Cancel controls are hidden
    fireEvent.click(screen.getByRole('button', { name: /Demo: Student/i }));
    expect(screen.queryByRole('button', { name: /^Verify$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Reschedule$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Cancel$/i })).not.toBeInTheDocument();
  });
});
