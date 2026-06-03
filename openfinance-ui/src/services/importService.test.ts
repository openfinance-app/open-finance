import { describe, it, expect, vi, beforeEach } from 'vitest';
import apiClient from './apiClient';
import { importService } from './importService';

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

const MOCK_ENCRYPTION_KEY = 'test-encryption-key';

const mockSession = {
    id: 1,
    status: 'PENDING',
    accountId: 1,
    fileName: 'transactions.csv',
    createdAt: '2026-01-01T00:00:00Z',
};

describe('importService', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        sessionStorage.setItem('encryption_session', MOCK_ENCRYPTION_KEY);
    });

    afterEach(() => {
        sessionStorage.removeItem('encryption_session');
    });

    describe('startImport', () => {
        it('should POST to /import/process with correct data', async () => {
            mockApiClient.post.mockResolvedValue({ data: mockSession });

            const request = { fileName: 'transactions.csv', content: 'csv-content', accountId: 1 } as any;
            const result = await importService.startImport(request);

            expect(mockApiClient.post).toHaveBeenCalledWith(
                '/import/process',
                request,
                expect.objectContaining({
                    headers: expect.objectContaining({ 'X-Encryption-Session': MOCK_ENCRYPTION_KEY }),
                })
            );
            expect(result).toEqual(mockSession);
        });

        it('should throw when encryption key is missing', async () => {
            sessionStorage.removeItem('encryption_session');

            await expect(importService.startImport({} as any)).rejects.toThrow(
                'Encryption key not found'
            );
        });
    });

    describe('getSession', () => {
        it('should GET /import/sessions/:id', async () => {
            mockApiClient.get.mockResolvedValue({ data: mockSession });

            const result = await importService.getSession(1);

            expect(mockApiClient.get).toHaveBeenCalledWith(
                '/import/sessions/1',
                expect.objectContaining({
                    headers: expect.objectContaining({ 'X-Encryption-Session': MOCK_ENCRYPTION_KEY }),
                })
            );
            expect(result).toEqual(mockSession);
        });

        it('should throw when encryption key is missing', async () => {
            sessionStorage.removeItem('encryption_session');

            await expect(importService.getSession(1)).rejects.toThrow(
                'Encryption key not found'
            );
        });
    });

    describe('getTransactions', () => {
        it('should GET /import/sessions/:id/review', async () => {
            const mockTransactions = [{ id: 1, amount: 50 }];
            mockApiClient.get.mockResolvedValue({ data: mockTransactions });

            const result = await importService.getTransactions(1);

            expect(mockApiClient.get).toHaveBeenCalledWith(
                '/import/sessions/1/review',
                expect.objectContaining({
                    headers: expect.objectContaining({ 'X-Encryption-Session': MOCK_ENCRYPTION_KEY }),
                    timeout: 600000,
                })
            );
            expect(result).toEqual(mockTransactions);
        });

        it('should throw when encryption key is missing', async () => {
            sessionStorage.removeItem('encryption_session');

            await expect(importService.getTransactions(1)).rejects.toThrow(
                'Encryption key not found'
            );
        });
    });

    describe('confirmImport', () => {
        it('should POST to /import/sessions/:id/confirm', async () => {
            mockApiClient.post.mockResolvedValue({ data: mockSession });

            const confirmData = { mappings: [] } as any;
            const result = await importService.confirmImport(1, confirmData);

            expect(mockApiClient.post).toHaveBeenCalledWith(
                '/import/sessions/1/confirm',
                confirmData,
                expect.objectContaining({
                    headers: expect.objectContaining({ 'X-Encryption-Session': MOCK_ENCRYPTION_KEY }),
                })
            );
            expect(result).toEqual(mockSession);
        });

        it('should throw when encryption key is missing', async () => {
            sessionStorage.removeItem('encryption_session');

            await expect(importService.confirmImport(1, {} as any)).rejects.toThrow(
                'Encryption key not found'
            );
        });
    });

    describe('cancelImport', () => {
        it('should POST to /import/sessions/:id/cancel', async () => {
            mockApiClient.post.mockResolvedValue({ data: mockSession });

            const result = await importService.cancelImport(1);

            expect(mockApiClient.post).toHaveBeenCalledWith(
                '/import/sessions/1/cancel',
                {},
                expect.objectContaining({
                    headers: expect.objectContaining({ 'X-Encryption-Session': MOCK_ENCRYPTION_KEY }),
                })
            );
            expect(result).toEqual(mockSession);
        });

        it('should throw when encryption key is missing', async () => {
            sessionStorage.removeItem('encryption_session');

            await expect(importService.cancelImport(1)).rejects.toThrow(
                'Encryption key not found'
            );
        });
    });

    describe('listSessions', () => {
        it('should GET /import/sessions', async () => {
            mockApiClient.get.mockResolvedValue({ data: [mockSession] });

            const result = await importService.listSessions();

            expect(mockApiClient.get).toHaveBeenCalledWith(
                '/import/sessions',
                expect.objectContaining({
                    headers: expect.objectContaining({ 'X-Encryption-Session': MOCK_ENCRYPTION_KEY }),
                })
            );
            expect(result).toEqual([mockSession]);
        });

        it('should throw when encryption key is missing', async () => {
            sessionStorage.removeItem('encryption_session');

            await expect(importService.listSessions()).rejects.toThrow(
                'Encryption key not found'
            );
        });
    });
});
