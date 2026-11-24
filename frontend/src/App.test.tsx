import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import App from './App';

describe('App Shell Smoke Test', () => {
  it('renders KUETcord heading and navigation brand', () => {
    render(<App />);
    const headingElements = screen.getAllByText(/KUETcord/i);
    expect(headingElements.length).toBeGreaterThan(0);
  });

  it('renders all 4 core module cards', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'CT Hub' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Core Messaging' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Project Playgrounds' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Study Circles' })).toBeInTheDocument();
  });
});
