import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EmptyState } from './EmptyState';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { Search } from 'lucide-react';

describe('EmptyState', () => {
  beforeEach(() => {
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders title', () => {
    renderWithProviders(<EmptyState title="No results found" />);
    expect(screen.getByText('No results found')).toBeInTheDocument();
  });

  it('renders description when provided', () => {
    renderWithProviders(
      <EmptyState title="Empty" description="Try adjusting your filters" />
    );
    expect(screen.getByText('Try adjusting your filters')).toBeInTheDocument();
  });

  it('does not render description when not provided', () => {
    renderWithProviders(<EmptyState title="Empty" />);
    expect(screen.queryByText('Try adjusting')).not.toBeInTheDocument();
  });

  it('renders icon when provided', () => {
    renderWithProviders(<EmptyState title="Empty" icon={Search} />);
    // The icon should be rendered (lucide renders SVG)
    const container = screen.getByText('Empty').closest('div')!.parentElement!;
    expect(container.querySelector('svg')).not.toBeNull();
  });

  it('renders action button when provided', async () => {
    const handleClick = vi.fn();
    const user = userEvent.setup();

    renderWithProviders(
      <EmptyState
        title="Empty"
        action={{ label: 'Add Item', onClick: handleClick }}
      />
    );

    const button = screen.getByRole('button', { name: 'Add Item' });
    expect(button).toBeInTheDocument();

    await user.click(button);
    expect(handleClick).toHaveBeenCalledOnce();
  });

  it('renders children content', () => {
    renderWithProviders(
      <EmptyState title="Empty">
        <span data-testid="custom-child">Custom content</span>
      </EmptyState>
    );
    expect(screen.getByTestId('custom-child')).toBeInTheDocument();
  });

  it('applies custom className', () => {
    const { container } = renderWithProviders(
      <EmptyState title="Empty" className="my-custom-class" />
    );
    expect(container.querySelector('.my-custom-class')).not.toBeNull();
  });
});
