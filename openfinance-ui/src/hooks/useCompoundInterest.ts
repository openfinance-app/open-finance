import { useState, useCallback, useRef } from 'react';
import axios from 'axios';
import { validateCompoundInterest } from '@/validators/calculatorValidation';
import i18n from '@/i18n';
import { calculateCompoundInterest } from '@/services/compoundInterestApi';
import {
  DEFAULT_COMPOUND_INTEREST_INPUT,
  type CompoundInterestInput,
  type CompoundInterestResult,
} from '@/types/calculator';

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
  const generation = useRef(0);

  const updateInput = useCallback(
    <K extends keyof CompoundInterestInput>(key: K, value: CompoundInterestInput[K]) => {
      generation.current += 1;
      setState(prev => ({
        ...prev,
        input: { ...prev.input, [key]: value },
        result: null,
        isLoading: false,
        error: null,
      }));
    },
    []
  );

  const resetInputs = useCallback(() => {
    generation.current += 1;
    setState(prev => ({
      ...prev,
      input: DEFAULT_COMPOUND_INTEREST_INPUT,
      result: null,
      isLoading: false,
      error: null,
    }));
  }, []);

  const calculate = useCallback(async () => {
    const requestGeneration = ++generation.current;
    const error = validateCompoundInterest(state.input);
    setState(prev => ({ ...prev, isLoading: !error, result: null, error }));
    if (error) return;
    try {
      const result = await calculateCompoundInterest(state.input);
      if (requestGeneration !== generation.current) return;
      setState(prev => ({ ...prev, result, isLoading: false }));
    } catch (err: unknown) {
      if (requestGeneration !== generation.current) return;
      const validation = axios.isAxiosError(err) ? err.response?.data?.validationErrors : null;
      const messages =
        validation && typeof validation === 'object'
          ? Object.values(validation).filter((value): value is string => typeof value === 'string')
          : [];
      const message = messages.length
        ? messages.join(' ')
        : i18n.t('validation.calculationFailed', { ns: 'tools' });
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
