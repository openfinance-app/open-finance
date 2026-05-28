import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import FinancialFreedomPage from './FinancialFreedomPage';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/components/financial-freedom/FinancialFreedomCalculator', () => ({
  FinancialFreedomCalculator: () => (
    <div data-testid="financial-freedom-calculator">Financial Freedom Calculator</div>
  ),
}));

vi.mock('@/hooks/useDocumentTitle', () => ({
  useDocumentTitle: vi.fn(),
}));

describe('FinancialFreedomPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders page header with title', () => {
    renderWithProviders(<FinancialFreedomPage />);
    expect(screen.getByRole('heading')).toBeInTheDocument();
  });

  it('renders the calculator component', () => {
    renderWithProviders(<FinancialFreedomPage />);
    expect(screen.getByTestId('financial-freedom-calculator')).toBeInTheDocument();
  });
});
