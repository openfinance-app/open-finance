import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/hooks/useDocumentTitle', () => ({ useDocumentTitle: vi.fn() }));
vi.mock('@/components/layout/PageHeader', () => ({
  PageHeader: ({ title }: { title: string }) => <h1 data-testid="page-header">{title}</h1>,
}));
vi.mock('@/components/layout/EmptyState', () => ({
  EmptyState: ({ title }: { title: string }) => <div data-testid="empty-state">{title}</div>,
}));

import CommunityPage from './CommunityPage';

describe('CommunityPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('renders page header with title', () => {
    renderWithProviders(<CommunityPage />);
    expect(screen.getByTestId('page-header')).toHaveTextContent('Community');
  });

  it('renders coming soon empty state', () => {
    renderWithProviders(<CommunityPage />);
    expect(screen.getByTestId('empty-state')).toHaveTextContent('Community features coming soon');
  });
});
