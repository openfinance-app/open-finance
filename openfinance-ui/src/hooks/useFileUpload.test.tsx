import { renderHook, act, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { useFileUpload, useFileUploadWithProgress } from './useFileUpload';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

describe('useFileUpload hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  // ── useFileUpload ─────────────────────────────────────────────────
  describe('useFileUpload', () => {
    it('should upload file as FormData', async () => {
      const mockResponse = {
        uploadId: 'upload-123',
        fileName: 'transactions.qif',
        fileSize: 2048,
        format: 'QIF',
        status: 'VALID',
        message: 'File uploaded successfully',
      };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse });

      const { result } = renderHook(() => useFileUpload(), { wrapper });

      const file = new File(['QIF content'], 'transactions.qif', {
        type: 'application/octet-stream',
      });

      await act(async () => {
        result.current.mutate(file);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockResponse);
      expect(mockedApiClient.post).toHaveBeenCalledWith(
        '/import/upload',
        expect.any(FormData),
        expect.objectContaining({
          headers: { 'Content-Type': 'multipart/form-data' },
        })
      );
    });

    it('should throw error when response status is INVALID', async () => {
      const mockResponse = {
        uploadId: null,
        fileName: 'bad.txt',
        fileSize: 100,
        format: null,
        status: 'INVALID',
        message: 'Unsupported file format',
      };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse, status: 400 });

      const { result } = renderHook(() => useFileUpload(), { wrapper });

      const file = new File(['bad content'], 'bad.txt', { type: 'text/plain' });

      await act(async () => {
        result.current.mutate(file);
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
    });

    it('should throw error when response status is ERROR', async () => {
      const mockResponse = {
        uploadId: null,
        fileName: 'corrupt.qif',
        fileSize: 0,
        format: 'QIF',
        status: 'ERROR',
        message: 'File is corrupt',
      };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse, status: 500 });

      const { result } = renderHook(() => useFileUpload(), { wrapper });

      const file = new File(['corrupt'], 'corrupt.qif', { type: 'application/octet-stream' });

      await act(async () => {
        result.current.mutate(file);
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
    });

    it('should handle network errors', async () => {
      mockedApiClient.post.mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useFileUpload(), { wrapper });

      const file = new File(['content'], 'test.qif', { type: 'application/octet-stream' });

      await act(async () => {
        result.current.mutate(file);
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
    });
  });

  // ── useFileUploadWithProgress ─────────────────────────────────────────────────
  describe('useFileUploadWithProgress', () => {
    it('should expose upload mutation and progress', () => {
      const { result } = renderHook(() => useFileUploadWithProgress(), { wrapper });

      expect(result.current.uploadFileWithProgress).toBeDefined();
      expect(result.current.uploadProgress).toBe(0);
    });

    it('should delegate upload to useFileUpload', async () => {
      const mockResponse = {
        uploadId: 'upload-456',
        fileName: 'data.ofx',
        fileSize: 1024,
        format: 'OFX',
        status: 'VALID',
        message: 'Success',
      };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse });

      const { result } = renderHook(() => useFileUploadWithProgress(), { wrapper });

      const file = new File(['OFX data'], 'data.ofx', { type: 'application/octet-stream' });

      await act(async () => {
        result.current.uploadFileWithProgress.mutate(file);
      });

      await waitFor(() => expect(result.current.uploadFileWithProgress.isSuccess).toBe(true));
      expect(result.current.uploadFileWithProgress.data).toEqual(mockResponse);
    });
  });
});
