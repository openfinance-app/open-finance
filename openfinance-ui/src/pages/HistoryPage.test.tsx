import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import {
  renderWithProviders,
  mockAuthentication,
  clearAuthentication,
  userEvent,
} from '@/test/test-utils';
import HistoryPage from '@/pages/HistoryPage';
import type { OperationHistoryResponse } from '@/types/history';

// ---------------------------------------------------------------------------
// Mock history data
// ---------------------------------------------------------------------------
const mockHistoryItems: OperationHistoryResponse[] = [
  {
    id: 1,
    canUndo: true,
    canRedo: false,
    entityType: 'TRANSACTION',
    entityId: 101,
    entityLabel: 'Weekly groceries',
    operationType: 'CREATE',
    createdAt: '2026-01-10T10:00:00Z',
  },
  {
    id: 2,
    canUndo: false,
    canRedo: false,
    entityType: 'ACCOUNT',
    entityId: 1,
    entityLabel: 'Checking Account',
    operationType: 'UPDATE',
    createdAt: '2026-01-09T09:00:00Z',
  },
];

const mockHistoryPage = {
  content: mockHistoryItems,
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 20,
};

// ---------------------------------------------------------------------------
// Mock historyService
// ---------------------------------------------------------------------------
vi.mock('@/services/historyService', () => ({
  historyService: {
    getHistory: vi.fn().mockResolvedValue({
      content: [
        {
          id: 1,
          canUndo: true,
          canRedo: false,
          entityType: 'TRANSACTION',
          entityId: 101,
          entityLabel: 'Weekly groceries',
          operationType: 'CREATE',
          createdAt: '2026-01-10T10:00:00Z',
        },
        {
          id: 2,
          canUndo: false,
          canRedo: false,
          entityType: 'ACCOUNT',
          entityId: 1,
          entityLabel: 'Checking Account',
          operationType: 'UPDATE',
          createdAt: '2026-01-09T09:00:00Z',
        },
      ],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
    }),
    undo: vi.fn().mockResolvedValue({}),
    redo: vi.fn().mockResolvedValue({}),
  },
}));

// Mock AuthContext to always provide sessionStartTime
vi.mock('@/context/AuthContext', async importOriginal => {
  const actual = await importOriginal<typeof import('@/context/AuthContext')>();
  return {
    ...actual,
    useAuthContext: () => ({
      baseCurrency: 'USD',
      sessionStartTime: '2026-01-01T00:00:00Z',
      user: { id: 1, username: 'testuser', email: 'test@example.com' },
      isAuthenticated: true,
      logout: vi.fn(),
    }),
  };
});

