import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { AuthProvider } from '../context/AuthContext';

function renderRoute(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('Labtest Hub (/labtest-hub), Quiz Hub (/quiz-hub), and Calendar Sync', () => {
  it('renders /labtest-hub with type-specific assessments, My Batch default, and lab room placeholder', () => {
    renderRoute('/labtest-hub');

    expect(screen.getByRole('heading', { name: /Labtest Hub/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /My Batch \(3-2 · CSE\)/i })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByText(/CSE 3220 Lab Test 1/i)).toBeInTheDocument();
    expect(screen.getByText(/EEE 1108 Lab Test/i)).toBeInTheDocument();
    expect(screen.queryByText(/CT 2: State Pattern/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/CSE 3101 Quiz 2/i)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Propose Lab Test/i }));
    expect(screen.getByRole('heading', { name: /Propose Lab Test Schedule/i })).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText(/Lab Room \(e\.g\., CSE Software Lab-1\)/i),
    ).toBeInTheDocument();
  });

  it('renders /quiz-hub with type-specific assessments, My Batch default, and quiz room placeholder', () => {
    renderRoute('/quiz-hub');

    expect(screen.getByRole('heading', { name: /Quiz Hub/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /My Batch \(3-2 · CSE\)/i })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByText(/CSE 3101 Quiz 2/i)).toBeInTheDocument();
    expect(screen.getByText(/MATH 2107 Quiz 1/i)).toBeInTheDocument();
    expect(screen.queryByText(/CT 2: State Pattern/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/CSE 3220 Lab Test 1/i)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Propose Quiz/i }));
    expect(screen.getByRole('heading', { name: /Propose Quiz Schedule/i })).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Room \(e\.g\., CSE-204\)/i)).toBeInTheDocument();
  });

  it('syncs Lab Tests and Quizzes into /calendar with working category toggle checkboxes', () => {
    renderRoute('/calendar');

    fireEvent.click(screen.getByRole('button', { name: /^Schedule$/i }));
    expect(screen.getByText(/CSE 3220 Lab Test 1/i)).toBeInTheDocument();
    expect(screen.getByText(/CSE 3101 Quiz 2/i)).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText(/Lab Tests/i));
    expect(screen.queryByText(/CSE 3220 Lab Test 1/i)).not.toBeInTheDocument();

    fireEvent.click(screen.getByLabelText(/Quizzes/i));
    expect(screen.queryByText(/CSE 3101 Quiz 2/i)).not.toBeInTheDocument();
  });
});
