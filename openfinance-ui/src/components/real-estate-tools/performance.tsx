/**
 * Performance Optimization Utilities
 * 
 * Memoization and performance hooks for real estate calculations
 * Requirements: REQ-3.1.1, REQ-3.1.2, REQ-6.4
 */

import { useMemo, useCallback, useRef, useEffect, useState } from 'react';
import type { BuyRentInputs, InvestmentInputs } from '@/types/realEstateTools';
import { DEFAULT_CURRENCY } from '@/utils/currency';

/**
 * Custom hook for debouncing values
 */
export function useDebounce<T>(value: T, delay: number = 300): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => clearTimeout(timer);
  }, [value, delay]);

  return debouncedValue;
}

/**
 * Custom hook for throttling functions
 */
export function useThrottle<T extends (...args: unknown[]) => unknown>(
  callback: T,
  delay: number = 100
): T {
  const lastCall = useRef<number>(0);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);

  return useCallback(
    ((...args: unknown[]) => {
      const now = Date.now();
      const timeSinceLastCall = now - lastCall.current;

      if (timeSinceLastCall >= delay) {
        lastCall.current = now;
        callback(...args);
      } else {
        if (timeoutRef.current) {
          clearTimeout(timeoutRef.current);
        }
        timeoutRef.current = setTimeout(() => {
          lastCall.current = Date.now();
          callback(...args);
        }, delay - timeSinceLastCall);
      }
    }) as T,
    [callback, delay]
  );
}

/**
 * Memoized comparison for BuyRentInputs
 * Only returns true if relevant calculation inputs changed
 */
export function useMemoizedBuyRentInputs(inputs: BuyRentInputs): BuyRentInputs {
  return useMemo(() => inputs, [
    // Purchase inputs that affect calculations
    inputs.purchase.propertyPrice,
    inputs.purchase.renovationAmount,
    inputs.purchase.isNewProperty,
    inputs.purchase.notaryFeesPercent,
    inputs.purchase.agencyFees,
    inputs.purchase.downPayment,
    inputs.purchase.loanDuration,
    inputs.purchase.interestRate,
    inputs.purchase.totalInsurance,
    inputs.purchase.applicationFees,
    inputs.purchase.guaranteeFees,
    inputs.purchase.accountFees,
    inputs.purchase.propertyTax,
    inputs.purchase.coOwnershipCharges,
    inputs.purchase.maintenancePercent,
    inputs.purchase.homeInsurance,
    inputs.purchase.bankFees,
    inputs.purchase.garbageTax,
    // Rental inputs
    inputs.rental.monthlyRent,
    inputs.rental.monthlyCharges,
    inputs.rental.securityDeposit,
    inputs.rental.rentalInsurance,
    inputs.rental.garbageTax,
    inputs.rental.initialSavings,
    inputs.rental.monthlySavings,
    // Market inputs
    inputs.market.priceEvolution,
    inputs.market.rentEvolution,
    inputs.market.investmentReturn,
    inputs.market.inflation,
    // Resale inputs
    inputs.resale.targetYear,
    inputs.resale.desiredProfit,
    inputs.resale.resaleFeesPercent,
  ]);
}

/**
 * Memoized comparison for InvestmentInputs
 */
export function useMemoizedInvestmentInputs(inputs: InvestmentInputs): InvestmentInputs {
  return useMemo(() => inputs, [
    inputs.credit.monthlyPayment,
    inputs.credit.annualCost,
    inputs.credit.totalCost,
    inputs.credit.assurance,
    inputs.credit.bankFees,
    inputs.property.totalPrice,
    inputs.property.furnishingType,
    inputs.property.furnitureValue,
    inputs.revenue.monthlyRent,
    inputs.revenue.recoverableCharges,
    inputs.revenue.occupancyRate,
    inputs.revenue.badDebtRate,
    inputs.expenses.propertyTax,
    inputs.expenses.nonRecoverableCharges,
    inputs.expenses.annualMaintenance,
    inputs.expenses.cfe,
    inputs.expenses.cvae,
    inputs.expenses.managementFees,
    inputs.expenses.pnoInsurance,
    inputs.expenses.accountingFees,
    inputs.expenses.marginalTaxRate,
  ]);
}

/**
 * Hook to measure and log render performance in development
 */
