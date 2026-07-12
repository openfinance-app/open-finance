/**
 * React Query Hooks for Real Estate API
 *
 * Provides hooks for managing real estate properties:
 * - CRUD operations (create, read, update, delete)
 * - Equity calculations
 * - ROI analysis
 * - Property value updates
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { UseQueryResult } from '@tanstack/react-query';
import { resolveEncryptionEnabled, useSecurityConfig } from '@/hooks/useSecurityConfig';
import apiClient from '@/services/apiClient';
import type {
  RealEstateProperty,
  RealEstatePropertyRequest,
  PropertyEquityResponse,
  PropertyROIResponse,
  PropertyFilters,
  PropertySearchFilters,
} from '../types/realEstate';
import { useAuthContext } from '@/context/AuthContext';
import { buildEncryptionHeaders } from '@/utils/encryption';

const API_BASE_URL = '/real-estate';

// ============================================================================
// Query Keys
// ============================================================================

const realEstateKeys = {
  all: ['realEstate'] as const,
  lists: () => [...realEstateKeys.all, 'list'] as const,
  list: (filters?: PropertyFilters) => [...realEstateKeys.lists(), filters] as const,
  search: () => [...realEstateKeys.all, 'search'] as const,
  searchList: (filters?: PropertySearchFilters) => [...realEstateKeys.search(), filters] as const,
  details: () => [...realEstateKeys.all, 'detail'] as const,
  detail: (id: number) => [...realEstateKeys.details(), id] as const,
  equity: (id: number) => [...realEstateKeys.detail(id), 'equity'] as const,
  roi: (id: number) => [...realEstateKeys.detail(id), 'roi'] as const,
};

// ============================================================================
// API Functions
// ============================================================================

/**
 * Fetch all properties for the authenticated user
 */
async function fetchProperties(
  filters: PropertyFilters | undefined,
  encryptionEnabled: boolean
): Promise<RealEstateProperty[]> {
  const params = new URLSearchParams();

  if (filters?.propertyType) {
    params.append('propertyType', filters.propertyType);
  }
  if (filters?.includeInactive !== undefined) {
    params.append('includeInactive', String(filters.includeInactive));
  }

  const url = params.toString() ? `${API_BASE_URL}?${params}` : API_BASE_URL;
  const response = await apiClient.get<RealEstateProperty[]>(url, {
    headers: buildEncryptionHeaders(encryptionEnabled),
  });

  return response.data;
}

/**
 * Fetch properties with filters and pagination using the search endpoint
 */
