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
    expect(screen.getByText('CT Hub')).toBeInTheDocument();
    expect(screen.getByText('Core Messaging')).toBeInTheDocument();
    expect(screen.getByText('Project Playgrounds')).toBeInTheDocument();
    expect(screen.getByText('Study Circles')).toBeInTheDocument();
  });
});
