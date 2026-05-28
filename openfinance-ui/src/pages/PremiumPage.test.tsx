import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import PremiumPage from './PremiumPage';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/hooks/useDocumentTitle', () => ({
  useDocumentTitle: vi.fn(),
}));

describe('PremiumPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders page header', () => {
    renderWithProviders(<PremiumPage />);
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
  });

  it('renders coming soon empty state', () => {
    renderWithProviders(<PremiumPage />);
    // The page uses EmptyState with premium.comingSoon
    expect(screen.getByText(/coming soon/i)).toBeInTheDocument();
  });
});
