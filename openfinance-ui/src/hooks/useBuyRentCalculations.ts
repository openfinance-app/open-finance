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
import i18n from '@/i18n';
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
  calculate: (nextInputs?: BuyRentInputs) => void;
  reset: () => void;
  setInputs: (inputs: BuyRentInputs) => void;

  // Analysis
  getYearNAnalysis: (year: number) => YearNAnalysis | null;
  isValidResaleYear: boolean;
}

/** Savings and the calculation always use the same input snapshot. */
function normalizeInputs(inputs: BuyRentInputs): BuyRentInputs {
  const { suggestedMonthlySavings } = RealEstateCalculationService.calculateDerivedValues(inputs);
  return {
    ...inputs,
    rental: {
      ...inputs.rental,
      initialSavings: Math.max(0, inputs.purchase.downPayment - inputs.rental.securityDeposit),
      monthlySavings: suggestedMonthlySavings,
    },
  };
}

export function useBuyRentCalculations(
  initialInputs: BuyRentInputs = DEFAULT_BUY_RENT_INPUTS
): UseBuyRentCalculationsReturn {
  const [inputs, setInputsState] = useState(() => normalizeInputs(initialInputs));
  const [results, setResults] = useState<BuyRentResults | null>(null);
  const [isCalculating, setIsCalculating] = useState(false);
  const [calculationErrors, setCalculationErrors] = useState<ValidationError[]>([]);
  const calculationTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const edited = useRef(false);
  const derivedValues = useMemo(
    () => RealEstateCalculationService.calculateDerivedValues(inputs),
    [inputs]
  );
  const errors = [...validateBuyRentInputs(inputs), ...calculationErrors];

  const invalidate = useCallback(() => {
    if (calculationTimeoutRef.current !== null) clearTimeout(calculationTimeoutRef.current);
    calculationTimeoutRef.current = null;
    setIsCalculating(false);
    setResults(null);
    setCalculationErrors([]);
  }, []);
  useEffect(() => {
    if (!edited.current) {
      invalidate();
      setInputsState(normalizeInputs(initialInputs));
    }
  }, [initialInputs, invalidate]);
  useEffect(
    () => () => {
      if (calculationTimeoutRef.current !== null) clearTimeout(calculationTimeoutRef.current);
    },
    []
  );

  const update = useCallback(
    (change: (previous: BuyRentInputs) => BuyRentInputs) => {
      edited.current = true;
      invalidate();
      setInputsState(previous => normalizeInputs(change(previous)));
    },
    [invalidate]
  );
  const updatePurchaseInput = useCallback(
    (field: keyof BuyRentInputs['purchase'], value: number | boolean) =>
      update(previous => ({ ...previous, purchase: { ...previous.purchase, [field]: value } })),
    [update]
  );
  const updateRentalInput = useCallback(
    (field: keyof BuyRentInputs['rental'], value: number) =>
      update(previous => ({ ...previous, rental: { ...previous.rental, [field]: value } })),
    [update]
  );
  const updateMarketInput = useCallback(
    (field: keyof BuyRentInputs['market'], value: number) =>
      update(previous => ({ ...previous, market: { ...previous.market, [field]: value } })),
    [update]
  );
  const updateResaleInput = useCallback(
    (field: keyof BuyRentInputs['resale'], value: number) =>
      update(previous => ({ ...previous, resale: { ...previous.resale, [field]: value } })),
    [update]
  );
  const setInputs = useCallback((next: BuyRentInputs) => update(() => next), [update]);
  const reset = useCallback(() => {
    edited.current = false;
    invalidate();
    setInputsState(normalizeInputs(initialInputs));
  }, [initialInputs, invalidate]);

  const calculate = useCallback(
    (nextInputs?: BuyRentInputs) => {
      invalidate();
      const snapshot = normalizeInputs(nextInputs ?? inputs);
      if (nextInputs) {
        edited.current = true;
        setInputsState(snapshot);
      }
      if (validateBuyRentInputs(snapshot).length) return;
      setIsCalculating(true);
      calculationTimeoutRef.current = setTimeout(() => {
        try {
          setResults(RealEstateCalculationService.calculateBuyRentComparison(snapshot));
        } catch {
          setCalculationErrors([
            { field: 'general', message: i18n.t('validation.calculation', { ns: 'realEstate' }) },
          ]);
        } finally {
          calculationTimeoutRef.current = null;
          setIsCalculating(false);
        }
      }, 0);
    },
    [inputs, invalidate]
  );
  const getYearNAnalysis = useCallback(
    (year: number): YearNAnalysis | null =>
      results
        ? RealEstateCalculationService.calculateYearNAnalysis(
            results,
            year,
            inputs.resale.resaleFeesPercent
          )
        : null,
    [results, inputs.resale.resaleFeesPercent]
  );
  return {
    inputs,
    results,
    isCalculating,
    errors,
    derivedValues,
    updatePurchaseInput,
    updateRentalInput,
    updateMarketInput,
    updateResaleInput,
    calculate,
    reset,
    setInputs,
    getYearNAnalysis,
    isValidResaleYear: RealEstateCalculationService.isValidResaleYear(inputs),
  };
}

export default useBuyRentCalculations;
