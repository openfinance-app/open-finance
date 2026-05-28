import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { LastUpdatedIndicator, BatchUpdateIndicator } from '../LastUpdatedIndicator';

vi.mock('@/utils/time', () => ({
  formatRelativeTime: vi.fn((ts: string) => '5 minutes ago'),
  isStalePrice: vi.fn((ts: string) => false),
  getTimestampColor: vi.fn((ts: string) => 'text-green-500'),
}));

import { isStalePrice } from '@/utils/time';

describe('LastUpdatedIndicator', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    (isStalePrice as any).mockReturnValue(false);
  });

  it('renders relative time text', () => {
    renderWithProviders(<LastUpdatedIndicator lastUpdated="2024-01-01T00:00:00Z" />);
    expect(screen.getByText(/5 minutes ago/)).toBeInTheDocument();
  });

  it('shows clock icon when not stale', () => {
    renderWithProviders(<LastUpdatedIndicator lastUpdated="2024-01-01T00:00:00Z" />);
    expect(document.querySelector('.text-muted-foreground')).toBeInTheDocument();
  });

  it('shows warning icon when stale', () => {
    (isStalePrice as any).mockReturnValue(true);
    renderWithProviders(<LastUpdatedIndicator lastUpdated="2020-01-01T00:00:00Z" />);
    expect(document.querySelector('.text-yellow-500')).toBeInTheDocument();
  });

  it('hides warning when showWarning is false', () => {
    (isStalePrice as any).mockReturnValue(true);
    renderWithProviders(<LastUpdatedIndicator lastUpdated="2020-01-01T00:00:00Z" showWarning={false} />);
    expect(document.querySelector('.text-yellow-500')).not.toBeInTheDocument();
  });

  it('applies sm size class', () => {
    renderWithProviders(<LastUpdatedIndicator lastUpdated="2024-01-01T00:00:00Z" size="sm" />);
    expect(document.querySelector('.text-xs')).toBeInTheDocument();
  });

  it('applies lg size class', () => {
    renderWithProviders(<LastUpdatedIndicator lastUpdated="2024-01-01T00:00:00Z" size="lg" />);
    expect(document.querySelector('.text-base')).toBeInTheDocument();
  });
});

describe('BatchUpdateIndicator', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    (isStalePrice as any).mockReturnValue(false);
  });

  it('returns null for empty array', () => {
    const { container } = renderWithProviders(<BatchUpdateIndicator assets={[]} />);
    expect(container.textContent).toBe('');
  });

  it('renders with assets', () => {
    const assets = [
      { lastUpdated: '2024-01-01T00:00:00Z' },
      { lastUpdated: '2024-01-02T00:00:00Z' },
    ];
    renderWithProviders(<BatchUpdateIndicator assets={assets} />);
    expect(screen.getByText(/5 minutes ago/)).toBeInTheDocument();
  });

  it('shows stale count when assets are stale', () => {
    (isStalePrice as any).mockReturnValue(true);
    const assets = [
      { lastUpdated: '2020-01-01T00:00:00Z' },
      { lastUpdated: '2020-01-02T00:00:00Z' },
    ];
    renderWithProviders(<BatchUpdateIndicator assets={assets} />);
    expect(screen.getByText(/2/)).toBeInTheDocument();
  });
});
