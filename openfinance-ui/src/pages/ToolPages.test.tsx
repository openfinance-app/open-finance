import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication } from '@/test/test-utils';

// Simple wrapper pages that delegate to child components
vi.mock('@/components/compound-interest/CompoundInterestCalculator', () => ({
  CompoundInterestCalculator: () => <div data-testid="compound-calculator">Calculator</div>,
}));
vi.mock('@/components/loan-calculator/LoanCalculator', () => ({
  LoanCalculator: () => <div data-testid="loan-calculator">Loan Calculator</div>,
}));
vi.mock('@/components/early-payoff/EarlyPayoffCalculator', () => ({
  EarlyPayoffCalculator: () => <div data-testid="early-payoff">Early Payoff</div>,
}));
vi.mock('@/components/financial-freedom/FinancialFreedomCalculator', () => ({
  FinancialFreedomCalculator: () => <div data-testid="financial-freedom">Freedom</div>,
}));

import CompoundInterestPage from '@/pages/CompoundInterestPage';
import LoanCalculatorPage from '@/pages/LoanCalculatorPage';
import EarlyPayoffPage from '@/pages/EarlyPayoffPage';
import FinancialFreedomPage from '@/pages/FinancialFreedomPage';
import CommunityPage from '@/pages/CommunityPage';
import PremiumPage from '@/pages/PremiumPage';

describe('CompoundInterestPage', () => {
  beforeEach(() => { clearAuthentication(); mockAuthentication(); });

  it('renders the calculator', () => {
    renderWithProviders(<CompoundInterestPage />);
    expect(screen.getByTestId('compound-calculator')).toBeInTheDocument();
  });
});

describe('LoanCalculatorPage', () => {
  beforeEach(() => { clearAuthentication(); mockAuthentication(); });

  it('renders the loan calculator', () => {
    renderWithProviders(<LoanCalculatorPage />);
    expect(screen.getByTestId('loan-calculator')).toBeInTheDocument();
  });
});

describe('EarlyPayoffPage', () => {
  beforeEach(() => { clearAuthentication(); mockAuthentication(); });

  it('renders the early payoff calculator', () => {
    renderWithProviders(<EarlyPayoffPage />);
    expect(screen.getByTestId('early-payoff')).toBeInTheDocument();
  });
});

describe('FinancialFreedomPage', () => {
  beforeEach(() => { clearAuthentication(); mockAuthentication(); });

  it('renders the financial freedom calculator', () => {
    renderWithProviders(<FinancialFreedomPage />);
    expect(screen.getByTestId('financial-freedom')).toBeInTheDocument();
  });
});

describe('CommunityPage', () => {
  beforeEach(() => { clearAuthentication(); mockAuthentication(); });

  it('renders without crashing', () => {
    renderWithProviders(<CommunityPage />);
    expect(document.querySelector('h1') || document.querySelector('[class*="page"]')).toBeTruthy();
  });
});

describe('PremiumPage', () => {
  beforeEach(() => { clearAuthentication(); mockAuthentication(); });

  it('renders without crashing', () => {
    renderWithProviders(<PremiumPage />);
    expect(document.querySelector('h1') || document.querySelector('[class*="page"]')).toBeTruthy();
  });
});
