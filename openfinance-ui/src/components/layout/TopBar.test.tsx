import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/components/alerts/NotificationBadge', () => ({
  NotificationBadge: () => <div data-testid="notification-badge" />,
}));
vi.mock('./UserDropdownMenu', () => ({
  UserDropdownMenu: () => <div data-testid="user-dropdown" />,
}));
vi.mock('@/components/search/GlobalSearch', () => ({
  GlobalSearch: () => <input data-testid="global-search" />,
  __esModule: true,
  default: () => <input data-testid="global-search" />,
}));

import { TopBar } from './TopBar';

describe('TopBar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('renders the header element', () => {
    renderWithProviders(<TopBar />);
    expect(screen.getByRole('banner')).toBeInTheDocument();
  });

  it('renders the user dropdown menu', () => {
    renderWithProviders(<TopBar />);
    expect(screen.getByTestId('user-dropdown')).toBeInTheDocument();
  });

  it('renders the notification badge', () => {
    renderWithProviders(<TopBar />);
    expect(screen.getByTestId('notification-badge')).toBeInTheDocument();
  });
});