async function fetchPropertiesSearch(
  filters: PropertySearchFilters | undefined,
  encryptionEnabled: boolean
): Promise<{
  content: RealEstateProperty[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}> {
  const params = new URLSearchParams();
  if (filters?.keyword) params.append('keyword', filters.keyword);
  if (filters?.propertyType) params.append('propertyType', filters.propertyType);
  if (filters?.currency) params.append('currency', filters.currency);
  if (filters?.isActive !== undefined) params.append('isActive', filters.isActive.toString());
  if (filters?.hasMortgage !== undefined)
    params.append('hasMortgage', filters.hasMortgage.toString());
  if (filters?.purchaseDateFrom) params.append('purchaseDateFrom', filters.purchaseDateFrom);
  if (filters?.purchaseDateTo) params.append('purchaseDateTo', filters.purchaseDateTo);
  if (filters?.valueMin !== undefined) params.append('valueMin', filters.valueMin.toString());
  if (filters?.valueMax !== undefined) params.append('valueMax', filters.valueMax.toString());
  if (filters?.priceMin !== undefined) params.append('priceMin', filters.priceMin.toString());
  if (filters?.priceMax !== undefined) params.append('priceMax', filters.priceMax.toString());
  if (filters?.rentalIncomeMin !== undefined)
    params.append('rentalIncomeMin', filters.rentalIncomeMin.toString());
  if (filters?.page !== undefined) params.append('page', filters.page.toString());
  if (filters?.size) params.append('size', filters.size.toString());
  // Add sorting (default to name ascending)
  if (filters?.sort) {
    params.append('sort', filters.sort);
  } else {
    params.append('sort', 'name,asc');
  }

  const response = await apiClient.get<{
    content: RealEstateProperty[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  }>(`${API_BASE_URL}/search?${params.toString()}`, {
    headers: buildEncryptionHeaders(encryptionEnabled),
  });

  return response.data;
}

/**
 * Fetch a single property by ID
 */
async function fetchProperty(id: number, encryptionEnabled: boolean): Promise<RealEstateProperty> {
  const response = await apiClient.get<RealEstateProperty>(`${API_BASE_URL}/${id}`, {
    headers: buildEncryptionHeaders(encryptionEnabled),
  });

  return response.data;
}

/**
 * Create a new property
 */
async function createProperty(
  request: RealEstatePropertyRequest,
  encryptionEnabled: boolean
): Promise<RealEstateProperty> {
  const response = await apiClient.post<RealEstateProperty>(API_BASE_URL, request, {
    headers: buildEncryptionHeaders(encryptionEnabled),
  });

  return response.data;
}

/**
 * Update an existing property
 */
async function updateProperty(
  id: number,
  request: RealEstatePropertyRequest,
  encryptionEnabled: boolean
): Promise<RealEstateProperty> {
  const response = await apiClient.put<RealEstateProperty>(`${API_BASE_URL}/${id}`, request, {
    headers: buildEncryptionHeaders(encryptionEnabled),
  });

  return response.data;
}

/**
 * Delete a property (soft delete - sets isActive = false)
 */
async function deleteProperty(id: number, encryptionEnabled: boolean): Promise<void> {
  await apiClient.delete(`${API_BASE_URL}/${id}`, {
    headers: buildEncryptionHeaders(encryptionEnabled),
  });
}

/**
 * Calculate property equity
 */
async function fetchPropertyEquity(
  id: number,
  encryptionEnabled: boolean
): Promise<PropertyEquityResponse> {
  const response = await apiClient.get<PropertyEquityResponse>(`${API_BASE_URL}/${id}/equity`, {
    headers: buildEncryptionHeaders(encryptionEnabled),
  });

  return response.data;
}

/**
 * Calculate property ROI
 */
async function fetchPropertyROI(
  id: number,
  encryptionEnabled: boolean
): Promise<PropertyROIResponse> {
  const response = await apiClient.get<PropertyROIResponse>(`${API_BASE_URL}/${id}/roi`, {
    headers: buildEncryptionHeaders(encryptionEnabled),
  });

  return response.data;
}

/**
 * Update property value estimate
 */
async function updatePropertyValue(
  id: number,
  newValue: number,
  encryptionEnabled: boolean
): Promise<RealEstateProperty> {
  const response = await apiClient.put<RealEstateProperty>(
    `${API_BASE_URL}/${id}/value`,
    { currentValue: newValue },
    { headers: buildEncryptionHeaders(encryptionEnabled) }
  );

  return response.data;
}

// ============================================================================
// React Query Hooks
// ============================================================================

/**
 * Hook to fetch all properties
 *
 * @param filters - Optional filters for property list
 * @returns Query result with properties array
 *
 * @example
 * const { data: properties, isLoading, error } = useProperties();
 *
 * @example
 * // Filter by property type
 * const { data } = useProperties({ propertyType: PropertyType.RESIDENTIAL });
 */
export function useProperties(filters?: PropertyFilters): UseQueryResult<RealEstateProperty[]> {
  const { baseCurrency } = useAuthContext();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);
  return useQuery({
    queryKey: [...realEstateKeys.list(filters), baseCurrency, encryptionEnabled],
    queryFn: () => fetchProperties(filters, encryptionEnabled),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch properties with filters and pagination
 *
 * @param filters - Optional filters and pagination parameters
 * @returns Query result with paginated properties
 *
 * @example
 * const { data, isLoading } = usePropertiesSearch({ page: 0, size: 20, sort: 'name,asc' });
 *
 * @example
 * // Filter by property type with pagination
 * const { data } = usePropertiesSearch({
 *   propertyType: PropertyType.RESIDENTIAL,
 *   page: 0,
 *   size: 10
 * });
 */
export function usePropertiesSearch(filters?: PropertySearchFilters): UseQueryResult<{
  content: RealEstateProperty[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}> {
  const { baseCurrency } = useAuthContext();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);
  return useQuery({
    queryKey: [...realEstateKeys.searchList(filters), baseCurrency, encryptionEnabled],
    queryFn: () => fetchPropertiesSearch(filters, encryptionEnabled),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch a single property
 *
 * @param id - Property ID
 * @returns Query result with property details
 *
 * @example
 * const { data: property, isLoading } = useProperty(123);
 */
export function useProperty(id: number): UseQueryResult<RealEstateProperty> {
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);
  return useQuery({
    queryKey: [...realEstateKeys.detail(id), encryptionEnabled],
    queryFn: () => fetchProperty(id, encryptionEnabled),
    enabled: !!id,
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * Hook to create a new property
 *
 * @returns Mutation for creating property
 *
 * @example
 * const createMutation = useCreateProperty();
 *
 * createMutation.mutate({
 *   name: "My House",
 *   address: "123 Main St",
 *   propertyType: PropertyType.RESIDENTIAL,
 *   purchasePrice: 300000,
 *   currentValue: 350000,
 *   purchaseDate: "2020-01-15",
 *   currency: "USD"
 * });
 */
export function useCreateProperty() {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation({
    mutationFn: (request: RealEstatePropertyRequest) => createProperty(request, encryptionEnabled),
    onSuccess: () => {
      // Invalidate all property queries (list, search) to refresh data
      queryClient.invalidateQueries({ queryKey: realEstateKeys.lists() });
      queryClient.invalidateQueries({ queryKey: realEstateKeys.search() });
      // Also invalidate dashboard to update net worth
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Hook to update an existing property
 *
 * @returns Mutation for updating property
 *
 * @example
 * const updateMutation = useUpdateProperty();
 *
 * updateMutation.mutate({
 *   id: 123,
 *   data: { currentValue: 375000 }
 * });
 */
export function useUpdateProperty() {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: RealEstatePropertyRequest }) =>
      updateProperty(id, data, encryptionEnabled),
    onSuccess: (_updatedProperty, variables) => {
      // Invalidate the specific property and its sub-queries
      queryClient.invalidateQueries({ queryKey: realEstateKeys.detail(variables.id) });
      queryClient.invalidateQueries({ queryKey: realEstateKeys.equity(variables.id) });
      queryClient.invalidateQueries({ queryKey: realEstateKeys.roi(variables.id) });
      // Invalidate property lists and search results
      queryClient.invalidateQueries({ queryKey: realEstateKeys.lists() });
      queryClient.invalidateQueries({ queryKey: realEstateKeys.search() });
      // Invalidate dashboard
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Hook to delete a property
 *
 * @returns Mutation for deleting property
 *
 * @example
 * const deleteMutation = useDeleteProperty();
 *
 * deleteMutation.mutate(123);
 */
export function useDeleteProperty() {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation({
    mutationFn: (id: number) => deleteProperty(id, encryptionEnabled),
    onSuccess: () => {
      // Invalidate all property queries (list, search) to refresh data
      queryClient.invalidateQueries({ queryKey: realEstateKeys.lists() });
      queryClient.invalidateQueries({ queryKey: realEstateKeys.search() });
      // Invalidate dashboard
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Hook to fetch property equity calculation
 *
 * @param id - Property ID
 * @returns Query result with equity details
 *
 * @example
 * const { data: equity, isLoading } = usePropertyEquity(123);
 */
export function usePropertyEquity(id: number): UseQueryResult<PropertyEquityResponse> {
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);
  return useQuery({
    queryKey: [...realEstateKeys.equity(id), encryptionEnabled],
    queryFn: () => fetchPropertyEquity(id, encryptionEnabled),
    enabled: !!id,
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * Hook to fetch property ROI calculation
 *
 * @param id - Property ID
 * @returns Query result with ROI analysis
 *
 * @example
 * const { data: roi, isLoading } = usePropertyROI(123);
 */
export function usePropertyROI(id: number): UseQueryResult<PropertyROIResponse> {
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);
  return useQuery({
    queryKey: [...realEstateKeys.roi(id), encryptionEnabled],
    queryFn: () => fetchPropertyROI(id, encryptionEnabled),
    enabled: !!id,
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * Hook to update property value estimate
 *
 * @returns Mutation for updating property value
 *
 * @example
 * const updateValueMutation = useUpdatePropertyValue();
 *
 * updateValueMutation.mutate({
 *   id: 123,
 *   newValue: 380000
 * });
 */
export function useUpdatePropertyValue() {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation({
    mutationFn: ({ id, newValue }: { id: number; newValue: number }) =>
      updatePropertyValue(id, newValue, encryptionEnabled),
    onSuccess: (_updatedProperty, variables) => {
      // Invalidate the specific property
      queryClient.invalidateQueries({ queryKey: realEstateKeys.detail(variables.id) });
      // Invalidate property lists
      queryClient.invalidateQueries({ queryKey: realEstateKeys.lists() });
      // Invalidate ROI and equity calculations
      queryClient.invalidateQueries({ queryKey: realEstateKeys.equity(variables.id) });
      queryClient.invalidateQueries({ queryKey: realEstateKeys.roi(variables.id) });
      // Invalidate dashboard
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
