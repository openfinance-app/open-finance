/**
 * ConversionSummary - Dashboard info bar for currency conversion status.
 *
 * Reference: REQ-8.3, REQ-12.1, REQ-12.2
 *
 * Shows:
 *  - Number of foreign-currency accounts being converted
 *  - When exchange rates were last updated
 *  - A stale-rate warning when the last update is older than 24 hours
 *  - A "Refresh rates" button to trigger an immediate rate update
 */
import { AlertTriangle, RefreshCw, Check } from 'lucide-react';
import { useAccounts } from '@/hooks/useAccounts';
import { useLatestExchangeRate, useUpdateExchangeRates } from '@/hooks/useCurrency';
import { useAuthContext } from '@/context/AuthContext';
import { cn } from '@/lib/utils';

/** Number of milliseconds in 24 hours — stale-rate threshold. */
const STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000;

/** Format a UTC date string as a short locale-aware string, e.g. "Mar 4, 14:32". */
function formatRateDate(isoDate: string): string {
  try {
    return new Date(isoDate).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return isoDate;
  }
}

/** Returns true when the supplied ISO date string is older than 24 hours. */
function isStale(isoDate: string): boolean {
  try {
    const diff = Date.now() - new Date(isoDate).getTime();
    return diff > STALE_THRESHOLD_MS;
  } catch {
    return false;
  }
}

/**
 * Inner component that fetches a representative exchange rate for the first
 * foreign-currency pair so we can show the "last updated" timestamp.
 */
interface RateSummaryProps {
  fromCurrency: string;
  baseCurrency: string;
  convertedCount: number;
}

function RateSummary({ fromCurrency, baseCurrency, convertedCount }: RateSummaryProps) {
  const { data: rate, isLoading } = useLatestExchangeRate(fromCurrency, baseCurrency);
  const updateRates = useUpdateExchangeRates();

  const rateDate = rate?.rateDate;
  const stale = rateDate ? isStale(rateDate) : false;

  const handleRefresh = () => {
    updateRates.mutate();
  };

  return (
    <div
      className={cn(
        'flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2 rounded-lg text-xs border',
        stale
          ? 'bg-warning/10 border-warning/30 text-warning'
          : 'bg-surface border-border text-text-secondary'
      )}
      role="status"
      aria-live="polite"
    >
      {/* Converted accounts count */}
      <span className="flex items-center gap-1">
        <Check className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        {convertedCount} {convertedCount === 1 ? 'account' : 'accounts'} converted to {baseCurrency}
      </span>

      {/* Last updated timestamp */}
      {!isLoading && rateDate && <span>Rates updated: {formatRateDate(rateDate)}</span>}

      {/* Stale warning */}
      {stale && (
        <span className="flex items-center gap-1 font-medium">
          <AlertTriangle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          {/* REQ-12.1: Warn when rate is older than 24 hours */}
          Rates may be outdated
        </span>
      )}

      {/* REQ-12.2: Refresh button */}
      <button
        type="button"
        onClick={handleRefresh}
        disabled={updateRates.isPending}
        className={cn(
          'ml-auto flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium transition-colors',
          'border border-current/30 hover:bg-current/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary',
          updateRates.isPending && 'opacity-50 cursor-not-allowed'
        )}
        aria-label="Refresh exchange rates"
      >
        <RefreshCw
          className={cn('h-3 w-3', updateRates.isPending && 'animate-spin')}
          aria-hidden="true"
        />
        {updateRates.isPending ? 'Refreshing…' : 'Refresh rates'}
      </button>
    </div>
  );
}

/**
 * ConversionSummary component.
 *
 * Renders a compact info bar on the dashboard when at least one account
 * is denominated in a foreign currency (i.e., different from the user's
 * base currency). Returns null when there are no foreign-currency accounts.
 */
export function ConversionSummary() {
  const { baseCurrency } = useAuthContext();

  const { data: accounts, isLoading } = useAccounts();

  if (isLoading || !accounts || accounts.length === 0) {
    return null;
  }

  // Find accounts with a currency different from the user's base currency
  const foreignAccounts = accounts.filter(a => a.currency && a.currency !== baseCurrency);

  if (foreignAccounts.length === 0) {
    return null;
  }

  // Use the first foreign currency to fetch a representative rate timestamp
  const representativeCurrency = foreignAccounts[0].currency;

  return (
    <RateSummary
      fromCurrency={representativeCurrency}
      baseCurrency={baseCurrency}
      convertedCount={foreignAccounts.length}
    />
  );
}
