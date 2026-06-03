import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useAttachments,
  useAttachment,
  useStorageStats,
  useUploadAttachment,
  useDownloadAttachment,
  useFetchAttachmentBlob,
  useDeleteAttachment,
  useAttachmentOperations,
} from './useAttachments';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

const mockSessionStorage = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
  key: vi.fn(),
  length: 0,
};
Object.defineProperty(window, 'sessionStorage', { value: mockSessionStorage });

describe('useAttachments hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.restoreAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  const mockAttachment = {
    id: 1,
    entityType: 'TRANSACTION',
    entityId: 10,
    fileName: 'receipt.pdf',
    mimeType: 'application/pdf',
    fileSize: 1024,
    description: 'Monthly receipt',
    createdAt: '2024-01-01T00:00:00Z',
  };

  // ── useAttachments ─────────────────────────────────────────────────
  describe('useAttachments', () => {
    it('should fetch attachments with filters', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockAttachment] });

      const { result } = renderHook(
        () => useAttachments({ entityType: 'TRANSACTION' as any, entityId: 10 }),
        { wrapper },
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual([mockAttachment]);
      expect(mockedApiClient.get).toHaveBeenCalledWith(expect.stringContaining('entityType=TRANSACTION'));
    });

    it('should be disabled when no filters are provided', () => {
      const { result } = renderHook(() => useAttachments(), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });
  });

  // ── useAttachment ─────────────────────────────────────────────────
  describe('useAttachment', () => {
    it('should fetch a single attachment by id', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockAttachment });

      const { result } = renderHook(() => useAttachment(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockAttachment);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/attachments/1');
    });

    it('should be disabled when id is null', () => {
      const { result } = renderHook(() => useAttachment(null), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });
  });

  // ── useStorageStats ─────────────────────────────────────────────────
  describe('useStorageStats', () => {
    it('should fetch storage statistics', async () => {
      const mockStats = { totalSize: 10240, fileCount: 5, maxSize: 1048576 };
      mockedApiClient.get.mockResolvedValue({ data: mockStats });

      const { result } = renderHook(() => useStorageStats(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockStats);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/attachments/stats');
    });
  });

  // ── useUploadAttachment ─────────────────────────────────────────────────
  describe('useUploadAttachment', () => {
    it('should upload attachment with form data', async () => {
      mockedApiClient.post.mockResolvedValue({ data: mockAttachment });

      const { result } = renderHook(() => useUploadAttachment(), { wrapper });

      const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
      await act(async () => {
        result.current.mutate({
          file,
          entityType: 'TRANSACTION' as any,
          entityId: 10,
          description: 'Test attachment',
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.post).toHaveBeenCalledWith(
        '/attachments',
        expect.any(FormData),
        expect.objectContaining({
          headers: expect.objectContaining({
            'Content-Type': 'multipart/form-data',
            'X-Encryption-Session': 'test-encryption-key',
          }),
        }),
      );
    });

    it('should throw error when encryption key is missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useUploadAttachment(), { wrapper });

      const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
      await act(async () => {
        result.current.mutate({
          file,
          entityType: 'TRANSACTION' as any,
          entityId: 10,
        });
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toContain('Encryption key not found');
    });
  });

  // ── useDownloadAttachment ─────────────────────────────────────────────────
  describe('useDownloadAttachment', () => {
    it('should download attachment and create blob URL', async () => {
      const blobData = new Blob(['file content'], { type: 'application/pdf' });
      mockedApiClient.get.mockResolvedValue({
        data: blobData,
        headers: { 'content-type': 'application/pdf' },
      });

      // Mock URL.createObjectURL and revokeObjectURL
      const createObjectURLSpy = vi.fn(() => 'blob:mock-url');
      const revokeObjectURLSpy = vi.fn();
      window.URL.createObjectURL = createObjectURLSpy;
      window.URL.revokeObjectURL = revokeObjectURLSpy;

      // Mock anchor element creation without breaking renderHook
      const clickSpy = vi.fn();
      const originalCreateElement = document.createElement.bind(document);
      vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
        if (tag === 'a') {
          return { href: '', download: '', click: clickSpy } as any;
        }
        return originalCreateElement(tag);
      });
      vi.spyOn(document.body, 'appendChild').mockImplementation((node) => node);
      vi.spyOn(document.body, 'removeChild').mockImplementation((node) => node);

      const { result } = renderHook(() => useDownloadAttachment(), { wrapper });

      await act(async () => {
        await result.current.downloadAttachment(1, 'receipt.pdf');
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith('/attachments/1/download', {
        responseType: 'blob',
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
      expect(clickSpy).toHaveBeenCalled();

      vi.restoreAllMocks();
    });

    it('should track downloading state', async () => {
      const { result } = renderHook(() => useDownloadAttachment(), { wrapper });
      expect(result.current.isDownloading).toBe(false);
    });
  });

  // ── useFetchAttachmentBlob ─────────────────────────────────────────────────
  describe('useFetchAttachmentBlob', () => {
    it('should fetch attachment as blob', async () => {
      const blobData = new Blob(['image data'], { type: 'image/png' });
      mockedApiClient.get.mockResolvedValue({
        data: blobData,
        headers: { 'content-type': 'image/png' },
      });

      const { result } = renderHook(() => useFetchAttachmentBlob(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toBeInstanceOf(Blob);
    });
  });

  // ── useDeleteAttachment ─────────────────────────────────────────────────
  describe('useDeleteAttachment', () => {
    it('should delete attachment', async () => {
      mockedApiClient.delete.mockResolvedValue({});

      const { result } = renderHook(() => useDeleteAttachment(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.delete).toHaveBeenCalledWith('/attachments/1');
    });

    it('should invalidate queries on success', async () => {
      mockedApiClient.delete.mockResolvedValue({});
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useDeleteAttachment(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['attachments'] });
    });
  });

  // ── useAttachmentOperations ─────────────────────────────────────────────────
  describe('useAttachmentOperations', () => {
    it('should expose all operations', () => {
      mockedApiClient.get.mockResolvedValue({ data: [] });

      const { result } = renderHook(
        () => useAttachmentOperations('TRANSACTION', 10),
        { wrapper },
      );

      expect(result.current.upload).toBeDefined();
      expect(result.current.download).toBeDefined();
      expect(result.current.deleteAttachment).toBeDefined();
      expect(result.current.isUploading).toBe(false);
      expect(result.current.isDownloading).toBe(false);
      expect(result.current.isDeleting).toBe(false);
    });

    it('should not fetch attachments when no entity info provided', () => {
      const { result } = renderHook(
        () => useAttachmentOperations(),
        { wrapper },
      );

      expect(result.current.attachments).toEqual([]);
      expect(result.current.isLoading).toBe(false);
    });
  });
});
