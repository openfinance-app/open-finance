/**
 * useBuyRentCalculations Hook
 *
 * React hook for managing Buy/Rent calculator state and calculations
 * Requirements: REQ-1.2.2, REQ-1.2.3, REQ-3.1.2
 */

import { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import type {
  BuyRentInputs,
  BuyRentResults,
  ValidationError,
  YearNAnalysis,
} from '@/types/realEstateTools';
import { DEFAULT_BUY_RENT_INPUTS } from '@/types/realEstateTools';
import { RealEstateCalculationService } from '@/services/realEstateCalculationService';
import { validateBuyRentInputs } from '@/validators/realEstateValidators';

export interface UseBuyRentCalculationsReturn {
  // State
  inputs: BuyRentInputs;
  results: BuyRentResults | null;
  isCalculating: boolean;
  errors: ValidationError[];

  // Derived values (real-time)
  derivedValues: {
    totalPrice: number;
    borrowedAmount: number;
    monthlyPayment: number;
    minimumDownPayment: number;
    suggestedMonthlySavings: number;
  };

  // Actions
  updatePurchaseInput: (field: keyof BuyRentInputs['purchase'], value: number | boolean) => void;
  updateRentalInput: (field: keyof BuyRentInputs['rental'], value: number) => void;
  updateMarketInput: (field: keyof BuyRentInputs['market'], value: number) => void;
  updateResaleInput: (field: keyof BuyRentInputs['resale'], value: number) => void;
  calculate: () => void;
  reset: () => void;
  setInputs: (inputs: BuyRentInputs) => void;

  // Analysis
  getYearNAnalysis: (year: number) => YearNAnalysis | null;
  isValidResaleYear: boolean;
}

/**
 * Hook for managing Buy/Rent calculator state and calculations
 *
 * @param initialInputs - Optional initial input values
 * @returns Hook state and actions
 */
export function useBuyRentCalculations(
  initialInputs: BuyRentInputs = DEFAULT_BUY_RENT_INPUTS
): UseBuyRentCalculationsReturn {
  // Main state
  const [inputs, setInputsState] = useState<BuyRentInputs>(initialInputs);
  const [results, setResults] = useState<BuyRentResults | null>(null);
  const [isCalculating, setIsCalculating] = useState(false);
  const [errors, setErrors] = useState<ValidationError[]>([]);

  // Refs for cleanup
  const calculationTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Calculate derived values in real-time
  const derivedValues = useMemo(() => {
    return RealEstateCalculationService.calculateDerivedValues(inputs);
  }, [inputs]);

  // Auto-update savings fields when costs change (prevent infinite loop)
  useEffect(() => {
    const shouldUpdateInitialSavings = inputs.rental.initialSavings !== inputs.purchase.downPayment;
    const shouldUpdateMonthlySavings =
      inputs.rental.monthlySavings !== derivedValues.suggestedMonthlySavings;

    if (shouldUpdateInitialSavings || shouldUpdateMonthlySavings) {
      setInputsState(prev => ({
        ...prev,
        rental: {
          ...prev.rental,
          initialSavings: prev.purchase.downPayment,
          monthlySavings: derivedValues.suggestedMonthlySavings,
        },
      }));
    }
  }, [
    derivedValues.suggestedMonthlySavings,
    inputs.purchase.downPayment,
    inputs.rental.initialSavings,
    inputs.rental.monthlySavings,
  ]);

  // Validate inputs when they change
  useEffect(() => {
    const validationErrors = validateBuyRentInputs(inputs);
    setErrors(validationErrors);
  }, [inputs]);

  /**
   * Update a purchase input field
   */
  const updatePurchaseInput = useCallback(
    (field: keyof BuyRentInputs['purchase'], value: number | boolean) => {
      setInputsState(prev => ({
        ...prev,
        purchase: {
          ...prev.purchase,
          [field]: value,
        },
      }));
    },
    []
  );

  /**
   * Update a rental input field
   */
  const updateRentalInput = useCallback((field: keyof BuyRentInputs['rental'], value: number) => {
    setInputsState(prev => ({
      ...prev,
      rental: {
        ...prev.rental,
        [field]: value,
      },
    }));
  }, []);

  /**
   * Update a market input field
   */
  const updateMarketInput = useCallback((field: keyof BuyRentInputs['market'], value: number) => {
    setInputsState(prev => ({
      ...prev,
      market: {
        ...prev.market,
        [field]: value,
      },
    }));
  }, []);

  /**
   * Update a resale input field
   */
  const updateResaleInput = useCallback((field: keyof BuyRentInputs['resale'], value: number) => {
    setInputsState(prev => ({
      ...prev,
      resale: {
        ...prev.resale,
        [field]: value,
      },
    }));
  }, []);

  /**
   * Run the calculation
   */
  const calculate = useCallback(() => {
    // Clear any pending calculation
    if (calculationTimeoutRef.current) {
      clearTimeout(calculationTimeoutRef.current);
    }

    // Check for validation errors
    const validationErrors = validateBuyRentInputs(inputs);
    if (validationErrors.length > 0) {
      setErrors(validationErrors);
      return;
    }

    setIsCalculating(true);

    // Use setTimeout to allow UI to show loading state
    calculationTimeoutRef.current = setTimeout(() => {
      try {
        const calculationResults = RealEstateCalculationService.calculateBuyRentComparison(inputs);
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
    setInputsState(DEFAULT_BUY_RENT_INPUTS);
    setResults(null);
    setErrors([]);
  }, []);

  /**
   * Set all inputs at once (for loading saved simulations)
   */
  const setInputs = useCallback((newInputs: BuyRentInputs) => {
    setInputsState(newInputs);
  }, []);

  /**
   * Get analysis for a specific year
   */
  const getYearNAnalysis = useCallback(
    (year: number): YearNAnalysis | null => {
      if (!results) return null;
      return RealEstateCalculationService.calculateYearNAnalysis(results, year);
    },
    [results]
  );

  // Check if resale year is valid
  const isValidResaleYear = useMemo(() => {
    return RealEstateCalculationService.isValidResaleYear(inputs);
  }, [inputs]);

  return {
    // State
    inputs,
    results,
    isCalculating,
    errors,

    // Derived values
    derivedValues,

    // Actions
    updatePurchaseInput,
    updateRentalInput,
    updateMarketInput,
    updateResaleInput,
    calculate,
    reset,
    setInputs,

    // Analysis
    getYearNAnalysis,
    isValidResaleYear,
  };
}

export default useBuyRentCalculations;
