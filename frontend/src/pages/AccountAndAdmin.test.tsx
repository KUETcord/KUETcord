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

describe('Phase 7: My Account, Admin Governance CRUD, Year–Term Default Filter & CT Exam Room', () => {
  it('activates My Batch (3-2 · CSE) by default on CT Hub, renders examRoom, and reveals other terms when toggling All Terms', () => {
    renderAt('/ct-hub?data=demo');

    // Default user is CR (3-2 · CSE, roll 2007001) -> My Batch is active by default, hiding EEE 3110 (3-1)
    expect(screen.getByRole('button', { name: /My Batch \(3-2 · CSE\)/i })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    // Multi-room roll ranges & combined 2 teachers render on CT card, highlighting student's room (Roll 1-40: CSE-101)
    expect(screen.getByText(/CSE-101/i)).toBeInTheDocument();
    expect(screen.getByText(/CSE-105/i)).toBeInTheDocument();
    expect(screen.getByText(/CSE-102/i)).toBeInTheDocument();
    expect(screen.getByText(/Your Room/i)).toBeInTheDocument();
    expect(screen.getByText(/Prof\. Dr\. Hasan Mahmud & Dr\. Farhana Yasmin/i)).toBeInTheDocument();
    expect(screen.queryByText(/Discrete-Time Fourier Transform/i)).not.toBeInTheDocument();

    // Clicking "All Terms" reveals EEE 3110 (3-1) as well
    fireEvent.click(screen.getByRole('button', { name: /^All Terms$/i }));
    expect(screen.getByText(/Discrete-Time Fourier Transform/i)).toBeInTheDocument();
  });

  it('allows updating Year–Term & profile fields on My Account (/account)', () => {
    renderAt('/account');

    expect(screen.getByRole('heading', { name: /My Account/i })).toBeInTheDocument();
    const ytSelect = screen.getByLabelText(/Current Year & Term/i) as HTMLSelectElement;
    expect(ytSelect.value).toBe('3-2');

    fireEvent.change(ytSelect, { target: { value: '4-1' } });
    fireEvent.click(screen.getByRole('button', { name: /Save Account Changes/i }));

    expect((screen.getByLabelText(/Current Year & Term/i) as HTMLSelectElement).value).toBe('4-1');
  });

  it('enforces Moderator/Admin guard on /admin and supports direct Teacher provisioning + Course creation', () => {
    renderAt('/admin');

    // Default CR preview user sees RBAC guard
    expect(screen.getByText(/Moderator or Admin Authority Required/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Switch Preview to Admin/i }));

    // Now inside Governance Console: add a new course offering
    expect(screen.getByRole('heading', { name: /Governance Console/i })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Add Course/i }));
    fireEvent.change(screen.getByLabelText(/Course Code/i), { target: { value: 'CSE 4105' } });
    fireEvent.change(screen.getByLabelText(/Course Title/i), { target: { value: 'Computer Networks' } });
    fireEvent.click(screen.getByRole('button', { name: /Create Course/i }));
    expect(screen.getByText('CSE 4105')).toBeInTheDocument();

    // Switch to Teachers & Users tab and provision a Teacher directly
    fireEvent.click(screen.getByRole('button', { name: /Teachers & Users/i }));
    fireEvent.click(screen.getByRole('button', { name: /Add Teacher Directly/i }));
    fireEvent.change(screen.getByLabelText(/Faculty Full Name/i), {
      target: { value: 'Dr. Aminul Islam' },
    });
    fireEvent.change(screen.getByLabelText(/KUET Faculty Email/i), {
      target: { value: 'aminul@cse.kuet.ac.bd' },
    });
    fireEvent.change(screen.getByLabelText(/Initial Password/i), {
      target: { value: 'FacultyPass123!' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Provision Teacher/i }));

    expect(screen.getByText('Dr. Aminul Islam')).toBeInTheDocument();
  });
});
