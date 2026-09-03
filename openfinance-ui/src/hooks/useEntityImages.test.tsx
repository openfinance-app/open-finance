import { describe, it, expect, vi, beforeEach } from 'vitest';
import { waitFor } from '@testing-library/react';
import { renderHook, waitFor as waitForHook } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { createTestQueryClient } from '@/test/test-utils';
import { useEntityImages, useAttachmentImageUrl } from './useEntityImages';
import { useAttachments } from '@/hooks/useAttachments';
import apiClient from '@/services/apiClient';

vi.mock('@/hooks/useAttachments', () => ({ useAttachments: vi.fn() }));
vi.mock('@/services/apiClient', () => ({ default: { get: vi.fn() } }));

const mockUseAttachments = vi.mocked(useAttachments);
const mockGet = vi.mocked(apiClient.get);

beforeEach(() => {
  vi.clearAllMocks();
  // buildEncryptionHeaders() throws (fail-closed) without a seeded key — apiClient.get would never be reached
  sessionStorage.setItem('encryption_session', 'test-encryption-key');
  Object.defineProperty(globalThis.URL, 'createObjectURL', {
    writable: true,
    value: vi.fn(() => 'blob:mock-url'),
  });
  Object.defineProperty(globalThis.URL, 'revokeObjectURL', {
    writable: true,
    value: vi.fn(),
  });
});

describe('useEntityImages', () => {
  it('passes the entity type + id to the attachments query and filters to images only', () => {
    mockUseAttachments.mockReturnValue({
      data: [
        { id: 1, image: true, fileName: 'a.png' },
        { id: 2, image: false, fileName: 'b.pdf' },
      ],
      isLoading: false,
    } as any);

    const { result } = renderHook(() => useEntityImages('ASSET', 5), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={createTestQueryClient()}>{children}</QueryClientProvider>
      ),
    });

    expect(mockUseAttachments).toHaveBeenCalledWith({
      entityType: 'ASSET',
      entityId: 5,
    });
    expect(result.current.images.map((i) => i.id)).toEqual([1]);
    expect(result.current.isLoading).toBe(false);
  });

  it('disables the query when entityId is null', () => {
    mockUseAttachments.mockReturnValue({ data: undefined, isLoading: false } as any);

    renderHook(() => useEntityImages('ASSET', null), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={createTestQueryClient()}>{children}</QueryClientProvider>
      ),
    });

    expect(mockUseAttachments).toHaveBeenCalledWith(undefined);
  });
});

describe('useAttachmentImageUrl', () => {
  it('downloads the blob and exposes an object URL', async () => {
    mockGet.mockResolvedValue({
      data: new Blob(['x'], { type: 'image/png' }),
      headers: { 'content-type': 'image/png' },
    });

    const { result } = renderHook(() => useAttachmentImageUrl(1), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={createTestQueryClient()}>{children}</QueryClientProvider>
      ),
    });

    await waitForHook(() => expect(result.current).toBe('blob:mock-url'));
    expect(mockGet).toHaveBeenCalledWith('/attachments/1/download', {
      responseType: 'blob',
      headers: expect.anything(),
    });
  });

  it('stays null when download fails (placeholder fallback)', async () => {
    mockGet.mockRejectedValue(new Error('boom'));

    const { result } = renderHook(() => useAttachmentImageUrl(1), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={createTestQueryClient()}>{children}</QueryClientProvider>
      ),
    });

    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    expect(result.current).toBeNull();
  });
});
