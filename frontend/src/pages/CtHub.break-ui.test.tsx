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

describe('CT Hub UI, Teacher Approval RBAC & Adversarial break-ui Stress Tests', () => {
  it('renders worst-case 80+ char course titles, long Unicode teacher names, and 1-word names without crashing', () => {
    renderAt('/ct-hub?data=worst');

    expect(
      screen.getAllByText(/CSE 3103: Advanced Algorithms, Graph Traversals, Dynamic Programming & NP-Completeness/i)
        .length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText(/Prof\. Dr\. Aleksandra Wiśniewska-Kowalczyk/i).length).toBeGreaterThan(0);
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

  it('enforces CR propose + Teacher approve + Student read-only RBAC and searchable teacher selection', () => {
    renderAt('/ct-hub?data=demo');

    // Default demo role is CR: can Propose/Reschedule/Cancel, CANNOT Approve
    expect(screen.getByRole('button', { name: /Propose CT/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Approve$/i })).not.toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /^Reschedule$/i }).length).toBeGreaterThan(0);

    // Open Propose CT modal and search for a teacher by name
    fireEvent.click(screen.getByRole('button', { name: /Propose CT/i }));
    const searchInput = screen.getByPlaceholderText(/Search teacher by name or email/i);
    fireEvent.change(searchInput, { target: { value: 'Farhana' } });
    expect(screen.getByText(/Dr\. Farhana Yasmin/i)).toBeInTheDocument();
    expect(screen.queryByText(/Prof\. Dr\. Rafiqul Islam/i)).not.toBeInTheDocument();

    // Close modal
    fireEvent.click(screen.getByRole('button', { name: /Close modal/i }));

    // Switch preview role to TEACHER -> Approve buttons appear for assigned teacher
    fireEvent.click(screen.getByRole('button', { name: /^Teacher$/i }));
    const approveButtons = screen.getAllByRole('button', { name: /^Approve$/i });
    expect(approveButtons.length).toBe(2);
    fireEvent.click(approveButtons[0]);
    expect(screen.getAllByRole('button', { name: /^Approve$/i }).length).toBe(1);

    // Switch preview role to STUDENT -> Propose, Approve, Reschedule & Cancel are all hidden
    fireEvent.click(screen.getByRole('button', { name: /^Student$/i }));
    expect(screen.queryByRole('button', { name: /Propose CT/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Approve$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Reschedule$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Cancel$/i })).not.toBeInTheDocument();
  });
});
