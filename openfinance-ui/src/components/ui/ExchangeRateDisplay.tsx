/**
 * ExchangeRateDisplay component
 * Sprint 6 - Task 6.2.14: Display exchange rates
 * 
 * Shows currency conversion information with exchange rates.
 * Used for displaying foreign currency account balances in base currency.
 */

import { useLatestExchangeRate } from '@/hooks/useCurrency';
import { formatExchangeRate } from '@/utils/currency';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { multiply } from '@/utils/money';
import { RefreshCw, TrendingDown, TrendingUp } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useState } from 'react';

interface ExchangeRateDisplayProps {
  /** Source currency code (e.g., "USD") */
  from: string;
  /** Target currency code (e.g., "EUR") */
  to: string;
  /** Optional amount to convert and display */
  amount?: number;
  /** Show refresh button to manually fetch latest rate */
  showRefresh?: boolean;
  /** Compact mode - single line display */
  compact?: boolean;
  /** Additional CSS classes */
  className?: string;
}

/**
 * ExchangeRateDisplay component
 * 
 * Displays exchange rate information between two currencies.
 * Optionally shows converted amount and refresh functionality.
 * 
 * @example
 * // Basic rate display
 * <ExchangeRateDisplay from="USD" to="EUR" />
 * 
 * @example
 * // With amount conversion
 * <ExchangeRateDisplay from="USD" to="EUR" amount={100} />
 * // Shows: "100 USD ≈ 85.00 EUR (1 USD = 0.85 EUR)"
 * 
 * @example
 * // Compact mode with refresh
 * <ExchangeRateDisplay 
 *   from="GBP" 
 *   to="USD" 
 *   amount={500} 
 *   compact 
 *   showRefresh 
 * />
 */
export function ExchangeRateDisplay({
  from,
  to,
  amount,
  showRefresh = false,
  compact = false,
  className = '',
}: ExchangeRateDisplayProps) {
  const [refreshKey, setRefreshKey] = useState(0);
  const { format: formatCurrency } = useFormatCurrency();
  
  const { 
    data: exchangeRate, 
    isLoading, 
    isError, 
    refetch,
    isFetching
  } = useLatestExchangeRate(from, to, refreshKey);

  // Handle same currency
  if (from === to) {
    return (
      <div className={`text-sm text-text-muted ${className}`}>
        Same currency
      </div>
    );
  }

  // Loading state
  if (isLoading) {
    return (
      <div className={`flex items-center gap-2 text-sm text-text-muted ${className}`}>
        <RefreshCw className="h-3 w-3 animate-spin" />
        <span>Loading exchange rate...</span>
      </div>
    );
  }

  // Error state
  if (isError || !exchangeRate) {
    return (
      <div className={`flex items-center gap-2 text-sm text-error ${className}`}>
        <TrendingDown className="h-3 w-3" />
        <span>Unable to load exchange rate</span>
        {showRefresh && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => refetch()}
            className="h-6 px-2"
          >
            Retry
          </Button>
        )}
      </div>
    );
  }

  const { rate, inverseRate, rateDate, source } = exchangeRate;

  // Calculate converted amount if provided
  const convertedAmount = amount ? multiply(amount, rate) : undefined;

  // Format date
  const formattedDate = new Date(rateDate).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });

  // Handle refresh
  const handleRefresh = async () => {
    setRefreshKey(prev => prev + 1);
    await refetch();
  };

  // Compact mode - single line
  if (compact) {
    return (
      <div className={`flex items-center gap-2 text-sm ${className}`}>
        {amount !== undefined && convertedAmount !== undefined ? (
          <>
            <span className="text-text-secondary">
              {formatCurrency(amount, from)} ≈ {formatCurrency(convertedAmount, to)}
            </span>
            <span className="text-text-muted">
              (1 {from} = {formatExchangeRate(rate)} {to})
            </span>
          </>
        ) : (
          <span className="text-text-secondary">
            1 {from} = {formatExchangeRate(rate)} {to}
          </span>
        )}
        {showRefresh && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleRefresh}
            disabled={isFetching}
            className="h-6 w-6 p-0"
            title="Refresh exchange rate"
          >
            <RefreshCw className={`h-3 w-3 ${isFetching ? 'animate-spin' : ''}`} />
          </Button>
        )}
      </div>
    );
  }

  // Full mode - multi-line display
  return (
    <div className={`rounded-lg border border-border bg-surface p-3 ${className}`}>
      {/* Header with title and refresh button */}
      <div className="mb-2 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <TrendingUp className="h-4 w-4 text-primary" />
          <span className="text-sm font-medium text-text-primary">
            Exchange Rate
          </span>
        </div>
        {showRefresh && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleRefresh}
            disabled={isFetching}
            className="h-7 px-2"
          >
            <RefreshCw className={`h-3 w-3 ${isFetching ? 'animate-spin' : ''}`} />
            <span className="ml-1 text-xs">Refresh</span>
          </Button>
        )}
      </div>

      {/* Conversion display */}
      {amount !== undefined && convertedAmount !== undefined && (
        <div className="mb-2">
          <div className="flex items-baseline gap-2">
            <span className="font-mono text-lg font-semibold text-text-primary">
              {formatCurrency(amount, from)}
            </span>
            <span className="text-text-muted">≈</span>
            <span className="font-mono text-lg font-semibold text-primary">
              {formatCurrency(convertedAmount, to)}
            </span>
          </div>
        </div>
      )}

      {/* Rate information */}
      <div className="space-y-1 text-sm">
        <div className="flex items-center justify-between">
          <span className="text-text-muted">Rate ({from} → {to}):</span>
          <span className="font-mono text-text-primary">
            {formatExchangeRate(rate)}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-text-muted">Rate ({to} → {from}):</span>
          <span className="font-mono text-text-primary">
            {formatExchangeRate(inverseRate)}
          </span>
        </div>
      </div>

      {/* Metadata */}
      <div className="mt-2 flex items-center justify-between border-t border-border pt-2 text-xs text-text-muted">
        <span>Updated: {formattedDate}</span>
        <span>Source: {source}</span>
      </div>
    </div>
  );
}

/**
 * ExchangeRateInline - Inline rate display for tight spaces
 * 
 * @example
 * <ExchangeRateInline from="USD" to="EUR" />
 * // Shows: "1 USD = 0.85 EUR"
 */
export function ExchangeRateInline({
  from,
  to,
  className = '',
}: Pick<ExchangeRateDisplayProps, 'from' | 'to' | 'className'>) {
  const { data: exchangeRate, isLoading, isError } = useLatestExchangeRate(from, to);

  if (from === to) return null;
  if (isLoading) {
    return (
      <span className={`text-xs text-text-muted ${className}`}>
        Loading rate...
      </span>
    );
  }
  if (isError || !exchangeRate) {
    return (
      <span className={`text-xs text-error ${className}`}>
        Rate unavailable
      </span>
    );
  }

  return (
    <span className={`text-xs text-text-muted ${className}`}>
      1 {from} = {formatExchangeRate(exchangeRate.rate)} {to}
    </span>
  );
}
