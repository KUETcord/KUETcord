import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { AuthProvider } from '../context/AuthContext';

function renderCalendar() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/calendar']}>
        <AppRoutes />
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('Academic Calendar Tab (/calendar — Google Calendar Style)', () => {
  it('defaults to My Batch (3-2 · CSE), toggles All Terms, filters by hub category, and switches Month/Week/Schedule views', () => {
    renderCalendar();

    // Heading & default My Batch chip active
    expect(screen.getByRole('heading', { name: /Academic Calendar/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /My Batch \(3-2 · CSE\)/i })).toHaveAttribute(
      'aria-pressed',
      'true',
    );

    // Switch to Schedule view to verify all 4 CSE 3-2 synced hub types appear while EEE 3-1 is filtered out
    fireEvent.click(screen.getByRole('button', { name: /^Schedule$/i }));
    expect(screen.getByText(/CT 2: State Pattern/i)).toBeInTheDocument();
    expect(screen.getByText(/Assignment 3: Distributed Rate Limiter/i)).toBeInTheDocument();
    expect(screen.getByText(/Presentation: Graph Algorithms/i)).toBeInTheDocument();
    expect(screen.getByText(/Project Showcase: KUETcord Campus Platform Viva/i)).toBeInTheDocument();
    expect(screen.queryByText(/Discrete-Time Fourier Transform/i)).not.toBeInTheDocument();

    // Uncheck Assignments -> hides Assignment 3
    fireEvent.click(screen.getByLabelText(/Assignments/i));
    expect(screen.queryByText(/Assignment 3: Distributed Rate Limiter/i)).not.toBeInTheDocument();

    // Toggle All Terms -> reveals EEE 3-1 CT
    fireEvent.click(screen.getByRole('button', { name: /^All Terms$/i }));
    expect(screen.getByText(/Discrete-Time Fourier Transform/i)).toBeInTheDocument();

    // Clicking an event opens detail modal with room & submission/submitted list links
    fireEvent.click(screen.getByText(/Project Showcase: KUETcord Campus Platform Viva/i));
    expect(screen.getByText(/Room: CSE Software Lab-2/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Submission Link/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Submitted List/i })).toBeInTheDocument();
  });
});