describe('HistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearAuthentication();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  describe('Data display', () => {
    it('should render the page heading', async () => {
      renderWithProviders(<HistoryPage />);
      expect(document.querySelector('h1')).toBeInTheDocument();
    });

    it('should display history entries after loading', async () => {
      renderWithProviders(<HistoryPage />);
      expect(await screen.findByText('Weekly groceries')).toBeInTheDocument();
    });

    it('should show the second history entry', async () => {
      renderWithProviders(<HistoryPage />);
      expect(await screen.findByText('Checking Account')).toBeInTheDocument();
    });
  });

  describe('Undo/Redo buttons', () => {
    it('should show undo buttons for entries', async () => {
      renderWithProviders(<HistoryPage />);
      await screen.findByText('Weekly groceries');

      const undoButtons = screen.getAllByRole('button', { name: /undo/i });
      expect(undoButtons.length).toBeGreaterThan(0);
    });

    it('hides redo when no entry supports it', async () => {
      renderWithProviders(<HistoryPage />);
      await screen.findByText('Weekly groceries');

      expect(screen.queryByRole('button', { name: /redo/i })).not.toBeInTheDocument();
    });
  });

  it('loads persisted history without restricting it to the login session', async () => {
    const { historyService } = await import('@/services/historyService');
    renderWithProviders(<HistoryPage />);
    await screen.findByText('Weekly groceries');
    expect(historyService.getHistory).toHaveBeenCalledWith(
      0,
      expect.any(Number),
      undefined,
      undefined,
      undefined,
      undefined
    );
  });

  it('shows an undo rejection and keeps the affected entry', async () => {
    const { historyService } = await import('@/services/historyService');
    vi.mocked(historyService.undo).mockRejectedValueOnce({
      isAxiosError: true,
      response: { data: { message: 'Undo the later payment first.' } },
    });
    const user = userEvent.setup();
    renderWithProviders(<HistoryPage />);
    await screen.findByText('Weekly groceries');
    await user.click(screen.getAllByRole('button', { name: /undo/i })[0]);
    expect(await screen.findByRole('alert')).toHaveTextContent('Undo the later payment first.');
    expect(screen.getByText('Weekly groceries')).toBeInTheDocument();
  });

  it('executes redo for a reversible undone entry', async () => {
    const { historyService } = await import('@/services/historyService');
    vi.mocked(historyService.getHistory).mockResolvedValueOnce({
      ...mockHistoryPage,
      content: [
        { ...mockHistoryItems[0], canUndo: false, canRedo: true, undoneAt: '2026-01-10T11:00:00Z' },
      ],
    });
    const user = userEvent.setup();
    renderWithProviders(<HistoryPage />);
    await screen.findByText('Weekly groceries');
    await user.click(screen.getByRole('button', { name: /redo/i }));
    await waitFor(() => expect(historyService.redo).toHaveBeenCalledWith(1));
  });

  it('displays before and after values when changes are expanded', async () => {
    const { historyService } = await import('@/services/historyService');
    vi.mocked(historyService.getHistory).mockResolvedValueOnce({
      ...mockHistoryPage,
      content: [
        {
          ...mockHistoryItems[0],
          changedFieldsJson: JSON.stringify({ amount: { before: '50.01', after: '65.02' } }),
        },
      ],
    });
    const user = userEvent.setup();
    renderWithProviders(<HistoryPage />);
    await screen.findByText('Weekly groceries');
    await user.click(screen.getByRole('button', { name: /show changes/i }));
    expect(screen.getByText('50.01')).toBeInTheDocument();
    expect(screen.getByText('65.02')).toBeInTheDocument();
  });

  describe('Filters', () => {
    it('should have an entity type filter dropdown', async () => {
      renderWithProviders(<HistoryPage />);
      await screen.findByText('Weekly groceries');

      const selects = screen.getAllByRole('combobox');
      expect(selects.length).toBeGreaterThan(0);
    });

    it('should filter by entity type', async () => {
      const user = userEvent.setup();
      renderWithProviders(<HistoryPage />);
      await screen.findByText('Weekly groceries');

      const select = screen.getAllByRole('combobox')[0];
      await user.selectOptions(select, 'TRANSACTION');
      expect(select).toBeInTheDocument();
    });
  });

  describe('Undo/Redo actions', () => {
    it('should call undo when clicking undo button', async () => {
      const { historyService } = await import('@/services/historyService');
      const user = userEvent.setup();
      renderWithProviders(<HistoryPage />);
      await screen.findByText('Weekly groceries');

      const undoButtons = screen.getAllByRole('button', { name: /undo/i });
      await user.click(undoButtons[0]);
      expect(historyService.undo).toHaveBeenCalledWith(1);
    });

    it('explains unavailable redo for an undone operation', async () => {
      const { historyService } = await import('@/services/historyService');
      // Being undone alone does not make redo available.
      vi.mocked(historyService.getHistory).mockResolvedValueOnce({
        content: [
          {
            id: 3,
            canUndo: false,
            canRedo: false,
            entityType: 'TRANSACTION',
            entityId: 101,
            entityLabel: 'Undone item',
            operationType: 'CREATE',
            createdAt: '2026-01-10T10:00:00Z',
            undoneAt: '2026-01-10T11:00:00Z',
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      });
      const user = userEvent.setup();
      renderWithProviders(<HistoryPage />);
      await screen.findByText('Undone item');

      expect(screen.getByRole('button', { name: /redo/i })).toBeDisabled();
      expect(historyService.redo).not.toHaveBeenCalled();
    });
  });
});
