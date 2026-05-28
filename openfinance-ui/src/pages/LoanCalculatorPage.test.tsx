import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import LoanCalculatorPage from './LoanCalculatorPage';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/components/loan-calculator/LoanCalculator', () => ({
  LoanCalculator: () => (
    <div data-testid="loan-calculator">Loan Calculator Component</div>
  ),
}));

vi.mock('@/hooks/useDocumentTitle', () => ({
  useDocumentTitle: vi.fn(),
}));

describe('LoanCalculatorPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders page header with title', () => {
    renderWithProviders(<LoanCalculatorPage />);
    expect(screen.getByRole('heading', { name: /loan calculator/i })).toBeInTheDocument();
  });

  it('renders the loan calculator component', () => {
    renderWithProviders(<LoanCalculatorPage />);
    expect(screen.getByTestId('loan-calculator')).toBeInTheDocument();
  });
});
