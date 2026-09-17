import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useRentalSimulator } from './useRentalSimulator';
import { DEFAULT_INVESTMENT_INPUTS, FURNITURE_VALUES } from '@/types/realEstateTools';
import { RealEstateCalculationService } from '@/services/realEstateCalculationService';
import * as validators from '@/validators/realEstateValidators';
import * as taxUtils from '@/utils/taxRegimeCalculations';

vi.mock('@/services/realEstateCalculationService');
vi.mock('@/validators/realEstateValidators');
vi.mock('@/utils/taxRegimeCalculations');

const mockedService = RealEstateCalculationService as any;
const mockedValidators = validators as any;
const mockedTaxUtils = taxUtils as any;

describe('useRentalSimulator', () => {
  const mockResults = {
    microFoncier: { eligible: true, netIncome: 5000, taxRate: 0.3 },
    reelFoncier: { eligible: true, netIncome: 6000, taxRate: 0.25 },
    lmnpReel: { eligible: true, netIncome: 7000, taxRate: 0.2 },
    microBic: { eligible: false, netIncome: 0, taxRate: 0 },
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    mockedService.calculateInvestment.mockReturnValue(mockResults);
    mockedValidators.validateInvestmentInputs.mockReturnValue([]);
    mockedTaxUtils.getRecommendedRegime.mockReturnValue('lmnp_reel');
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should initialize with default inputs', () => {
    const { result } = renderHook(() => useRentalSimulator());

    expect(result.current.inputs).toEqual(
      expect.objectContaining({
        credit: expect.any(Object),
        property: expect.any(Object),
        revenue: expect.any(Object),
        expenses: expect.any(Object),
      })
    );
    expect(result.current.results).toBeNull();
    expect(result.current.isCalculating).toBe(false);
    expect(result.current.errors).toEqual([]);
  });

  it('should initialize with shared data', () => {
    const sharedData = {
      totalPrice: 300000,
      credit: {
        monthlyPayment: 1200,
        annualCost: 14400,
        totalCost: 360000,
        assurance: 50,
        bankFees: 500,
      },
      propertyTax: 2000,
      coOwnershipCharges: 3000,
    };

    const { result } = renderHook(() => useRentalSimulator(sharedData));

    expect(result.current.inputs.property.totalPrice).toBe(300000);
    expect(result.current.inputs.credit.monthlyPayment).toBe(1200);
    expect(result.current.inputs.expenses.propertyTax).toBe(2000);
  });

  it('should update credit input', () => {
    const { result } = renderHook(() => useRentalSimulator());

    act(() => {
      result.current.updateCreditInput('monthlyPayment', 1500);
    });

    expect(result.current.inputs.credit.monthlyPayment).toBe(1500);
  });

  it('should update property input', () => {
    const { result } = renderHook(() => useRentalSimulator());

    act(() => {
      result.current.updatePropertyInput('totalPrice', 400000);
    });

    expect(result.current.inputs.property.totalPrice).toBe(400000);
  });

  it('should update revenue input', () => {
    const { result } = renderHook(() => useRentalSimulator());

    act(() => {
      result.current.updateRevenueInput('monthlyRent', 1200);
    });

    expect(result.current.inputs.revenue.monthlyRent).toBe(1200);
  });

  it('should update expense input', () => {
    const { result } = renderHook(() => useRentalSimulator());

    act(() => {
      result.current.updateExpenseInput('propertyTax', 2500);
    });

    expect(result.current.inputs.expenses.propertyTax).toBe(2500);
  });

  it('should run calculation and set results', () => {
    const { result } = renderHook(() => useRentalSimulator());

    act(() => {
      result.current.calculate();
    });

    act(() => {
      vi.runAllTimers();
    });

    expect(result.current.results).toEqual(mockResults);
    expect(result.current.isCalculating).toBe(false);
    expect(mockedService.calculateInvestment).toHaveBeenCalled();
  });

  it('should not calculate when validation errors exist', () => {
    mockedValidators.validateInvestmentInputs.mockReturnValue([
      { field: 'revenue.monthlyRent', message: 'Rent is required' },
    ]);

    const { result } = renderHook(() => useRentalSimulator());

    act(() => {
      result.current.calculate();
    });

    act(() => {
      vi.runAllTimers();
    });

    expect(mockedService.calculateInvestment).not.toHaveBeenCalled();
    expect(result.current.errors.length).toBeGreaterThan(0);
  });

  it('should handle calculation errors gracefully', () => {
    mockedService.calculateInvestment.mockImplementation(() => {
      throw new Error('Calculation failed');
    });

    const { result } = renderHook(() => useRentalSimulator());

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
    const { result } = renderHook(() => useRentalSimulator());

    act(() => {
      result.current.updateCreditInput('monthlyPayment', 9999);
    });

    act(() => {
      result.current.calculate();
    });
    act(() => {
      vi.runAllTimers();
    });

    act(() => {
      result.current.reset();
    });

    expect(result.current.results).toBeNull();
    expect(result.current.errors).toEqual([]);
  });

  it('should set all inputs at once', () => {
    const { result } = renderHook(() => useRentalSimulator());

    const newInputs = {
      ...DEFAULT_INVESTMENT_INPUTS,
      revenue: { ...DEFAULT_INVESTMENT_INPUTS.revenue, monthlyRent: 1500 },
    };

    act(() => {
      result.current.setInputs(newInputs);
    });

    expect(result.current.inputs.revenue.monthlyRent).toBe(1500);
  });

  it('should load shared data', () => {
    const { result } = renderHook(() => useRentalSimulator());

    const sharedData = {
      totalPrice: 350000,
      credit: {
        monthlyPayment: 1300,
        annualCost: 15600,
        totalCost: 390000,
        assurance: 60,
        bankFees: 600,
      },
      propertyTax: 2500,
      coOwnershipCharges: 3500,
    };

    act(() => {
      result.current.loadSharedData(sharedData);
    });

    expect(result.current.inputs.property.totalPrice).toBe(350000);
    expect(result.current.inputs.credit.monthlyPayment).toBe(1300);
    expect(result.current.inputs.expenses.propertyTax).toBe(2500);
    expect(result.current.inputs.expenses.nonRecoverableCharges).toBe(3500);
  });

  it('should get recommended regime when results exist', () => {
    const { result } = renderHook(() => useRentalSimulator());

    act(() => {
      result.current.calculate();
    });
    act(() => {
      vi.runAllTimers();
    });

    expect(result.current.recommendedRegime).toBe('lmnp_reel');
  });

  it('should return null for recommended regime when no results', () => {
    const { result } = renderHook(() => useRentalSimulator());
    expect(result.current.recommendedRegime).toBeNull();
  });

  it('should compute eligible regimes from results', () => {
    const { result } = renderHook(() => useRentalSimulator());

    act(() => {
      result.current.calculate();
    });
    act(() => {
      vi.runAllTimers();
    });

    // microFoncier, reelFoncier, lmnpReel are eligible; microBic is not
    expect(result.current.eligibleRegimes).toContain('micro_foncier');
    expect(result.current.eligibleRegimes).toContain('reel_foncier');
    expect(result.current.eligibleRegimes).toContain('lmnp_reel');
    expect(result.current.eligibleRegimes).not.toContain('micro_bic');
  });

  it('should get regime result', () => {
    const { result } = renderHook(() => useRentalSimulator());

    act(() => {
      result.current.calculate();
    });
    act(() => {
      vi.runAllTimers();
    });

    expect(result.current.getRegimeResult('micro_foncier')).toEqual(mockResults.microFoncier);
    expect(result.current.getRegimeResult('reel_foncier')).toEqual(mockResults.reelFoncier);
    expect(result.current.getRegimeResult('lmnp_reel')).toEqual(mockResults.lmnpReel);
    expect(result.current.getRegimeResult('micro_bic')).toEqual(mockResults.microBic);
  });

  it('should return null for regime result when no results', () => {
    const { result } = renderHook(() => useRentalSimulator());
    expect(result.current.getRegimeResult('micro_foncier')).toBeNull();
  });

  it('should check regime eligibility', () => {
    const { result } = renderHook(() => useRentalSimulator());

    act(() => {
      result.current.calculate();
    });
    act(() => {
      vi.runAllTimers();
    });

    expect(result.current.isRegimeEligible('micro_foncier')).toBe(true);
    expect(result.current.isRegimeEligible('micro_bic')).toBe(false);
  });

  it('should return false for regime eligibility when no results', () => {
    const { result } = renderHook(() => useRentalSimulator());
    expect(result.current.isRegimeEligible('micro_foncier')).toBe(false);
  });
});