export function useRenderPerformance(componentName: string) {
  const renderCount = useRef(0);
  const startTime = useRef<number>(0);

  useEffect(() => {
    if (process.env.NODE_ENV === 'development') {
      renderCount.current += 1;
      const endTime = performance.now();
      const duration = startTime.current ? endTime - startTime.current : 0;
      
      console.log(`[Performance] ${componentName} rendered #${renderCount.current} in ${duration.toFixed(2)}ms`);
      startTime.current = performance.now();
    }
  });
}

/**
 * Hook to track calculation performance
 */
export function useCalculationPerformance() {
  const [performance, setPerformance] = useState<{
    lastCalculationTime: number;
    averageCalculationTime: number;
    totalCalculations: number;
  }>({
    lastCalculationTime: 0,
    averageCalculationTime: 0,
    totalCalculations: 0,
  });

  const trackCalculation = useCallback((duration: number) => {
    setPerformance((prev) => ({
      lastCalculationTime: duration,
      averageCalculationTime: 
        (prev.averageCalculationTime * prev.totalCalculations + duration) / 
        (prev.totalCalculations + 1),
      totalCalculations: prev.totalCalculations + 1,
    }));
  }, []);

  return { performance, trackCalculation };
}

/**
 * Hook to lazy load Chart.js only when needed
 */
export function useLazyChart() {
  const [ChartComponent, setChartComponent] = useState<React.ComponentType<any> | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const loadChart = useCallback(async () => {
    if (ChartComponent || isLoading) return;
    
    setIsLoading(true);
    try {
      const { Line } = await import('react-chartjs-2');
      setChartComponent(() => Line);
    } catch (error) {
      console.error('Failed to load Chart.js:', error);
    } finally {
      setIsLoading(false);
    }
  }, [ChartComponent, isLoading]);

  return { ChartComponent, isLoading, loadChart };
}

/**
 * Memoized currency formatter
 */
export function useMemoizedFormatter(currency: string = DEFAULT_CURRENCY) {
  return useMemo(() => {
    const currencyFormatter = new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });

    const percentFormatter = new Intl.NumberFormat('fr-FR', {
      style: 'percent',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });

    const compactFormatter = new Intl.NumberFormat('fr-FR', {
      notation: 'compact',
      compactDisplay: 'short',
    });

    return {
      formatCurrency: (value: number) => currencyFormatter.format(value),
      formatPercent: (value: number) => percentFormatter.format(value / 100),
      formatCompact: (value: number) => compactFormatter.format(value),
    };
  }, []);
}

/**
 * Hook to detect slow calculations and show warning
 */
export function useSlowCalculationWarning(threshold: number = 500) {
  const [isSlow, setIsSlow] = useState(false);
  const startTimeRef = useRef<number>(0);

  const startCalculation = useCallback(() => {
    startTimeRef.current = performance.now();
    setIsSlow(false);
  }, []);

  const endCalculation = useCallback(() => {
    const duration = performance.now() - startTimeRef.current;
    if (duration > threshold) {
      setIsSlow(true);
      console.warn(`Slow calculation detected: ${duration.toFixed(2)}ms`);
    }
    return duration;
  }, [threshold]);

  return { isSlow, startCalculation, endCalculation };
}

/**
 * Hook to prefetch calculations in background
 */
export function usePrefetchCalculations(
  inputs: BuyRentInputs,
  calculateFn: () => void
) {
  const [isPrefetched, setIsPrefetched] = useState(false);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    // Clear previous timeout
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }

    // Prefetch after user stops typing for 2 seconds
    timeoutRef.current = setTimeout(() => {
      if (!isPrefetched) {
        // Use requestIdleCallback for non-urgent prefetch
        if ('requestIdleCallback' in window) {
          (window as any).requestIdleCallback(() => {
            calculateFn();
            setIsPrefetched(true);
          });
        } else {
          // Fallback for browsers without requestIdleCallback
          setTimeout(calculateFn, 100);
          setIsPrefetched(true);
        }
      }
    }, 2000);

    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, [inputs, calculateFn, isPrefetched]);

  return { isPrefetched, resetPrefetch: () => setIsPrefetched(false) };
}

export default {
  useDebounce,
  useThrottle,
  useMemoizedBuyRentInputs,
  useMemoizedInvestmentInputs,
  useRenderPerformance,
  useCalculationPerformance,
  useLazyChart,
  useMemoizedFormatter,
  useSlowCalculationWarning,
  usePrefetchCalculations,
};
