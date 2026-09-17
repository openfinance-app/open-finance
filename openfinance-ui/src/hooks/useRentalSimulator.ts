/**
 * useRentalSimulator Hook
 *
 * React hook for managing Rental Simulator state and calculations
 * Requirements: REQ-2.4.x, REQ-2.5.x, REQ-2.6.2, REQ-2.6.3
 */

import { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import type {
  InvestmentInputs,
  InvestmentResults,
  RegimeCalculationResult,
  TaxRegime,
  ValidationError,
  SharedPropertyData,
} from '@/types/realEstateTools';
import { DEFAULT_INVESTMENT_INPUTS, FURNITURE_VALUES } from '@/types/realEstateTools';
import { RealEstateCalculationService } from '@/services/realEstateCalculationService';
import { validateInvestmentInputs } from '@/validators/realEstateValidators';
import { getRecommendedRegime } from '@/utils/taxRegimeCalculations';

export interface UseRentalSimulatorReturn {
  // State
  inputs: InvestmentInputs;
  results: InvestmentResults | null;
  isCalculating: boolean;
  errors: ValidationError[];

  // Derived values
  recommendedRegime: TaxRegime | null;
  eligibleRegimes: TaxRegime[];

  // Actions
  updateCreditInput: (field: keyof InvestmentInputs['credit'], value: number) => void;
  updatePropertyInput: (field: keyof InvestmentInputs['property'], value: string | number) => void;
  updateRevenueInput: (field: keyof InvestmentInputs['revenue'], value: number) => void;
  updateExpenseInput: (field: keyof InvestmentInputs['expenses'], value: number) => void;
  calculate: () => void;
  reset: () => void;
  setInputs: (inputs: InvestmentInputs) => void;
  loadSharedData: (sharedData: SharedPropertyData) => void;

  // Helpers
  getRegimeResult: (regime: TaxRegime) => RegimeCalculationResult | null;
  isRegimeEligible: (regime: TaxRegime) => boolean;
}

/**
 * Create default investment inputs with optional shared data
 */
function createDefaultInputs(sharedData?: SharedPropertyData): InvestmentInputs {
  return {
    credit: sharedData?.credit || {
      monthlyPayment: 0,
      annualCost: 0,
      totalCost: 0,
      assurance: 0,
      bankFees: 0,
    },
    property: {
      totalPrice: sharedData?.totalPrice || 0,
      furnishingType: 'unfurnished',
      furnitureValue: 0,
    },
    revenue: DEFAULT_INVESTMENT_INPUTS.revenue,
    expenses: {
      ...DEFAULT_INVESTMENT_INPUTS.expenses,
      propertyTax: sharedData?.propertyTax || DEFAULT_INVESTMENT_INPUTS.expenses.propertyTax,
      nonRecoverableCharges:
        sharedData?.coOwnershipCharges || DEFAULT_INVESTMENT_INPUTS.expenses.nonRecoverableCharges,
    },
  };
}

/**
 * Hook for managing Rental Simulator state and calculations
 *
 * @param sharedData - Optional shared data from Buy/Rent comparator
 * @returns Hook state and actions
 */
export function useRentalSimulator(sharedData?: SharedPropertyData): UseRentalSimulatorReturn {
  // Main state
  const [inputs, setInputsState] = useState<InvestmentInputs>(() =>
    createDefaultInputs(sharedData)
  );
  const [results, setResults] = useState<InvestmentResults | null>(null);
  const [isCalculating, setIsCalculating] = useState(false);
  const [errors, setErrors] = useState<ValidationError[]>([]);

  // Refs for cleanup
  const calculationTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Auto-update furniture value when type changes
  useEffect(() => {
    const furnitureValue = FURNITURE_VALUES[inputs.property.furnishingType];
    if (furnitureValue !== inputs.property.furnitureValue) {
      setInputsState(prev => ({
        ...prev,
        property: {
          ...prev.property,
          furnitureValue,
        },
      }));
    }
  }, [inputs.property.furnishingType]);

  // Validate inputs when they change
  useEffect(() => {
    const validationErrors = validateInvestmentInputs(inputs);
    setErrors(validationErrors);
  }, [inputs]);

  // Calculate derived values
  const recommendedRegime = useMemo(() => {
    if (!results) return null;
    return getRecommendedRegime(results);
  }, [results]);

  const eligibleRegimes = useMemo(() => {
    if (!results) return [];
    const regimes: TaxRegime[] = ['micro_foncier', 'reel_foncier', 'lmnp_reel', 'micro_bic'];
    return regimes.filter(regime => {
      const result =
        results[
          regime === 'micro_foncier'
            ? 'microFoncier'
            : regime === 'reel_foncier'
              ? 'reelFoncier'
              : regime === 'lmnp_reel'
                ? 'lmnpReel'
                : 'microBic'
        ];
      return result.eligible;
    });
  }, [results]);

  /**
   * Update a credit input field
   */
  const updateCreditInput = useCallback(
    (field: keyof InvestmentInputs['credit'], value: number) => {
      setInputsState(prev => ({
        ...prev,
        credit: {
          ...prev.credit,
          [field]: value,
        },
      }));
    },
    []
  );

  /**
   * Update a property input field
   */
  const updatePropertyInput = useCallback(
    (field: keyof InvestmentInputs['property'], value: string | number) => {
      setInputsState(prev => ({
        ...prev,
        property: {
          ...prev.property,
          [field]: value,
        },
      }));
    },
    []
  );

  /**
   * Update a revenue input field
   */
  const updateRevenueInput = useCallback(
    (field: keyof InvestmentInputs['revenue'], value: number) => {
      setInputsState(prev => ({
        ...prev,
        revenue: {
          ...prev.revenue,
          [field]: value,
        },
      }));
    },
    []
  );

  /**
   * Update an expense input field
   */
  const updateExpenseInput = useCallback(
    (field: keyof InvestmentInputs['expenses'], value: number) => {
      setInputsState(prev => ({
        ...prev,
        expenses: {
          ...prev.expenses,
          [field]: value,
        },
      }));
    },
    []
  );

  /**
   * Run the calculation
   */
  const calculate = useCallback(() => {
    // Clear any pending calculation
    if (calculationTimeoutRef.current) {
      clearTimeout(calculationTimeoutRef.current);
    }

    // Check for validation errors
    const validationErrors = validateInvestmentInputs(inputs);
    if (validationErrors.length > 0) {
      setErrors(validationErrors);
      return;
    }

    setIsCalculating(true);

    // Use setTimeout to allow UI to show loading state
    calculationTimeoutRef.current = setTimeout(() => {
      try {
        const calculationResults = RealEstateCalculationService.calculateInvestment(inputs);
        setResults(calculationResults);
        setErrors([]);
      } catch (error) {
        console.error('Calculation error:', error);
        setErrors([
          {
            field: 'general',
            message: 'Une erreur est survenue lors du calcul. Veuillez vérifier vos données.',
          },
        ]);
      } finally {
        setIsCalculating(false);
        calculationTimeoutRef.current = null;
      }
    }, 0);
  }, [inputs]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (calculationTimeoutRef.current) {
        clearTimeout(calculationTimeoutRef.current);
      }
    };
  }, []);

  /**
   * Reset to default values
   */
  const reset = useCallback(() => {
    setInputsState(createDefaultInputs(sharedData));
    setResults(null);
    setErrors([]);
  }, [sharedData]);

  /**
   * Set all inputs at once (for loading saved simulations)
   */
  const setInputs = useCallback((newInputs: InvestmentInputs) => {
    setInputsState(newInputs);
  }, []);

  /**
   * Load shared data from Buy/Rent comparator
   */
  const loadSharedData = useCallback((data: SharedPropertyData) => {
    setInputsState(prev => ({
      ...prev,
      credit: data.credit,
      property: {
        ...prev.property,
        totalPrice: data.totalPrice,
      },
      expenses: {
        ...prev.expenses,
        propertyTax: data.propertyTax,
        nonRecoverableCharges: data.coOwnershipCharges,
      },
    }));
  }, []);

  /**
   * Get result for a specific regime
   */
  const getRegimeResult = useCallback(
    (regime: TaxRegime): RegimeCalculationResult | null => {
      if (!results) return null;
      return results[
        regime === 'micro_foncier'
          ? 'microFoncier'
          : regime === 'reel_foncier'
            ? 'reelFoncier'
            : regime === 'lmnp_reel'
              ? 'lmnpReel'
              : 'microBic'
      ];
    },
    [results]
  );

  /**
   * Check if a regime is eligible
   */
  const isRegimeEligible = useCallback(
    (regime: TaxRegime): boolean => {
      const result = getRegimeResult(regime);
      return result?.eligible ?? false;
    },
    [getRegimeResult]
  );

  return {
    // State
    inputs,
    results,
    isCalculating,
    errors,

    // Derived values
    recommendedRegime,
    eligibleRegimes,

    // Actions
    updateCreditInput,
    updatePropertyInput,
    updateRevenueInput,
    updateExpenseInput,
    calculate,
    reset,
    setInputs,
    loadSharedData,

    // Helpers
    getRegimeResult,
    isRegimeEligible,
  };
}

export default useRentalSimulator;
