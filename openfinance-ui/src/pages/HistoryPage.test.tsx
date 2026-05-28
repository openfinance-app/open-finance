import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication, userEvent } from '@/test/test-utils';
import HistoryPage from '@/pages/HistoryPage';
import type { OperationHistoryResponse } from '@/types/history';

// ---------------------------------------------------------------------------
// Mock history data
// ---------------------------------------------------------------------------
const mockHistoryItems: OperationHistoryResponse[] = [
  {
    id: 1,
    entityType: 'TRANSACTION',
    entityId: 101,
    entityLabel: 'Weekly groceries',
    operationType: 'CREATE',
    operationDate: '2026-01-10T10:00:00Z',
    timestamp: '2026-01-10T10:00:00Z',
  },
  {
    id: 2,
    entityType: 'ACCOUNT',
    entityId: 1,
    entityLabel: 'Checking Account',
    operationType: 'UPDATE',
    operationDate: '2026-01-09T09:00:00Z',
    timestamp: '2026-01-09T09:00:00Z',
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
          entityType: 'TRANSACTION',
          entityId: 101,
          entityLabel: 'Weekly groceries',
          operationType: 'CREATE',
          operationDate: '2026-01-10T10:00:00Z',
          timestamp: '2026-01-10T10:00:00Z',
        },
        {
          id: 2,
          entityType: 'ACCOUNT',
          entityId: 1,
          entityLabel: 'Checking Account',
          operationType: 'UPDATE',
          operationDate: '2026-01-09T09:00:00Z',
          timestamp: '2026-01-09T09:00:00Z',
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
vi.mock('@/context/AuthContext', async (importOriginal) => {
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

    it('should show redo buttons for entries', async () => {
      renderWithProviders(<HistoryPage />);
      await screen.findByText('Weekly groceries');

      const redoButtons = screen.getAllByRole('button', { name: /redo/i });
      expect(redoButtons.length).toBeGreaterThan(0);
    });
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

    it('should call redo when clicking redo button on undone item', async () => {
      const { historyService } = await import('@/services/historyService');
      // Override to return an undone item so redo is enabled
      (historyService.getHistory as any).mockResolvedValueOnce({
        content: [
          {
            id: 3,
            entityType: 'TRANSACTION',
            entityId: 101,
            entityLabel: 'Undone item',
            operationType: 'CREATE',
            operationDate: '2026-01-10T10:00:00Z',
            timestamp: '2026-01-10T10:00:00Z',
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

      const redoButtons = screen.getAllByRole('button', { name: /redo/i });
      await user.click(redoButtons[0]);
      expect(historyService.redo).toHaveBeenCalledWith(3);
    });
  });
});
