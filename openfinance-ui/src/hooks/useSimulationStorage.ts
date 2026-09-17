/**
 * useSimulationStorage Hook - Backend API Version
 *
 * React hook for persisting simulations to backend API
 * Requirements: REQ-1.7.x, REQ-3.4.x, REQ-6.2.x, REQ-8.1.1
 */

import { useState, useEffect, useCallback } from 'react';
import apiClient from '@/services/apiClient';
import type {
  SavedSimulation,
  SimulationType,
  BuyRentInputs,
  InvestmentInputs,
} from '@/types/realEstateTools';

const MAX_SIMULATIONS = 50;

export interface UseSimulationStorageReturn {
  simulations: SavedSimulation[];
  isLoading: boolean;
  error: string | null;

  // Actions
  saveSimulation: (
    name: string,
    type: SimulationType,
    data: BuyRentInputs | InvestmentInputs
  ) => Promise<boolean>;
  loadSimulation: (id: string) => SavedSimulation | null;
  deleteSimulation: (id: string) => Promise<boolean>;
  renameSimulation: (id: string, newName: string) => Promise<boolean>;

  // Queries
  getSimulationsByType: (type: SimulationType) => SavedSimulation[];
  hasSimulationWithName: (name: string) => boolean;

  // Utility
  clearAll: () => Promise<boolean>;
  exportAll: () => string;
  importAll: (jsonString: string) => Promise<boolean>;
  refreshSimulations: () => Promise<void>;
}

interface ApiSimulation {
  id: number;
  name: string;
  simulationType: SimulationType;
  data: string;
  createdAt: string;
  updatedAt: string;
}

function sanitizeName(name: string): string {
  return name.trim().slice(0, 100);
}

function validateSimulation(data: unknown): data is BuyRentInputs | InvestmentInputs {
  if (!data || typeof data !== 'object') return false;
  const obj = data as Record<string, unknown>;

  if ('purchase' in obj && 'rental' in obj) {
    return true;
  }

  if ('credit' in obj && 'property' in obj && 'revenue' in obj) {
    return true;
  }

  return false;
}

function parseApiSimulation(apiSim: ApiSimulation): SavedSimulation {
  return {
    metadata: {
      id: String(apiSim.id),
      name: apiSim.name,
      type: apiSim.simulationType,
      createdAt: new Date(apiSim.createdAt),
      updatedAt: new Date(apiSim.updatedAt),
    },
    data: JSON.parse(apiSim.data),
  };
}

