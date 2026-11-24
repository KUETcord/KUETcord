import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { AuthProvider } from '../context/AuthContext';

describe('Institutional Auth UI', () => {
  it('blocks non-@stud.kuet.ac.bd email during registration before API call', () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/auth']}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>,
    );

    fireEvent.click(screen.getByRole('button', { name: /^Register$/i }));

    fireEvent.change(screen.getByLabelText(/Institutional Email/i), {
      target: { value: 'outsider@gmail.com' },
    });
    fireEvent.change(screen.getByLabelText(/Full Name/i), {
      target: { value: 'External User' },
    });
    fireEvent.change(screen.getByLabelText(/Password/i), {
      target: { value: 'StrongPassword!2026' },
    });

    fireEvent.click(screen.getByRole('button', { name: /Create KUETcord Account/i }));

    expect(screen.getByRole('alert')).toHaveTextContent(/@stud\.kuet\.ac\.bd/i);
  });
});
