import { useState, useCallback } from 'react';
import { calculateCompoundInterest } from '../services/compoundInterestApi';
import {
  DEFAULT_COMPOUND_INTEREST_INPUT,
  type CompoundInterestInput,
  type CompoundInterestResult,
} from '../types/calculator';

interface CompoundInterestState {
  input: CompoundInterestInput;
  result: CompoundInterestResult | null;
  isLoading: boolean;
  error: string | null;
}

const defaultState: CompoundInterestState = {
  input: DEFAULT_COMPOUND_INTEREST_INPUT,
  result: null,
  isLoading: false,
  error: null,
};

export function useCompoundInterest() {
  const [state, setState] = useState<CompoundInterestState>(defaultState);

  const updateInput = useCallback(
    <K extends keyof CompoundInterestInput>(key: K, value: CompoundInterestInput[K]) => {
      setState(prev => ({ ...prev, input: { ...prev.input, [key]: value }, error: null }));
    },
    []
  );

  const resetInputs = useCallback(() => {
    setState(prev => ({
      ...prev,
      input: DEFAULT_COMPOUND_INTEREST_INPUT,
      result: null,
      error: null,
    }));
  }, []);

  const calculate = useCallback(async () => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      const result = await calculateCompoundInterest(state.input);
      setState(prev => ({ ...prev, result, isLoading: false }));
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : 'Calculation failed. Please check your inputs.';
      setState(prev => ({ ...prev, error: message, isLoading: false }));
    }
  }, [state.input]);

  return {
    input: state.input,
    result: state.result,
    isLoading: state.isLoading,
    error: state.error,
    updateInput,
    resetInputs,
    calculate,
  };
}
