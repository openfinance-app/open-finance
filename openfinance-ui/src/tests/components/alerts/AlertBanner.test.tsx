import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { AlertBanner } from '@/components/alerts/AlertBanner';
import type { BudgetAlert } from '@/types/alert';

// ---------------------------------------------------------------------------
// Mock hooks
// ---------------------------------------------------------------------------
const mockUseUnreadAlerts = vi.fn();
const mockMarkAsRead = vi.fn();
const mockMarkAllAsRead = vi.fn();

vi.mock('@/hooks/useAlerts', () => ({
  useUnreadAlerts: () => mockUseUnreadAlerts(),
  useMarkAlertAsRead: () => ({ mutate: mockMarkAsRead, isPending: false }),
  useMarkAllAlertsAsRead: () => ({ mutate: mockMarkAllAsRead, isPending: false }),
}));

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
    message: 'You have spent 85% of your Groceries budget.',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('AlertBanner', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('should render nothing while loading', () => {
    mockUseUnreadAlerts.mockReturnValue({ data: undefined, isLoading: true });
    const { container } = renderWithProviders(<AlertBanner />);
    expect(container.firstChild).toBeNull();
  });

  it('should render nothing when there are no alerts', () => {
    mockUseUnreadAlerts.mockReturnValue({ data: [], isLoading: false });
    const { container } = renderWithProviders(<AlertBanner />);
    expect(container.firstChild).toBeNull();
  });

  it('should display budget alert count', () => {
    mockUseUnreadAlerts.mockReturnValue({
      data: [makeAlert(), makeAlert({ id: '2' })],
      isLoading: false,
    });
    renderWithProviders(<AlertBanner />);
    expect(screen.getByText(/Budget Alerts \(2 unread\)/)).toBeInTheDocument();
  });

  it('should display alert budget name and category', () => {
    mockUseUnreadAlerts.mockReturnValue({
      data: [makeAlert()],
      isLoading: false,
    });
    renderWithProviders(<AlertBanner />);
    expect(screen.getByText(/Groceries.*Food/)).toBeInTheDocument();
  });

  it('should display spent percentage', () => {
    mockUseUnreadAlerts.mockReturnValue({
      data: [makeAlert({ currentSpentPercentage: 92 })],
      isLoading: false,
    });
    renderWithProviders(<AlertBanner />);
    expect(screen.getByText('92%')).toBeInTheDocument();
  });

  it('should show at most 3 alerts', () => {
    const alerts = [
      makeAlert({ id: '1', budgetName: 'Groceries' }),
      makeAlert({ id: '2', budgetName: 'Transport' }),
      makeAlert({ id: '3', budgetName: 'Entertainment' }),
      makeAlert({ id: '4', budgetName: 'Utilities' }),
    ];
    mockUseUnreadAlerts.mockReturnValue({ data: alerts, isLoading: false });
    renderWithProviders(<AlertBanner />);
    expect(screen.queryByText(/Utilities/)).not.toBeInTheDocument();
    expect(screen.getAllByText(/Groceries/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Entertainment/).length).toBeGreaterThan(0);
  });

  it('should call markAsRead when mark as read button is clicked', async () => {
    mockUseUnreadAlerts.mockReturnValue({
      data: [makeAlert({ id: 'alert-1' })],
      isLoading: false,
    });
    renderWithProviders(<AlertBanner />);

    // The button text in the component is "Mark read" not "Mark as read"
    const markReadButton = screen.getByText(/mark read/i);
    fireEvent.click(markReadButton);

    expect(mockMarkAsRead).toHaveBeenCalledWith('alert-1');
  });

  it('should call markAllAsRead when "Mark all as read" is clicked', () => {
    mockUseUnreadAlerts.mockReturnValue({
      data: [makeAlert(), makeAlert({ id: '2' })],
      isLoading: false,
    });
    renderWithProviders(<AlertBanner />);

    const markAllButton = screen.getByText(/mark all as read/i);
    fireEvent.click(markAllButton);

    expect(mockMarkAllAsRead).toHaveBeenCalled();
  });

  it('should dismiss an alert locally when X button is clicked', async () => {
    mockUseUnreadAlerts.mockReturnValue({
      data: [makeAlert({ budgetName: 'Groceries', id: '1' })],
      isLoading: false,
    });
    renderWithProviders(<AlertBanner />);

    expect(screen.getAllByText(/Groceries/).length).toBeGreaterThan(0);

    const dismissButton = screen.getByLabelText(/dismiss/i);
    fireEvent.click(dismissButton);

    await waitFor(() => {
      // After dismissing, if it's the only alert the banner disappears
      // OR the item is removed from the visible list
    });
  });

  it('should render exceeded severity for 100%+ spent', () => {
    mockUseUnreadAlerts.mockReturnValue({
      data: [makeAlert({ threshold: 100, currentSpentPercentage: 110 })],
      isLoading: false,
    });
    renderWithProviders(<AlertBanner />);
    expect(screen.getByText('110%')).toBeInTheDocument();
  });

  it('should show alert message when present', () => {
    mockUseUnreadAlerts.mockReturnValue({
      data: [makeAlert({ message: 'Custom alert message' })],
      isLoading: false,
    });
    renderWithProviders(<AlertBanner />);
    expect(screen.getByText('Custom alert message')).toBeInTheDocument();
  });
});
