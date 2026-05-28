import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import EarlyPayoffPage from './EarlyPayoffPage';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/components/early-payoff/EarlyPayoffCalculator', () => ({
  EarlyPayoffCalculator: () => (
    <div data-testid="early-payoff-calculator">Early Payoff Calculator</div>
  ),
}));

vi.mock('@/hooks/useDocumentTitle', () => ({
  useDocumentTitle: vi.fn(),
}));

describe('EarlyPayoffPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders page header', () => {
    renderWithProviders(<EarlyPayoffPage />);
    expect(screen.getByRole('heading')).toBeInTheDocument();
  });

  it('renders the calculator component', () => {
    renderWithProviders(<EarlyPayoffPage />);
    expect(screen.getByTestId('early-payoff-calculator')).toBeInTheDocument();
  });
});