export function useSimulationStorage(): UseSimulationStorageReturn {
  const [simulations, setSimulations] = useState<SavedSimulation[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load simulations from API on mount
  const refreshSimulations = useCallback(async () => {
    try {
      setIsLoading(true);
      const response = await apiClient.get<ApiSimulation[]>('/real-estate-simulations');
      const parsed = response.data.map(parseApiSimulation);
      setSimulations(parsed);
      setError(null);
    } catch (err) {
      console.error('Failed to load simulations:', err);
      setError('Erreur lors du chargement des simulations');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshSimulations();
  }, [refreshSimulations]);

  const saveSimulation = useCallback(
    async (
      name: string,
      type: SimulationType,
      data: BuyRentInputs | InvestmentInputs
    ): Promise<boolean> => {
      try {
        const sanitizedName = sanitizeName(name);

        if (!sanitizedName) {
          setError('Le nom de la simulation ne peut pas être vide');
          return false;
        }

        if (simulations.length >= MAX_SIMULATIONS) {
          setError(`Limite de ${MAX_SIMULATIONS} simulations atteinte.`);
          return false;
        }

        const response = await apiClient.post<ApiSimulation>('/real-estate-simulations', {
          name: sanitizedName,
          simulationType: type,
          data: JSON.stringify(data),
        });

        const newSimulation = parseApiSimulation(response.data);
        setSimulations(prev => [...prev, newSimulation]);
        setError(null);
        return true;
      } catch (err: any) {
        console.error('Failed to save simulation:', err);
        const message = err.response?.data?.message || 'Erreur lors de la sauvegarde';
        setError(message);
        return false;
      }
    },
    [simulations.length]
  );

  const loadSimulation = useCallback(
    (id: string): SavedSimulation | null => {
      return simulations.find(s => s.metadata.id === id) || null;
    },
    [simulations]
  );

  const deleteSimulation = useCallback(async (id: string): Promise<boolean> => {
    try {
      await apiClient.delete(`/real-estate-simulations/${id}`);
      setSimulations(prev => prev.filter(s => s.metadata.id !== id));
      setError(null);
      return true;
    } catch (err) {
      console.error('Failed to delete simulation:', err);
      setError('Erreur lors de la suppression');
      return false;
    }
  }, []);

  const renameSimulation = useCallback(
    async (id: string, newName: string): Promise<boolean> => {
      try {
        const sanitizedName = sanitizeName(newName);

        if (!sanitizedName) {
          setError('Le nom ne peut pas être vide');
          return false;
        }

        const simulation = simulations.find(s => s.metadata.id === id);
        if (!simulation) {
          setError('Simulation non trouvée');
          return false;
        }

        await apiClient.put<ApiSimulation>(`/real-estate-simulations/${id}`, {
          name: sanitizedName,
          simulationType: simulation.metadata.type,
          data: JSON.stringify(simulation.data),
        });

        setSimulations(prev =>
          prev.map(s =>
            s.metadata.id === id
              ? {
                  ...s,
                  metadata: {
                    ...s.metadata,
                    name: sanitizedName,
                    updatedAt: new Date(),
                  },
                }
              : s
          )
        );

        setError(null);
        return true;
      } catch (err) {
        console.error('Failed to rename simulation:', err);
        setError('Erreur lors du renommage');
        return false;
      }
    },
    [simulations]
  );

  const getSimulationsByType = useCallback(
    (type: SimulationType): SavedSimulation[] => {
      return simulations.filter(s => s.metadata.type === type);
    },
    [simulations]
  );

  const hasSimulationWithName = useCallback(
    (name: string): boolean => {
      const normalizedName = name.toLowerCase().trim();
      return simulations.some(s => s.metadata.name.toLowerCase().trim() === normalizedName);
    },
    [simulations]
  );

  const clearAll = useCallback(async (): Promise<boolean> => {
    try {
      if (confirm('Voulez-vous vraiment supprimer toutes les simulations ?')) {
        // Delete each simulation individually
        await Promise.all(
          simulations.map(s => apiClient.delete(`/real-estate-simulations/${s.metadata.id}`))
        );
        setSimulations([]);
        setError(null);
        return true;
      }
      return false;
    } catch (err) {
      console.error('Failed to clear simulations:', err);
      setError('Erreur lors de la suppression');
      return false;
    }
  }, [simulations]);

  const exportAll = useCallback((): string => {
    return JSON.stringify(simulations, null, 2);
  }, [simulations]);

  const importAll = useCallback(
    async (jsonString: string): Promise<boolean> => {
      try {
        const parsed = JSON.parse(jsonString);

        if (!Array.isArray(parsed)) {
          setError('Format invalide : un tableau est attendu');
          return false;
        }

        const validSimulations = parsed.filter((s): s is SavedSimulation => {
          return (
            s &&
            typeof s.metadata === 'object' &&
            typeof s.data === 'object' &&
            validateSimulation(s.data)
          );
        });

        if (validSimulations.length === 0) {
          setError('Aucune simulation valide trouvée dans le fichier');
          return false;
        }

        if (simulations.length + validSimulations.length > MAX_SIMULATIONS) {
          setError(`Import impossible : limite de ${MAX_SIMULATIONS} simulations dépassée`);
          return false;
        }

        // Save each imported simulation to the backend
        for (const sim of validSimulations) {
          await apiClient.post('/real-estate-simulations', {
            name: sim.metadata.name,
            simulationType: sim.metadata.type,
            data: JSON.stringify(sim.data),
          });
        }

        // Refresh the list
        await refreshSimulations();
        setError(null);
        return true;
      } catch (err) {
        console.error('Failed to import simulations:', err);
        setError("Erreur lors de l'import : format JSON invalide");
        return false;
      }
    },
    [simulations.length, refreshSimulations]
  );

  return {
    simulations,
    isLoading,
    error,

    saveSimulation,
    loadSimulation,
    deleteSimulation,
    renameSimulation,

    getSimulationsByType,
    hasSimulationWithName,

    clearAll,
    exportAll,
    importAll,
    refreshSimulations,
  };
}

export default useSimulationStorage;
