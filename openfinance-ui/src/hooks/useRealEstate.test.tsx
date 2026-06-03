import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useProperties,
  usePropertiesSearch,
  useProperty,
  useCreateProperty,
  useUpdateProperty,
  useDeleteProperty,
  usePropertyEquity,
  usePropertyROI,
  useUpdatePropertyValue,
} from './useRealEstate';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

vi.mock('@/context/AuthContext', () => ({
  useAuthContext: vi.fn(() => ({
    baseCurrency: 'USD',
    user: { id: 1, email: 'test@example.com' },
  })),
}));

const mockSessionStorage = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
  key: vi.fn(),
  length: 0,
};
Object.defineProperty(window, 'sessionStorage', { value: mockSessionStorage });

describe('useRealEstate hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  const mockProperty = {
    id: 1,
    name: 'My House',
    address: '123 Main St',
    propertyType: 'RESIDENTIAL',
    purchasePrice: 300000,
    currentValue: 350000,
    purchaseDate: '2020-01-15',
    currency: 'USD',
    isActive: true,
    createdAt: '2020-01-15T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  // ── useProperties ─────────────────────────────────────────────────
  describe('useProperties', () => {
    it('should fetch all properties', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockProperty] });

      const { result } = renderHook(() => useProperties(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual([mockProperty]);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/real-estate', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should fetch properties with filters', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockProperty] });

      const { result } = renderHook(
        () => useProperties({ propertyType: 'RESIDENTIAL' }),
        { wrapper },
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith(
        expect.stringContaining('propertyType=RESIDENTIAL'),
        expect.any(Object),
      );
    });

    it('should throw error when encryption key is missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useProperties(), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toContain('Encryption key not found');
    });
  });

  // ── usePropertiesSearch ─────────────────────────────────────────────────
  describe('usePropertiesSearch', () => {
    it('should fetch properties with search filters and pagination', async () => {
      const pagedResponse = {
        content: [mockProperty],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      };
      mockedApiClient.get.mockResolvedValue({ data: pagedResponse });

      const { result } = renderHook(
        () => usePropertiesSearch({ page: 0, size: 20 }),
        { wrapper },
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(pagedResponse);
      expect(mockedApiClient.get).toHaveBeenCalledWith(
        expect.stringContaining('/real-estate/search'),
        expect.any(Object),
      );
    });
  });

  // ── useProperty ─────────────────────────────────────────────────
  describe('useProperty', () => {
    it('should fetch a single property by id', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockProperty });

      const { result } = renderHook(() => useProperty(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockProperty);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/real-estate/1', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });
  });

  // ── useCreateProperty ─────────────────────────────────────────────────
  describe('useCreateProperty', () => {
    it('should create a new property', async () => {
      mockedApiClient.post.mockResolvedValue({ data: mockProperty });

      const { result } = renderHook(() => useCreateProperty(), { wrapper });

      await act(async () => {
        result.current.mutate({
          name: 'My House',
          address: '123 Main St',
          propertyType: 'RESIDENTIAL',
          purchasePrice: 300000,
          currentValue: 350000,
          purchaseDate: '2020-01-15',
          currency: 'USD',
        } as any);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockProperty);
    });

    it('should invalidate queries on success', async () => {
      mockedApiClient.post.mockResolvedValue({ data: mockProperty });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useCreateProperty(), { wrapper });

      await act(async () => {
        result.current.mutate({ name: 'Test' } as any);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
    });
  });

  // ── useUpdateProperty ─────────────────────────────────────────────────
  describe('useUpdateProperty', () => {
    it('should update an existing property', async () => {
      const updated = { ...mockProperty, currentValue: 375000 };
      mockedApiClient.put.mockResolvedValue({ data: updated });

      const { result } = renderHook(() => useUpdateProperty(), { wrapper });

      await act(async () => {
        result.current.mutate({ id: 1, data: { currentValue: 375000 } as any });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data?.currentValue).toBe(375000);
    });
  });

  // ── useDeleteProperty ─────────────────────────────────────────────────
  describe('useDeleteProperty', () => {
    it('should delete a property', async () => {
      mockedApiClient.delete.mockResolvedValue({});

      const { result } = renderHook(() => useDeleteProperty(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.delete).toHaveBeenCalledWith('/real-estate/1', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });
  });

  // ── usePropertyEquity ─────────────────────────────────────────────────
  describe('usePropertyEquity', () => {
    it('should fetch property equity', async () => {
      const mockEquity = {
        propertyId: 1,
        currentValue: 350000,
        outstandingMortgage: 200000,
        equity: 150000,
        equityPercentage: 42.86,
      };
      mockedApiClient.get.mockResolvedValue({ data: mockEquity });

      const { result } = renderHook(() => usePropertyEquity(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockEquity);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/real-estate/1/equity', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });
  });

  // ── usePropertyROI ─────────────────────────────────────────────────
  describe('usePropertyROI', () => {
    it('should fetch property ROI', async () => {
      const mockROI = {
        propertyId: 1,
        totalReturn: 50000,
        annualizedReturn: 4.5,
        capitalGain: 50000,
        rentalIncome: 0,
      };
      mockedApiClient.get.mockResolvedValue({ data: mockROI });

      const { result } = renderHook(() => usePropertyROI(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockROI);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/real-estate/1/roi', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });
  });

  // ── useUpdatePropertyValue ─────────────────────────────────────────────────
  describe('useUpdatePropertyValue', () => {
    it('should update property value', async () => {
      const updated = { ...mockProperty, currentValue: 380000 };
      mockedApiClient.put.mockResolvedValue({ data: updated });

      const { result } = renderHook(() => useUpdatePropertyValue(), { wrapper });

      await act(async () => {
        result.current.mutate({ id: 1, newValue: 380000 });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.put).toHaveBeenCalledWith(
        '/real-estate/1/value',
        { currentValue: 380000 },
        { headers: { 'X-Encryption-Session': 'test-encryption-key' } },
      );
    });

    it('should invalidate related queries on success', async () => {
      mockedApiClient.put.mockResolvedValue({ data: mockProperty });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdatePropertyValue(), { wrapper });

      await act(async () => {
        result.current.mutate({ id: 1, newValue: 380000 });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
    });
  });
});
