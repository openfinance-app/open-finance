import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { createTestQueryClient } from '@/test/test-utils';
import { usePropertyImages, useAttachmentImageUrl } from './usePropertyImages';
import { useAttachments } from '@/hooks/useAttachments';
import apiClient from '@/services/apiClient';

vi.mock('@/hooks/useAttachments', () => ({ useAttachments: vi.fn() }));
vi.mock('@/services/apiClient', () => ({ default: { get: vi.fn() } }));

const mockUseAttachments = vi.mocked(useAttachments);
const mockGet = vi.mocked(apiClient.get);

function createWrapper() {
  const queryClient = createTestQueryClient();
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

beforeEach(() => {
  vi.clearAllMocks();
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

describe('usePropertyImages', () => {
  it('filters attachments to images only', () => {
    mockUseAttachments.mockReturnValue({
      data: [
        { id: 1, image: true, fileName: 'a.png' },
        { id: 2, image: false, fileName: 'b.pdf' },
      ],
      isLoading: false,
    } as unknown as ReturnType<typeof useAttachments>);
    const { result } = renderHook(() => usePropertyImages(5), { wrapper: createWrapper() });
    expect(result.current.images.map(img => img.id)).toEqual([1]);
  });
});

describe('useAttachmentImageUrl', () => {
  it('downloads the blob and exposes an object URL', async () => {
    mockGet.mockResolvedValue({
      data: new Blob(['x'], { type: 'image/png' }),
      headers: { 'content-type': 'image/png' },
    });
    const { result } = renderHook(() => useAttachmentImageUrl(1), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current).toBe('blob:mock-url'));
    expect(mockGet).toHaveBeenCalledWith('/attachments/1/download', {
      responseType: 'blob',
      headers: expect.anything(),
    });
  });

  it('stays null when download fails (placeholder fallback)', async () => {
    mockGet.mockRejectedValue(new Error('boom'));
    const { result } = renderHook(() => useAttachmentImageUrl(1), { wrapper: createWrapper() });
    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    expect(result.current).toBeNull();
  });
});
