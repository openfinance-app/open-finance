import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useConvertProperty,
  usePropertyConversionCandidates,
  usePropertyConversionEnabled,
  type PropertyConversionRequest,
} from './usePropertyConversion';
import apiClient from '@/services/apiClient';
import { useImportConfig } from '@/hooks/useImportConfig';

vi.mock('@/services/apiClient');
vi.mock('@/hooks/useImportConfig');
const mockedApiClient = apiClient as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
};
const mockedUseImportConfig = useImportConfig as unknown as ReturnType<typeof vi.fn>;

describe('property conversion hooks', () => {
  let queryClient: QueryClient;
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    sessionStorage.setItem('encryption_session', 'test-key');
    mockedUseImportConfig.mockReturnValue({
      data: { skroogeJsonEnabled: true, version: 'UNKNOWN' },
      isError: false,
    });
  });

  it('gates conversion on explicit server confirmation (fail-closed)', () => {
    expect(renderHook(() => usePropertyConversionEnabled()).result.current).toBe(true);

    mockedUseImportConfig.mockReturnValue({ data: undefined, isError: false });
    expect(renderHook(() => usePropertyConversionEnabled()).result.current).toBe(false);

    mockedUseImportConfig.mockReturnValue({
      data: { skroogeJsonEnabled: true, version: 'UNKNOWN' },
      isError: true,
    });
    expect(renderHook(() => usePropertyConversionEnabled()).result.current).toBe(false);
  });

  it('fetches property conversion candidates', async () => {
    mockedApiClient.get.mockResolvedValue({ data: [{ assetId: 4 }] });
    const { result } = renderHook(() => usePropertyConversionCandidates(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedApiClient.get).toHaveBeenCalledWith('/real-estate/conversion/candidates', {
      headers: { 'X-Encryption-Session': 'test-key' },
    });
    expect(result.current.data).toEqual([{ assetId: 4 }]);
  });

  it('does not fetch candidates when disabled or gated off', () => {
    renderHook(() => usePropertyConversionCandidates(false), { wrapper });
    mockedUseImportConfig.mockReturnValue({
      data: { skroogeJsonEnabled: false, version: 'UNKNOWN' },
      isError: false,
    });
    renderHook(() => usePropertyConversionCandidates(true), { wrapper });
    expect(mockedApiClient.get).not.toHaveBeenCalled();
  });

  it('posts the property conversion and invalidates affected surfaces once each', async () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    mockedApiClient.post.mockResolvedValue({ data: { propertyId: 21 } });
    const request = {
      operationId: 'op-2',
      assetId: 4,
      sourceRevision: 'rev-9',
      name: 'Flat',
      address: '1 Street',
      propertyType: 'APARTMENT',
    } as unknown as PropertyConversionRequest;

    const { result } = renderHook(() => useConvertProperty(), { wrapper });
    let data: unknown;
    await act(async () => {
      data = await result.current.mutateAsync(request);
    });

    expect(data).toEqual({ propertyId: 21 });
    expect(mockedApiClient.post).toHaveBeenCalledWith('/real-estate/conversion', request, {
      headers: { 'X-Encryption-Session': 'test-key' },
    });
    const keys = invalidate.mock.calls.map(([arg]) =>
      JSON.stringify((arg as { queryKey: unknown[] }).queryKey)
    );
    expect(keys).toHaveLength(9);
    expect(keys).toContain('["realEstate"]');
    expect(keys).toContain('["assetFinancing"]');
    expect(new Set(keys).size).toBe(keys.length);
  });
});
