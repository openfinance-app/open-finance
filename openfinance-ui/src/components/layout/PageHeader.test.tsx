import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { PageHeader } from './PageHeader';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

describe('PageHeader', () => {
  beforeEach(() => {
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders title', () => {
    renderWithProviders(<PageHeader title="Transactions" />);
    expect(
      screen.getByRole('heading', { name: 'Transactions' })
    ).toBeInTheDocument();
  });

  it('renders description when provided', () => {
    renderWithProviders(
      <PageHeader title="Budget" description="Manage your monthly budgets" />
    );
    expect(screen.getByText('Manage your monthly budgets')).toBeInTheDocument();
  });

  it('does not render description when not provided', () => {
    renderWithProviders(<PageHeader title="Budget" />);
    const heading = screen.getByRole('heading', { name: 'Budget' });
    const sibling = heading.nextElementSibling;
    // If there's no description, no <p> tag sibling
    expect(sibling).toBeNull();
  });

  it('renders action buttons when provided', () => {
    renderWithProviders(
      <PageHeader
        title="Assets"
        actions={<button>Add Asset</button>}
      />
    );
    expect(screen.getByRole('button', { name: 'Add Asset' })).toBeInTheDocument();
  });

  it('applies custom className', () => {
    const { container } = renderWithProviders(
      <PageHeader title="Test" className="my-class" />
    );
    expect(container.querySelector('.my-class')).not.toBeNull();
  });

  it('renders ReactNode description', () => {
    renderWithProviders(
      <PageHeader
        title="Dashboard"
        description={<span data-testid="custom-desc">Custom JSX</span>}
      />
    );
    expect(screen.getByTestId('custom-desc')).toBeInTheDocument();
  });
});
