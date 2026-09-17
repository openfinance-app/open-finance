import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useSimulationStorage } from './useSimulationStorage';
import apiClient from '@/services/apiClient';
import type { BuyRentInputs } from '@/types/realEstateTools';
import { DEFAULT_BUY_RENT_INPUTS } from '@/types/realEstateTools';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

describe('useSimulationStorage', () => {
  const mockApiSimulation = {
    id: 1,
    name: 'Test Simulation',
    simulationType: 'buy_rent' as const,
    data: JSON.stringify(DEFAULT_BUY_RENT_INPUTS),
    createdAt: '2025-01-15T10:00:00Z',
    updatedAt: '2025-01-15T10:00:00Z',
  };

  const mockApiSimulation2 = {
    id: 2,
    name: 'Rental Sim',
    simulationType: 'rental_investment' as const,
    data: JSON.stringify({
      credit: {
        monthlyPayment: 1000,
        annualCost: 12000,
        totalCost: 300000,
        assurance: 50,
        bankFees: 500,
      },
      property: { totalPrice: 250000, furnishingType: 'unfurnished', furnitureValue: 0 },
      revenue: { monthlyRent: 900, recoverableCharges: 100, occupancyRate: 95, badDebtRate: 1 },
      expenses: { propertyTax: 2000 },
    }),
    createdAt: '2025-01-16T10:00:00Z',
    updatedAt: '2025-01-16T10:00:00Z',
  };

  beforeEach(() => {
    vi.clearAllMocks();
    // Default: GET /real-estate-simulations returns list
    mockedApiClient.get.mockResolvedValue({ data: [mockApiSimulation, mockApiSimulation2] });
    mockedApiClient.post.mockResolvedValue({
      data: { ...mockApiSimulation, id: 3, name: 'New Sim' },
    });
    mockedApiClient.put.mockResolvedValue({ data: { ...mockApiSimulation, name: 'Renamed Sim' } });
    mockedApiClient.delete.mockResolvedValue({ data: {} });

    // Mock window.confirm for clearAll
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  it('should load simulations on mount', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    // Initially loading
    expect(result.current.isLoading).toBe(true);

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.simulations).toHaveLength(2);
    expect(result.current.simulations[0].metadata.id).toBe('1');
    expect(result.current.simulations[0].metadata.name).toBe('Test Simulation');
    expect(result.current.simulations[0].metadata.type).toBe('buy_rent');
    expect(result.current.error).toBeNull();
  });

  it('should handle API error on load', async () => {
    mockedApiClient.get.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.simulations).toEqual([]);
    expect(result.current.error).toBe('Erreur lors du chargement des simulations');
  });

  it('should save a simulation', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.saveSimulation('New Sim', 'buy_rent', DEFAULT_BUY_RENT_INPUTS);
    });

    expect(success).toBe(true);
    expect(mockedApiClient.post).toHaveBeenCalledWith('/real-estate-simulations', {
      name: 'New Sim',
      simulationType: 'buy_rent',
      data: JSON.stringify(DEFAULT_BUY_RENT_INPUTS),
    });
    expect(result.current.simulations).toHaveLength(3);
    expect(result.current.error).toBeNull();
  });

  it('should reject saving with empty name', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.saveSimulation('   ', 'buy_rent', DEFAULT_BUY_RENT_INPUTS);
    });

    expect(success).toBe(false);
    expect(result.current.error).toBe('Le nom de la simulation ne peut pas être vide');
    expect(mockedApiClient.post).not.toHaveBeenCalled();
  });

  it('should reject saving when at max simulations limit', async () => {
    // Create 50 simulations
    const manySims = Array.from({ length: 50 }, (_, i) => ({
      ...mockApiSimulation,
      id: i + 1,
      name: `Sim ${i + 1}`,
    }));
    mockedApiClient.get.mockResolvedValue({ data: manySims });

    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.simulations).toHaveLength(50);

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.saveSimulation(
        'Too Many',
        'buy_rent',
        DEFAULT_BUY_RENT_INPUTS
      );
    });

    expect(success).toBe(false);
    expect(result.current.error).toContain('50');
    expect(mockedApiClient.post).not.toHaveBeenCalled();
  });

  it('should handle save API error', async () => {
    mockedApiClient.post.mockRejectedValue({
      response: { data: { message: 'Server error' } },
    });

    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.saveSimulation(
        'Fail Sim',
        'buy_rent',
        DEFAULT_BUY_RENT_INPUTS
      );
    });

    expect(success).toBe(false);
    expect(result.current.error).toBe('Server error');
  });

  it('should load a simulation by id', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    const sim = result.current.loadSimulation('1');
    expect(sim).not.toBeNull();
    expect(sim!.metadata.name).toBe('Test Simulation');
  });

  it('should return null for non-existent simulation', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    const sim = result.current.loadSimulation('999');
    expect(sim).toBeNull();
  });

  it('should delete a simulation', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.deleteSimulation('1');
    });

    expect(success).toBe(true);
    expect(mockedApiClient.delete).toHaveBeenCalledWith('/real-estate-simulations/1');
    expect(result.current.simulations).toHaveLength(1);
    expect(result.current.simulations[0].metadata.id).toBe('2');
  });

  it('should handle delete API error', async () => {
    mockedApiClient.delete.mockRejectedValue(new Error('Delete failed'));

    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.deleteSimulation('1');
    });

    expect(success).toBe(false);
    expect(result.current.error).toBe('Erreur lors de la suppression');
  });

  it('should rename a simulation', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.renameSimulation('1', 'Renamed Sim');
    });

    expect(success).toBe(true);
    expect(mockedApiClient.put).toHaveBeenCalledWith(
      '/real-estate-simulations/1',
      expect.objectContaining({
        name: 'Renamed Sim',
      })
    );
    expect(result.current.simulations.find(s => s.metadata.id === '1')?.metadata.name).toBe(
      'Renamed Sim'
    );
  });

  it('should reject rename with empty name', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.renameSimulation('1', '  ');
    });

    expect(success).toBe(false);
    expect(result.current.error).toBe('Le nom ne peut pas être vide');
  });

  it('should reject rename for non-existent simulation', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.renameSimulation('999', 'New Name');
    });

    expect(success).toBe(false);
    expect(result.current.error).toBe('Simulation non trouvée');
  });

  it('should filter simulations by type', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    const buyRentSims = result.current.getSimulationsByType('buy_rent');
    expect(buyRentSims).toHaveLength(1);
    expect(buyRentSims[0].metadata.name).toBe('Test Simulation');

    const rentalSims = result.current.getSimulationsByType('rental_investment');
    expect(rentalSims).toHaveLength(1);
    expect(rentalSims[0].metadata.name).toBe('Rental Sim');
  });

  it('should check if simulation name exists', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.hasSimulationWithName('Test Simulation')).toBe(true);
    expect(result.current.hasSimulationWithName('test simulation')).toBe(true); // case-insensitive
    expect(result.current.hasSimulationWithName('Nonexistent')).toBe(false);
  });

  it('should clear all simulations', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.clearAll();
    });

    expect(success).toBe(true);
    expect(result.current.simulations).toEqual([]);
    expect(mockedApiClient.delete).toHaveBeenCalledTimes(2); // two sims deleted
  });

  it('should not clear when confirm is declined', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.clearAll();
    });

    expect(success).toBe(false);
    expect(result.current.simulations).toHaveLength(2);
    expect(mockedApiClient.delete).not.toHaveBeenCalled();
  });

  it('should export all simulations as JSON', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    const exported = result.current.exportAll();
    const parsed = JSON.parse(exported);

    expect(parsed).toHaveLength(2);
    expect(parsed[0].metadata.name).toBe('Test Simulation');
  });

  it('should import valid simulations', async () => {
    // After import, refreshSimulations is called
    mockedApiClient.get
      .mockResolvedValueOnce({ data: [mockApiSimulation, mockApiSimulation2] }) // initial load
      .mockResolvedValueOnce({
        data: [
          mockApiSimulation,
          mockApiSimulation2,
          { ...mockApiSimulation, id: 3, name: 'Imported' },
        ],
      }); // refresh after import

    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    const importData: any[] = [
      {
        metadata: {
          id: 'imp1',
          name: 'Imported Sim',
          type: 'buy_rent',
          createdAt: new Date(),
          updatedAt: new Date(),
        },
        data: DEFAULT_BUY_RENT_INPUTS,
      },
    ];

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.importAll(JSON.stringify(importData));
    });

    expect(success).toBe(true);
    expect(mockedApiClient.post).toHaveBeenCalledWith(
      '/real-estate-simulations',
      expect.objectContaining({
        name: 'Imported Sim',
        simulationType: 'buy_rent',
      })
    );
  });

  it('should reject import of non-array JSON', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.importAll(JSON.stringify({ not: 'an array' }));
    });

    expect(success).toBe(false);
    expect(result.current.error).toBe('Format invalide : un tableau est attendu');
  });

  it('should reject import with no valid simulations', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    const invalidData = [{ metadata: { id: '1', name: 'Bad' }, data: { noValidFields: true } }];

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.importAll(JSON.stringify(invalidData));
    });

    expect(success).toBe(false);
    expect(result.current.error).toBe('Aucune simulation valide trouvée dans le fichier');
  });

  it('should reject import when exceeding max simulations', async () => {
    // Already have 50 simulations
    const manySims = Array.from({ length: 50 }, (_, i) => ({
      ...mockApiSimulation,
      id: i + 1,
      name: `Sim ${i + 1}`,
    }));
    mockedApiClient.get.mockResolvedValue({ data: manySims });

    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    const importData = [
      {
        metadata: {
          id: 'imp1',
          name: 'One More',
          type: 'buy_rent',
          createdAt: new Date(),
          updatedAt: new Date(),
        },
        data: DEFAULT_BUY_RENT_INPUTS,
      },
    ];

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.importAll(JSON.stringify(importData));
    });

    expect(success).toBe(false);
    expect(result.current.error).toContain('50');
  });

  it('should handle import of invalid JSON string', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.importAll('not valid json');
    });

    expect(success).toBe(false);
    expect(result.current.error).toContain('import');
  });

  it('should refresh simulations from API', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    // Reset mock call count
    mockedApiClient.get.mockClear();
    mockedApiClient.get.mockResolvedValue({ data: [mockApiSimulation] });

    await act(async () => {
      await result.current.refreshSimulations();
    });

    expect(mockedApiClient.get).toHaveBeenCalledWith('/real-estate-simulations');
    expect(result.current.simulations).toHaveLength(1);
  });

  it('should sanitize simulation name by trimming and truncating', async () => {
    const { result } = renderHook(() => useSimulationStorage());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    const longName = 'A'.repeat(150);
    await act(async () => {
      await result.current.saveSimulation(longName, 'buy_rent', DEFAULT_BUY_RENT_INPUTS);
    });

    expect(mockedApiClient.post).toHaveBeenCalledWith(
      '/real-estate-simulations',
      expect.objectContaining({
        name: 'A'.repeat(100), // truncated to 100 chars
      })
    );
  });
});
