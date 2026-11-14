import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useConvertLiability,
  useLiabilityConversionCandidates,
  useLiabilityConversionEnabled,
  useLiabilityConversionPayments,
  type LiabilityConversionRequest,
} from './useLiabilityConversion';
import apiClient from '@/services/apiClient';
import { useImportConfig } from '@/hooks/useImportConfig';

vi.mock('@/services/apiClient');
vi.mock('@/hooks/useImportConfig');
const mockedApiClient = apiClient as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
};
const mockedUseImportConfig = useImportConfig as unknown as ReturnType<typeof vi.fn>;

const config = (overrides = {}) => ({
  data: { skroogeJsonEnabled: true, version: 'UNKNOWN' },
  isError: false,
  ...overrides,
});

describe('liability conversion hooks', () => {
  let queryClient: QueryClient;
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    sessionStorage.setItem('encryption_session', 'test-key');
    mockedUseImportConfig.mockReturnValue(config());
  });

  describe('useLiabilityConversionEnabled (fail-closed gate)', () => {
    it('is enabled only when the server explicitly enables Skrooge features', () => {
      const { result } = renderHook(() => useLiabilityConversionEnabled());
      expect(result.current).toBe(true);
    });

    it('is disabled while the config is still loading (data undefined)', () => {
      mockedUseImportConfig.mockReturnValue(config({ data: undefined }));
      const { result } = renderHook(() => useLiabilityConversionEnabled());
      expect(result.current).toBe(false);
    });

    it('is disabled when the config request failed', () => {
      mockedUseImportConfig.mockReturnValue(config({ isError: true }));
      const { result } = renderHook(() => useLiabilityConversionEnabled());
      expect(result.current).toBe(false);
    });

    it('is disabled when the server reports Skrooge features off', () => {
      mockedUseImportConfig.mockReturnValue(
        config({ data: { skroogeJsonEnabled: false, version: 'UNKNOWN' } })
      );
      const { result } = renderHook(() => useLiabilityConversionEnabled());
      expect(result.current).toBe(false);
    });
  });

  describe('useLiabilityConversionPayments', () => {
    it('fetches payment candidates with the search term and encryption header', async () => {
      const payload = { items: [{ transactionId: 9 }], truncated: false };
      mockedApiClient.get.mockResolvedValue({ data: payload });

      const { result } = renderHook(() => useLiabilityConversionPayments(7, 'rent', true), {
        wrapper,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/liabilities/conversion/7/payments', {
        params: { search: 'rent' },
        headers: { 'X-Encryption-Session': 'test-key' },
      });
      expect(result.current.data).toEqual(payload);
    });

    it('does not fetch when the wizard is closed', () => {
      renderHook(() => useLiabilityConversionPayments(7, '', false), { wrapper });
      expect(mockedApiClient.get).not.toHaveBeenCalled();
    });

    it('does not fetch while conversion is gated off by server config', () => {
      mockedUseImportConfig.mockReturnValue(
        config({ data: { skroogeJsonEnabled: false, version: 'UNKNOWN' } })
      );
      renderHook(() => useLiabilityConversionPayments(7, '', true), { wrapper });
      expect(mockedApiClient.get).not.toHaveBeenCalled();
    });
  });

  describe('useLiabilityConversionCandidates', () => {
    it('fetches candidates by default', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [{ accountId: 3 }] });
      const { result } = renderHook(() => useLiabilityConversionCandidates(), { wrapper });
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/liabilities/conversion/candidates', {
        headers: { 'X-Encryption-Session': 'test-key' },
      });
      expect(result.current.data).toEqual([{ accountId: 3 }]);
    });

    it('respects an explicit enabled=false', () => {
      renderHook(() => useLiabilityConversionCandidates(false), { wrapper });
      expect(mockedApiClient.get).not.toHaveBeenCalled();
    });
  });

  describe('useConvertLiability', () => {
    const request = {
      operationId: 'op-1',
      accountId: 7,
      sourceRevision: 'rev-1',
      fundedPrincipal: '120000',
      liability: {},
    } as LiabilityConversionRequest;

    it('posts the conversion and refreshes every affected surface exactly once', async () => {
      const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);
      mockedApiClient.post.mockResolvedValue({ data: { id: 11 } });

      const { result } = renderHook(() => useConvertLiability(), { wrapper });
      await act(async () => {
        await result.current.mutateAsync(request);
      });

      expect(mockedApiClient.post).toHaveBeenCalledWith('/liabilities/conversion', request, {
        headers: { 'X-Encryption-Session': 'test-key' },
      });
      const keys = invalidate.mock.calls.map(([arg]) =>
        JSON.stringify((arg as { queryKey: unknown[] }).queryKey)
      );
      expect(keys).toEqual(
        expect.arrayContaining([
          '["realEstate"]',
          '["liabilities"]',
          '["borrowingCapacity"]',
          '["assets"]',
          '["accounts"]',
          '["transactions"]',
          '["dashboard"]',
          '["networth"]',
          '["history"]',
          '["session-history-exists"]',
          '["assetFinancing"]',
        ])
      );
      expect(new Set(keys).size).toBe(keys.length);
    });

    it('does not invalidate anything when the conversion fails', async () => {
      const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);
      mockedApiClient.post.mockRejectedValue(new Error('conflict'));

      const { result } = renderHook(() => useConvertLiability(), { wrapper });
      await act(async () => {
        await expect(result.current.mutateAsync(request)).rejects.toThrow('conflict');
      });
      expect(invalidate).not.toHaveBeenCalled();
    });
  });
});
