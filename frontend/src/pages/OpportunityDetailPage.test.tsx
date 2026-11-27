import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { resetDemoCollabStore } from '../lib/collab/demoCollabSource';
import { renderApp } from '../test/renderApp';

describe('Opportunity detail & team formation (Week 4, Day 2)', () => {
  beforeEach(() => resetDemoCollabStore());

  it('shows eligibility, the official source link (new tab, no opener) and the teams already formed', async () => {
    renderApp('/opportunities/102');

    expect(await screen.findByRole('heading', { name: 'KUET Hackathon 2026: Tech for Impact' })).toBeInTheDocument();
    expect(screen.getByText('Undergraduate students, all departments')).toBeInTheDocument();
    const source = screen.getByRole('link', { name: /Official source/i });
    expect(source).toHaveAttribute('href', 'https://hackathon.kuet.ac.bd');
    expect(source).toHaveAttribute('target', '_blank');
    expect(source).toHaveAttribute('rel', 'noopener noreferrer');
    expect(await screen.findByText('Team KUETcord: Campus Workflow System')).toBeInTheDocument();
    expect(screen.getByText('(1 team)')).toBeInTheDocument();
  });

  it('"Start Project Playground" creates a linked team workspace and opens its board', async () => {
    renderApp('/opportunities/101');
    fireEvent.click(await screen.findByRole('button', { name: /Start Project Playground/i }));

    expect(await screen.findByRole('heading', { name: 'ICPC Asia Dhaka Regional 2026 Team' })).toBeInTheDocument();
    expect(screen.getByText('Formed for')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'ICPC Asia Dhaka Regional 2026' })).toHaveAttribute('href', '/opportunities/101');
    expect(await screen.findByText('No tasks in backlog')).toBeInTheDocument();
    expect(screen.getByText('LEAD')).toBeInTheDocument();
  });

  it('disables team creation once registration has closed', async () => {
    renderApp('/opportunities/105');
    expect(await screen.findByRole('button', { name: /Start Project Playground/i })).toBeDisabled();
  });

  it('shows a friendly message for an unknown opportunity', async () => {
    renderApp('/opportunities/999');
    expect(await screen.findByText(/does not exist or was removed/i)).toBeInTheDocument();
  });
});
