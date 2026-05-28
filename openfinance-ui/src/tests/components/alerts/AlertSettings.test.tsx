import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, act } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { AlertSettings } from '@/components/alerts/AlertSettings';
import type { BudgetAlert } from '@/types/alert';

// ---------------------------------------------------------------------------
// Mock hooks
// ---------------------------------------------------------------------------
const mockUseAlertsByBudget = vi.fn();
const mockCreateAlertMutate = vi.fn();
const mockUpdateAlertMutate = vi.fn();
const mockDeleteAlertMutate = vi.fn();

vi.mock('@/hooks/useAlerts', () => ({
  useAlertsByBudget: (...args: any[]) => mockUseAlertsByBudget(...args),
  useCreateAlert: () => ({ mutate: mockCreateAlertMutate, isPending: false }),
  useUpdateAlert: () => ({ mutate: mockUpdateAlertMutate, isPending: false }),
  useDeleteAlert: () => ({ mutate: mockDeleteAlertMutate, isPending: false }),
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
    threshold: 75,
    isEnabled: true,
    lastTriggered: null,
    isRead: false,
    currentSpentPercentage: 50,
    message: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('AlertSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    // Default: no existing alerts, not loading
    mockUseAlertsByBudget.mockReturnValue({ data: [], isLoading: false });
  });

  describe('Loading state', () => {
    it('should show loading spinner while fetching alerts', () => {
      mockUseAlertsByBudget.mockReturnValue({ data: [], isLoading: true });
      renderWithProviders(<AlertSettings budgetId={1} />);
      expect(screen.getByText(/loading alerts/i)).toBeInTheDocument();
    });
  });

  describe('Empty state', () => {
    it('should show "No alerts configured" message when no alerts exist', () => {
      renderWithProviders(<AlertSettings budgetId={1} />);
      expect(screen.getByText(/no alerts configured/i)).toBeInTheDocument();
    });

    it('should show "Add Alert" button', () => {
      renderWithProviders(<AlertSettings budgetId={1} />);
      expect(screen.getByRole('button', { name: /add alert/i })).toBeInTheDocument();
    });
  });

  describe('Existing alerts', () => {
    it('should display existing alert thresholds', () => {
      mockUseAlertsByBudget.mockReturnValue({
        data: [makeAlert({ threshold: 80 })],
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      expect(screen.getByText('80%')).toBeInTheDocument();
    });

    it('should render multiple alerts', () => {
      mockUseAlertsByBudget.mockReturnValue({
        data: [makeAlert({ id: '1', threshold: 50 }), makeAlert({ id: '2', threshold: 90 })],
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      expect(screen.getByText('50%')).toBeInTheDocument();
      expect(screen.getByText('90%')).toBeInTheDocument();
    });
  });

  describe('Add Alert form', () => {
    it('should show the add alert form when "Add Alert" button is clicked', async () => {
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /add alert/i }));
      await waitFor(() => {
        expect(screen.getByText(/alert threshold/i)).toBeInTheDocument();
      });
    });

    it('should show threshold value in the form', async () => {
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /add alert/i }));
      await waitFor(() => {
        // 75% appears in both the bold display span and as a quick-select button
        expect(screen.getAllByText('75%').length).toBeGreaterThan(0);
      });
    });

    it('should call createAlert with correct data when form is submitted', async () => {
      mockCreateAlertMutate.mockImplementation((_payload: any, opts: any) => opts?.onSuccess?.());
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /add alert/i }));

      await waitFor(() => {
        expect(screen.getByText(/alert threshold/i)).toBeInTheDocument();
      });

      const form = document.querySelector('form')!;
      fireEvent.submit(form);

      expect(mockCreateAlertMutate).toHaveBeenCalledWith(
        expect.objectContaining({
          budgetId: 1,
          data: expect.objectContaining({ threshold: 75, isEnabled: true }),
        }),
        expect.any(Object)
      );
    });

    it('should constrain threshold to valid range via slider attributes', async () => {
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /add alert/i }));

      await waitFor(() => {
        expect(screen.getByRole('slider')).toBeInTheDocument();
      });

      // The slider enforces the 1-150% range at the input level
      const slider = screen.getByRole('slider') as HTMLInputElement;
      expect(slider).toHaveAttribute('min', '1');
      expect(slider).toHaveAttribute('max', '150');
    });

    it('should show error when duplicate threshold exists', async () => {
      mockUseAlertsByBudget.mockReturnValue({
        data: [makeAlert({ threshold: 75 })],
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /add alert/i }));

      await waitFor(() => {
        expect(screen.getByRole('slider')).toBeInTheDocument();
      });

      // Threshold is already 75 by default — submit to trigger duplicate error
      const form = document.querySelector('form')!;
      fireEvent.submit(form);

      await waitFor(() => {
        expect(screen.getByText(/already exists/i)).toBeInTheDocument();
      });
    });

    it('should show available quick-select threshold buttons', async () => {
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /add alert/i }));

      await waitFor(() => {
        expect(screen.getByText(/quick select/i)).toBeInTheDocument();
      });

      // Quick select should include suggested thresholds not already used
      expect(screen.getByRole('button', { name: '25%' })).toBeInTheDocument();
    });

    it('should update threshold when quick-select button is clicked', async () => {
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /add alert/i }));

      await waitFor(() => {
        expect(screen.getByRole('button', { name: '50%' })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: '50%' }));

      // The displayed threshold should update
      await waitFor(() => {
        const boldThreshold = screen.getAllByText('50%')[0];
        expect(boldThreshold).toBeInTheDocument();
      });
    });

    it('should close the form after cancel', async () => {
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /add alert/i }));

      await waitFor(() => {
        expect(screen.getByText(/alert threshold/i)).toBeInTheDocument();
      });

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      fireEvent.click(cancelButton);

      await waitFor(() => {
        expect(screen.queryByText(/alert threshold/i)).not.toBeInTheDocument();
      });
    });
  });

  describe('Toggle and edit alerts', () => {
    it('should toggle alert enabled state', () => {
      mockUseAlertsByBudget.mockReturnValue({
        data: [makeAlert({ id: '1', threshold: 75, isEnabled: true })],
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      const toggleBtn = screen.getByRole('button', { name: /disable alert/i });
      fireEvent.click(toggleBtn);
      expect(mockUpdateAlertMutate).toHaveBeenCalledWith(
        expect.objectContaining({ alertId: '1', data: { isEnabled: false } })
      );
    });

    it('should enable a disabled alert', () => {
      mockUseAlertsByBudget.mockReturnValue({
        data: [makeAlert({ id: '2', threshold: 50, isEnabled: false })],
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      const toggleBtn = screen.getByRole('button', { name: /enable alert/i });
      fireEvent.click(toggleBtn);
      expect(mockUpdateAlertMutate).toHaveBeenCalledWith(
        expect.objectContaining({ alertId: '2', data: { isEnabled: true } })
      );
    });

    it('should enter edit mode when edit button is clicked', () => {
      mockUseAlertsByBudget.mockReturnValue({
        data: [makeAlert({ id: '1', threshold: 75 })],
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      const editBtn = screen.getByRole('button', { name: /edit threshold/i });
      fireEvent.click(editBtn);
      // Should show number input and save/cancel buttons
      expect(screen.getByRole('spinbutton')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /save/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    });

    it('should save edited threshold', () => {
      mockUseAlertsByBudget.mockReturnValue({
        data: [makeAlert({ id: '1', threshold: 75 })],
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /edit threshold/i }));
      const input = screen.getByRole('spinbutton');
      fireEvent.change(input, { target: { value: '90' } });
      fireEvent.click(screen.getByRole('button', { name: /save/i }));
      expect(mockUpdateAlertMutate).toHaveBeenCalledWith(
        expect.objectContaining({ alertId: '1', data: { threshold: 90 } })
      );
    });

    it('should cancel edit without saving', () => {
      mockUseAlertsByBudget.mockReturnValue({
        data: [makeAlert({ id: '1', threshold: 75 })],
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /edit threshold/i }));
      const input = screen.getByRole('spinbutton');
      fireEvent.change(input, { target: { value: '99' } });
      fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
      // Should go back to display mode
      expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();
      expect(mockUpdateAlertMutate).not.toHaveBeenCalled();
    });

    it('should delete alert with confirmation', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      mockUseAlertsByBudget.mockReturnValue({
        data: [makeAlert({ id: '1', threshold: 75 })],
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      const deleteBtn = screen.getByRole('button', { name: /delete alert/i });
      fireEvent.click(deleteBtn);
      expect(mockDeleteAlertMutate).toHaveBeenCalledWith('1');
      vi.restoreAllMocks();
    });

    it('should not delete alert when confirmation is cancelled', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      mockUseAlertsByBudget.mockReturnValue({
        data: [makeAlert({ id: '1', threshold: 75 })],
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      const deleteBtn = screen.getByRole('button', { name: /delete alert/i });
      fireEvent.click(deleteBtn);
      expect(mockDeleteAlertMutate).not.toHaveBeenCalled();
      vi.restoreAllMocks();
    });

    it('should show "Triggered" badge when alert has lastTriggered', () => {
      mockUseAlertsByBudget.mockReturnValue({
        data: [makeAlert({ id: '1', threshold: 75, lastTriggered: '2026-05-01T00:00:00Z' })],
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      expect(screen.getByText(/triggered/i)).toBeInTheDocument();
    });
  });

  describe('Create error handling', () => {
    it('should show error when create fails', async () => {
      mockCreateAlertMutate.mockImplementation((_payload: any, opts: any) => {
        opts?.onError?.(new Error('Server error'));
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /add alert/i }));
      await waitFor(() => expect(screen.getByRole('slider')).toBeInTheDocument());
      const form = document.querySelector('form')!;
      fireEvent.submit(form);
      await waitFor(() => {
        expect(screen.getByText(/server error|failed to create/i)).toBeInTheDocument();
      });
    });

    it('should show visual preview with selected threshold', () => {
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /add alert/i }));
      expect(screen.getByText(/spending reaches/i)).toBeInTheDocument();
      expect(screen.getAllByText('75%').length).toBeGreaterThan(0);
    });

    it('should hide quick-select when all thresholds are used', () => {
      mockUseAlertsByBudget.mockReturnValue({
        data: [25, 50, 75, 90, 100, 125].map((t, i) => makeAlert({ id: String(i), threshold: t })),
        isLoading: false,
      });
      renderWithProviders(<AlertSettings budgetId={1} />);
      fireEvent.click(screen.getByRole('button', { name: /add alert/i }));
      expect(screen.queryByText(/quick select/i)).not.toBeInTheDocument();
    });
  });
});
