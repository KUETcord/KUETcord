import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { AuthProvider } from '../context/AuthContext';
import { computeDayOfWeek } from './TermHubPage';

function renderTermHub() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/term-hub']}>
        <AppRoutes />
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('Term Hub (/term-hub)', () => {
  it('computes exact weekday names from ISO dates', () => {
    expect(computeDayOfWeek('2026-07-12')).toBe('Sunday');
    expect(computeDayOfWeek('2026-07-15')).toBe('Wednesday');
    expect(computeDayOfWeek('2026-12-13')).toBe('Sunday');
    expect(computeDayOfWeek('')).toBe('—');
  });

  it('renders My Batch default schedule with cross-dept badge and allows CR to upload a schedule with live auto-day calculation', () => {
    renderTermHub();

    expect(screen.getByRole('heading', { name: /Term Hub/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /My Batch \(3-2 · CSE\)/i })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(
      screen.getByText(/B\.Sc\. Engineering 3rd Year 2nd Term Final Examination 2026/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/Cross-Dept \(EEE\)/i)).toBeInTheDocument();

    // Open Upload Schedule modal (default demo user is CR for CSE)
    fireEvent.click(screen.getByRole('button', { name: /Upload Schedule/i }));
    expect(screen.getByRole('heading', { name: /Upload Term Final Schedule/i })).toBeInTheDocument();

    // CR target department is locked to CSE
    const deptSelect = screen.getByLabelText(/Target Department/i) as HTMLSelectElement;
    expect(deptSelect.value).toBe('CSE');
    expect(deptSelect).toBeDisabled();

    // Fill title and exam row date -> live auto-day updates to Sunday
    fireEvent.change(screen.getByLabelText(/Schedule Title/i), {
      target: { value: '1-1 Term Final Routine 2026' },
    });
    fireEvent.change(screen.getByLabelText(/Exam row 1 date/i), {
      target: { value: '2026-07-12' },
    });
    expect(screen.getByTestId('exam-row-day-0')).toHaveTextContent('Sunday');

    // Pick cross-department EEE course from all-dept course picker
    fireEvent.change(screen.getByLabelText(/Exam row 1 course picker/i), {
      target: { value: '103' },
    });
    expect((screen.getByLabelText(/Exam row 1 course code/i) as HTMLInputElement).value).toBe(
      'EEE 3110',
    );

    fireEvent.click(screen.getByRole('button', { name: /Publish Term Schedule/i }));
    expect(screen.getByText(/1-1 Term Final Routine 2026/i)).toBeInTheDocument();
  });
});
