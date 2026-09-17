import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useBuyRentCalculations } from './useBuyRentCalculations';
import { DEFAULT_BUY_RENT_INPUTS } from '@/types/realEstateTools';
import { RealEstateCalculationService } from '@/services/realEstateCalculationService';
import * as validators from '@/validators/realEstateValidators';

vi.mock('@/services/realEstateCalculationService');
vi.mock('@/validators/realEstateValidators');

const mockedService = RealEstateCalculationService as any;
const mockedValidators = validators as any;

describe('useBuyRentCalculations', () => {
  const mockDerivedValues = {
    totalPrice: 250000,
    borrowedAmount: 200000,
    monthlyPayment: 950,
    minimumDownPayment: 5000,
    suggestedMonthlySavings: 300,
  };

  const mockResults = {
    summary: {
      breakEvenYear: 8,
      totalBuyCost: 300000,
      totalRentCost: 280000,
    },
    years: [],
    buyNetCost: 300000,
    rentNetCost: 280000,
    recommendation: 'buy',
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    mockedService.calculateDerivedValues.mockReturnValue(mockDerivedValues);
    mockedService.calculateBuyRentComparison.mockReturnValue(mockResults);
    mockedService.isValidResaleYear.mockReturnValue(true);
    mockedService.calculateYearNAnalysis.mockReturnValue({ year: 5, buyAdvantage: 10000 });
    mockedValidators.validateBuyRentInputs.mockReturnValue([]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should initialize with default inputs', () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    expect(result.current.inputs).toEqual(
      expect.objectContaining({
        purchase: expect.any(Object),
        rental: expect.any(Object),
        market: expect.any(Object),
        resale: expect.any(Object),
      })
    );
    expect(result.current.results).toBeNull();
    expect(result.current.isCalculating).toBe(false);
    expect(result.current.errors).toEqual([]);
  });

  it('should initialize with custom inputs', () => {
    const customInputs = {
      ...DEFAULT_BUY_RENT_INPUTS,
      purchase: { ...DEFAULT_BUY_RENT_INPUTS.purchase, propertyPrice: 500000 },
    };

    const { result } = renderHook(() => useBuyRentCalculations(customInputs));

    expect(result.current.inputs.purchase.propertyPrice).toBe(500000);
  });

  it('should compute derived values', () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    expect(result.current.derivedValues).toEqual(mockDerivedValues);
    expect(mockedService.calculateDerivedValues).toHaveBeenCalled();
  });

  it('should update purchase input', () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    act(() => {
      result.current.updatePurchaseInput('propertyPrice', 400000);
    });

    expect(result.current.inputs.purchase.propertyPrice).toBe(400000);
  });

  it('should update rental input', () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    act(() => {
      result.current.updateRentalInput('monthlyRent', 1200);
    });

    expect(result.current.inputs.rental.monthlyRent).toBe(1200);
  });

  it('should update market input', () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    act(() => {
      result.current.updateMarketInput('priceEvolution', 3.5);
    });

    expect(result.current.inputs.market.priceEvolution).toBe(3.5);
  });

  it('should update resale input', () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    act(() => {
      result.current.updateResaleInput('targetYear', 15);
    });

    expect(result.current.inputs.resale.targetYear).toBe(15);
  });

  it('should run calculation and set results', async () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    act(() => {
      result.current.calculate();
    });

    // Flush the setTimeout(fn, 0)
    act(() => {
      vi.runAllTimers();
    });

    expect(result.current.results).toEqual(mockResults);
    expect(result.current.isCalculating).toBe(false);
    expect(mockedService.calculateBuyRentComparison).toHaveBeenCalled();
  });

  it('should not calculate when validation errors exist', () => {
    mockedValidators.validateBuyRentInputs.mockReturnValue([
      { field: 'purchase.propertyPrice', message: 'Price is required' },
    ]);

    const { result } = renderHook(() => useBuyRentCalculations());

    act(() => {
      result.current.calculate();
    });

    act(() => {
      vi.runAllTimers();
    });

    expect(mockedService.calculateBuyRentComparison).not.toHaveBeenCalled();
    expect(result.current.errors.length).toBeGreaterThan(0);
  });

  it('should handle calculation errors gracefully', () => {
    mockedService.calculateBuyRentComparison.mockImplementation(() => {
      throw new Error('Calculation failed');
    });

    const { result } = renderHook(() => useBuyRentCalculations());

    act(() => {
      result.current.calculate();
    });

    act(() => {
      vi.runAllTimers();
    });

    expect(result.current.results).toBeNull();
    expect(result.current.errors).toEqual(
      expect.arrayContaining([expect.objectContaining({ field: 'general' })])
    );
    expect(result.current.isCalculating).toBe(false);
  });

  it('should reset to default values', () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    // First update something
    act(() => {
      result.current.updatePurchaseInput('propertyPrice', 999999);
    });

    // Then calculate to set results
    act(() => {
      result.current.calculate();
    });
    act(() => {
      vi.runAllTimers();
    });

    // Then reset
    act(() => {
      result.current.reset();
    });

    expect(result.current.results).toBeNull();
    expect(result.current.errors).toEqual([]);
    // The hook auto-updates rental.initialSavings and rental.monthlySavings
    // via a useEffect based on downPayment and suggestedMonthlySavings,
    // so we check the non-derived fields match defaults
    expect(result.current.inputs.purchase).toEqual(DEFAULT_BUY_RENT_INPUTS.purchase);
    expect(result.current.inputs.market).toEqual(DEFAULT_BUY_RENT_INPUTS.market);
    expect(result.current.inputs.resale).toEqual(DEFAULT_BUY_RENT_INPUTS.resale);
    expect(result.current.inputs.rental.monthlyRent).toBe(
      DEFAULT_BUY_RENT_INPUTS.rental.monthlyRent
    );
    expect(result.current.inputs.rental.monthlyCharges).toBe(
      DEFAULT_BUY_RENT_INPUTS.rental.monthlyCharges
    );
    expect(result.current.inputs.rental.securityDeposit).toBe(
      DEFAULT_BUY_RENT_INPUTS.rental.securityDeposit
    );
    expect(result.current.inputs.rental.rentalInsurance).toBe(
      DEFAULT_BUY_RENT_INPUTS.rental.rentalInsurance
    );
    expect(result.current.inputs.rental.garbageTax).toBe(DEFAULT_BUY_RENT_INPUTS.rental.garbageTax);
    // initialSavings and monthlySavings are auto-derived from downPayment and derivedValues
    expect(result.current.inputs.rental.initialSavings).toBe(
      DEFAULT_BUY_RENT_INPUTS.purchase.downPayment
    );
    expect(result.current.inputs.rental.monthlySavings).toBe(
      mockDerivedValues.suggestedMonthlySavings
    );
  });

  it('should set all inputs at once', () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    const newInputs = {
      ...DEFAULT_BUY_RENT_INPUTS,
      purchase: { ...DEFAULT_BUY_RENT_INPUTS.purchase, propertyPrice: 750000 },
    };

    act(() => {
      result.current.setInputs(newInputs);
    });

    expect(result.current.inputs.purchase.propertyPrice).toBe(750000);
  });

  it('should get year N analysis when results exist', () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    // Calculate first to set results
    act(() => {
      result.current.calculate();
    });
    act(() => {
      vi.runAllTimers();
    });

    const analysis = result.current.getYearNAnalysis(5);
    expect(analysis).toEqual({ year: 5, buyAdvantage: 10000 });
    expect(mockedService.calculateYearNAnalysis).toHaveBeenCalledWith(mockResults, 5);
  });

  it('should return null for year N analysis when no results', () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    const analysis = result.current.getYearNAnalysis(5);
    expect(analysis).toBeNull();
  });

  it('should expose isValidResaleYear', () => {
    const { result } = renderHook(() => useBuyRentCalculations());

    expect(result.current.isValidResaleYear).toBe(true);
    expect(mockedService.isValidResaleYear).toHaveBeenCalled();
  });
});
