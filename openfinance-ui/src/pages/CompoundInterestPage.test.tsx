import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import CompoundInterestPage from './CompoundInterestPage';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/components/compound-interest/CompoundInterestCalculator', () => ({
  CompoundInterestCalculator: () => (
    <div data-testid="compound-interest-calculator">Calculator Component</div>
  ),
}));

vi.mock('@/hooks/useDocumentTitle', () => ({
  useDocumentTitle: vi.fn(),
}));

describe('CompoundInterestPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders page header with title', () => {
    renderWithProviders(<CompoundInterestPage />);
    expect(screen.getByRole('heading', { name: /compound interest/i })).toBeInTheDocument();
  });

  it('renders the calculator component', () => {
    renderWithProviders(<CompoundInterestPage />);
    expect(screen.getByTestId('compound-interest-calculator')).toBeInTheDocument();
  });
});
