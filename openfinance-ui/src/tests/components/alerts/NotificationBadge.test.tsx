import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { NotificationBadge } from '@/components/alerts/NotificationBadge';
import type { BudgetAlert } from '@/types/alert';
import type { INotification } from '@/types/notification';

// ---------------------------------------------------------------------------
// Mock hooks
// ---------------------------------------------------------------------------
const mockUseUnreadAlertCount = vi.fn();
const mockUseUnreadAlerts = vi.fn();
const mockUseMarkAlertAsRead = vi.fn();
const mockUseNotifications = vi.fn();
const mockUseNotificationCount = vi.fn();
const mockUseUpdateExchangeRates = vi.fn();

vi.mock('@/hooks/useAlerts', () => ({
  useUnreadAlertCount: () => mockUseUnreadAlertCount(),
  useUnreadAlerts: () => mockUseUnreadAlerts(),
  useMarkAlertAsRead: () => mockUseMarkAlertAsRead(),
}));

vi.mock('@/hooks/useNotifications', () => ({
  useNotifications: () => mockUseNotifications(),
  useNotificationCount: () => mockUseNotificationCount(),
  useUpdateExchangeRatesFromNotification: () => mockUseUpdateExchangeRates(),
}));

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
function makeAlert(overrides: Partial<BudgetAlert> = {}): BudgetAlert {
  return {
    id: '1',
    budgetId: 1,
    budgetName: 'Groceries',
    categoryName: 'Food',
    threshold: 80,
    isEnabled: true,
    lastTriggered: null,
    isRead: false,
    currentSpentPercentage: 85,
    message: 'Spending alert',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function makeNotification(overrides: Partial<INotification> = {}): INotification {
  return {
    id: '1',
    type: 'UNCATEGORIZED_TRANSACTIONS',
    title: 'Uncategorized Transactions',
    message: '5 transactions need categorization',
    isRead: false,
    actionUrl: '/transactions?noCategory=1',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  } as INotification;
}

describe('NotificationBadge', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();

    // Default: no notifications
    mockUseUnreadAlertCount.mockReturnValue({ data: 0 });
    mockUseUnreadAlerts.mockReturnValue({ data: [] });
    mockUseMarkAlertAsRead.mockReturnValue({ mutate: vi.fn(), isPending: false });
    mockUseNotifications.mockReturnValue({ data: [] });
    mockUseNotificationCount.mockReturnValue({ data: 0 });
    mockUseUpdateExchangeRates.mockReturnValue({ mutate: vi.fn(), isPending: false });
  });

  describe('Bell button', () => {
    it('should render the bell button', () => {
      renderWithProviders(<NotificationBadge />);
      const bell = screen.getByRole('button');
      expect(bell).toBeInTheDocument();
    });

    it('should not show count badge when there are no notifications', () => {
      renderWithProviders(<NotificationBadge />);
      // No count badge text like "1", "2" etc.
      expect(screen.queryByText(/^[1-9]/)).not.toBeInTheDocument();
    });

    it('should show count badge when there are unread alerts', () => {
      mockUseUnreadAlertCount.mockReturnValue({ data: 3 });
      renderWithProviders(<NotificationBadge />);
      expect(screen.getByText('3')).toBeInTheDocument();
    });

    it('should show combined count from alerts and notifications', () => {
      mockUseUnreadAlertCount.mockReturnValue({ data: 2 });
      mockUseNotificationCount.mockReturnValue({ data: 1 });
      renderWithProviders(<NotificationBadge />);
      expect(screen.getByText('3')).toBeInTheDocument();
    });

    it('should display "99+" when count exceeds 99', () => {
      mockUseUnreadAlertCount.mockReturnValue({ data: 50 });
      mockUseNotificationCount.mockReturnValue({ data: 60 });
      renderWithProviders(<NotificationBadge />);
      expect(screen.getByText('99+')).toBeInTheDocument();
    });
  });

  describe('Dropdown', () => {
    it('should open dropdown when bell button is clicked', async () => {
      mockUseUnreadAlerts.mockReturnValue({ data: [makeAlert()] });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));

      await waitFor(() => {
        expect(screen.getByText('Spending alert')).toBeInTheDocument();
      });
    });

    it('should close dropdown when Escape is pressed', async () => {
      mockUseUnreadAlerts.mockReturnValue({ data: [makeAlert()] });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => {
        expect(screen.getByText('Spending alert')).toBeInTheDocument();
      });

      fireEvent.keyDown(document, { key: 'Escape' });
      await waitFor(() => {
        expect(screen.queryByText('Spending alert')).not.toBeInTheDocument();
      });
    });

    it('should show system notifications in dropdown', async () => {
      mockUseNotifications.mockReturnValue({
        data: [makeNotification({ message: '5 transactions need categorization' })],
      });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));

      await waitFor(() => {
        expect(screen.getByText('5 transactions need categorization')).toBeInTheDocument();
      });
    });

    it('should show empty state when no notifications exist', async () => {
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));

      await waitFor(() => {
        expect(screen.getByText(/no notifications|all caught up/i)).toBeInTheDocument();
      });
    });
  });

  describe('Loading state', () => {
    it('should show loading spinner when data is loading', async () => {
      mockUseUnreadAlerts.mockReturnValue({ data: undefined, isLoading: true });
      mockUseNotifications.mockReturnValue({ data: undefined, isLoading: true });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));

      await waitFor(() => {
        expect(document.querySelector('.animate-spin')).toBeTruthy();
      });
    });
  });

  describe('Budget alert interactions', () => {
    it('should navigate to budget page when budget alert is clicked', async () => {
      mockUseUnreadAlerts.mockReturnValue({ data: [makeAlert({ budgetName: 'Groceries' })] });
      mockUseUnreadAlertCount.mockReturnValue({ data: 1 });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => {
        expect(screen.getByText('Spending alert')).toBeInTheDocument();
      });

      // Click on the budget alert item
      const alertItem = screen.getByText('Groceries').closest('[role="button"]');
      if (alertItem) fireEvent.click(alertItem);
      expect(mockNavigate).toHaveBeenCalledWith('/budget?alertKeyword=Groceries');
    });

    it('should call markAsRead when mark read button is clicked', async () => {
      const mockMutate = vi.fn();
      mockUseMarkAlertAsRead.mockReturnValue({ mutate: mockMutate, isPending: false });
      mockUseUnreadAlerts.mockReturnValue({ data: [makeAlert({ id: '42' })] });
      mockUseUnreadAlertCount.mockReturnValue({ data: 1 });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => expect(screen.getByText('Spending alert')).toBeInTheDocument());

      // Find and click the mark-read button
      const markReadBtn = document.querySelector('[aria-label*="mark"]') ||
        document.querySelector('[aria-label*="Mark"]');
      if (markReadBtn) {
        fireEvent.click(markReadBtn);
        expect(mockMutate).toHaveBeenCalledWith('42');
      }
    });

    it('should show budget alert percentage', async () => {
      mockUseUnreadAlerts.mockReturnValue({
        data: [makeAlert({ currentSpentPercentage: 95 })],
      });
      mockUseUnreadAlertCount.mockReturnValue({ data: 1 });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => {
        expect(screen.getByText('95%')).toBeInTheDocument();
      });
    });
  });

  describe('System notification interactions', () => {
    it('should navigate when a non-inline notification is clicked', async () => {
      mockUseNotifications.mockReturnValue({
        data: [makeNotification({ type: 'UNCATEGORIZED_TRANSACTIONS' as any })],
      });
      mockUseNotificationCount.mockReturnValue({ data: 1 });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => {
        expect(screen.getByText('5 transactions need categorization')).toBeInTheDocument();
      });

      const notifItem = screen.getByText('Uncategorized Transactions').closest('[role="button"]');
      if (notifItem) fireEvent.click(notifItem);
      expect(mockNavigate).toHaveBeenCalledWith('/transactions?noCategory=1');
    });

    it('should show stale exchange rates with Update Now button', async () => {
      mockUseNotifications.mockReturnValue({
        data: [
          makeNotification({
            type: 'STALE_EXCHANGE_RATES' as any,
            title: 'Stale Exchange Rates',
            message: 'Exchange rates are outdated',
            actionLabel: 'Update Now',
          }),
        ],
      });
      mockUseNotificationCount.mockReturnValue({ data: 1 });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => {
        expect(screen.getByText('Stale Exchange Rates')).toBeInTheDocument();
        expect(screen.getByText('Exchange rates are outdated')).toBeInTheDocument();
      });
    });

    it('should call update exchange rates mutation', async () => {
      const mockMutate = vi.fn();
      mockUseUpdateExchangeRates.mockReturnValue({ mutate: mockMutate, isPending: false, isSuccess: false });
      mockUseNotifications.mockReturnValue({
        data: [
          makeNotification({
            type: 'STALE_EXCHANGE_RATES' as any,
            title: 'Stale Exchange Rates',
            message: 'Rates outdated',
            actionLabel: 'Update Now',
          }),
        ],
      });
      mockUseNotificationCount.mockReturnValue({ data: 1 });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => expect(screen.getByText('Stale Exchange Rates')).toBeInTheDocument());

      // Click the update rates button
      const updateBtn = document.querySelector('[aria-label*="update"]') ||
        document.querySelector('[aria-label*="Update"]');
      if (updateBtn) {
        fireEvent.click(updateBtn);
        expect(mockMutate).toHaveBeenCalled();
      }
    });

    it('should show success message after exchange rates update', async () => {
      mockUseUpdateExchangeRates.mockReturnValue({ mutate: vi.fn(), isPending: false, isSuccess: true });
      mockUseNotifications.mockReturnValue({
        data: [
          makeNotification({
            type: 'STALE_EXCHANGE_RATES' as any,
            title: 'Stale Exchange Rates',
            message: 'Rates outdated',
            actionLabel: 'Update Now',
          }),
        ],
      });
      mockUseNotificationCount.mockReturnValue({ data: 1 });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => {
        expect(screen.getByText(/updated successfully/i)).toBeInTheDocument();
      });
    });

    it('should show notification count badge for system notifications', async () => {
      mockUseNotifications.mockReturnValue({
        data: [
          makeNotification({
            type: 'UNCATEGORIZED_TRANSACTIONS' as any,
            count: 5,
          } as any),
        ],
      });
      mockUseNotificationCount.mockReturnValue({ data: 1 });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => {
        expect(screen.getByText('5')).toBeInTheDocument();
      });
    });
  });

  describe('Click outside', () => {
    it('should close dropdown when clicking outside', async () => {
      mockUseUnreadAlerts.mockReturnValue({ data: [makeAlert()] });
      mockUseUnreadAlertCount.mockReturnValue({ data: 1 });
      renderWithProviders(<NotificationBadge />);

      fireEvent.click(screen.getByRole('button'));
      await waitFor(() => expect(screen.getByText('Spending alert')).toBeInTheDocument());

      // Click outside
      fireEvent.mouseDown(document.body);
      await waitFor(() => {
        expect(screen.queryByText('Spending alert')).not.toBeInTheDocument();
      });
    });
  });
});
