import { describe, it, expect, vi, beforeEach } from 'vitest';
import apiClient from './apiClient';
import { historyService } from './historyService';
import type { OperationHistoryResponse, PageableResponse } from '@/types/history';

// Mock apiClient
vi.mock('./apiClient', () => ({
    default: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn(),
    },
}));

const mockApiClient = vi.mocked(apiClient);

const mockHistoryEntry: OperationHistoryResponse = {
    id: 1,
    entityType: 'TRANSACTION',
    entityId: 10,
    operationType: 'CREATE',
    createdAt: '2026-01-01T10:00:00Z',
    canUndo: true,
    canRedo: false,
    description: 'Created transaction',
} as unknown as OperationHistoryResponse;

const mockPageableResponse: PageableResponse<OperationHistoryResponse> = {
    content: [mockHistoryEntry],
    totalElements: 1,
    totalPages: 1,
    size: 20,
    number: 0,
};

describe('historyService', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('getHistory', () => {
        it('should call GET /history with default params', async () => {
            mockApiClient.get.mockResolvedValue({ data: mockPageableResponse });

            const result = await historyService.getHistory();

            expect(mockApiClient.get).toHaveBeenCalledWith(
                expect.stringContaining('/history?')
            );
            const url: string = mockApiClient.get.mock.calls[0][0];
            expect(url).toContain('page=0');
            expect(url).toContain('size=20');
            expect(url).toContain('sort=createdAt%2Cdesc');
            expect(result).toEqual(mockPageableResponse);
        });

        it('should include entityType when provided', async () => {
            mockApiClient.get.mockResolvedValue({ data: mockPageableResponse });

            await historyService.getHistory(0, 20, 'TRANSACTION');

            const url: string = mockApiClient.get.mock.calls[0][0];
            expect(url).toContain('entityType=TRANSACTION');
        });

        it('should include since when provided', async () => {
            mockApiClient.get.mockResolvedValue({ data: mockPageableResponse });
            const since = '2026-01-01T00:00:00Z';

            await historyService.getHistory(0, 20, undefined, since);

            const url: string = mockApiClient.get.mock.calls[0][0];
            expect(url).toContain('since=');
        });

        it('should use provided page and size', async () => {
            mockApiClient.get.mockResolvedValue({ data: mockPageableResponse });

            await historyService.getHistory(2, 50);

            const url: string = mockApiClient.get.mock.calls[0][0];
            expect(url).toContain('page=2');
            expect(url).toContain('size=50');
        });
    });

    describe('undo', () => {
        it('should call POST /history/:id/undo', async () => {
            mockApiClient.post.mockResolvedValue({ data: mockHistoryEntry });

            const result = await historyService.undo(1);

            expect(mockApiClient.post).toHaveBeenCalledWith('/history/1/undo');
            expect(result).toEqual(mockHistoryEntry);
        });

        it('should pass correct historyId', async () => {
            mockApiClient.post.mockResolvedValue({ data: mockHistoryEntry });

            await historyService.undo(42);

            expect(mockApiClient.post).toHaveBeenCalledWith('/history/42/undo');
        });
    });

    describe('redo', () => {
        it('should call POST /history/:id/redo', async () => {
            mockApiClient.post.mockResolvedValue({ data: mockHistoryEntry });

            const result = await historyService.redo(1);

            expect(mockApiClient.post).toHaveBeenCalledWith('/history/1/redo');
            expect(result).toEqual(mockHistoryEntry);
        });

        it('should pass correct historyId', async () => {
            mockApiClient.post.mockResolvedValue({ data: mockHistoryEntry });

            await historyService.redo(7);

            expect(mockApiClient.post).toHaveBeenCalledWith('/history/7/redo');
        });
    });
});
