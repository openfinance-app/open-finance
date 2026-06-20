/**
 * ConvertedAmount - Displays a monetary amount with optional currency conversion tooltip.
 *
 * Reference: REQ-7.1, REQ-7.2, REQ-7.3, REQ-7.4, REQ-8.1, REQ-8.2, REQ-8.3, REQ-8.4, REQ-8.5,
 *            REQ-9.1, REQ-9.2, REQ-9.3
 *
 * This component is the primary way amounts are displayed in the application.
 * It reads the user's preferred display mode and secondary currency from
 * {@link CurrencyDisplayContext} and renders according to the tooltip decision matrix:
 *
 * | Mode   | native ≠ base | Secondary set | Primary Shows          | Tooltip Shows           |
 * |--------|---------------|---------------|------------------------|-------------------------|
 * | base   | yes           | yes           | base amount            | native + secondary      |
 * | base   | yes           | no            | base amount            | native only             |
 * | base   | no            | yes           | native (= base)        | secondary only          |
 * | base   | no            | no            | native (= base)        | (none)                  |
 * | native | yes           | yes           | native amount          | base + secondary        |
 * | native | yes           | no            | native amount          | base only               |
 * | native | no            | yes           | native (= base)        | secondary only          |
 * | native | no            | no            | native (= base)        | (none)                  |
 * | both   | yes           | yes           | base · native inline   | secondary only          |
 * | both   | yes           | no            | base · native inline   | (none)                  |
 * | both   | no            | yes           | native (= base) once   | secondary only          |
 * | both   | no            | no            | native (= base) once   | (none)                  |
 *
 * Tooltip lines include exchange rate strings where available,
 * e.g. "USD 1.62  (1 XOF = 0.00162 USD)". Exchange rates are appended to every
 * tooltip line — including the native-currency line shown in `base` display mode.
 *
 * No badge or icon is rendered. All conversion context is communicated via tooltip alone.
 * All amounts are wrapped in {@link PrivateAmount} to respect the global privacy toggle.
 */
import { useId, useMemo } from 'react';
import type { ReactNode } from 'react';
import { useCurrencyDisplay } from '@/context/CurrencyDisplayContext';
import type { AmountDisplayMode } from '@/context/CurrencyDisplayContext';
import { useNumberFormat } from '@/context/NumberFormatContext';
import type { NumberFormat } from '@/context/NumberFormatContext';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/Tooltip';
import { formatCurrency, formatExchangeRate } from '@/utils/currency';
import { cn } from '@/lib/utils';

export interface ConvertedAmountProps {
  /** Native (original) amount in the asset's own currency */
  amount: number;
  /** Native currency code (ISO 4217) — e.g. "XOF" */
  currency: string;

  /** Amount converted to the user's base currency (null/undefined if unavailable) */
  convertedAmount?: number | null;
  /** User's base currency code (ISO 4217) — e.g. "USD" */
  baseCurrency?: string | null;
  /**
   * Exchange rate used for the base conversion.
   * 1 unit of {@code currency} = {@code exchangeRate} units of {@code baseCurrency}.
   * Shown in the tooltip as "1 {currency} = {rate} {baseCurrency}".
   */
  exchangeRate?: number | null;
  /**
   * Whether a successful base-currency conversion took place.
   * false or undefined means the native currency is the same as base, or rate was unavailable.
   */
  isConverted?: boolean;

  /**
   * Amount converted to the user's secondary currency.
   * Null/undefined when no secondary currency is configured or rate was unavailable.
   * Requirement REQ-7.3
   */
  secondaryAmount?: number | null;
  /**
   * ISO 4217 code of the secondary currency.
   * Should be provided whenever secondaryAmount is provided.
   * Requirement REQ-7.3
   */
  secondaryCurrency?: string | null;
  /**
   * Exchange rate used for the secondary conversion.
   * 1 unit of {@code currency} = {@code secondaryExchangeRate} units of {@code secondaryCurrency}.
   * Shown in the tooltip as "1 {currency} = {rate} {secondaryCurrency}".
   */
  secondaryExchangeRate?: number | null;

  /** Use compact K/M notation (default: false) */
  compact?: boolean;
  /** Additional CSS classes applied to the outer wrapper */
  className?: string;
  /** Whether to display inline (default: false) */
  inline?: boolean;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Formats an exchange rate string: "1 {from} = {rate} {to}".
 * Returns null when the rate is not available.
 */
function formatRate(
  fromCurrency: string,
  toCurrency: string,
  rate: number | null | undefined
): string | null {
  if (rate == null) return null;
  const formatted = formatExchangeRate(rate);
  return `1 ${fromCurrency} = ${formatted} ${toCurrency}`;
}

// ---------------------------------------------------------------------------
// Tooltip computation — REQ-8.1, REQ-8.2, REQ-8.3, REQ-8.5
// ---------------------------------------------------------------------------

/**
 * Builds the list of formatted amount lines to display in the tooltip.
 *
 * Returns an empty array when no tooltip should be shown.
 * Each line shows the formatted amount and, where an exchange rate is available,
 * appends it in the form "  (1 FROM = N TO)".
 *
 * Requirement REQ-8.5: tooltip only renders when there is at least one line.
 */
function buildTooltipLines(
  amount: number,
  currency: string,
  convertedAmount: number | null | undefined,
  baseCurrency: string | null | undefined,
  exchangeRate: number | null | undefined,
  isConverted: boolean | undefined,
  secondaryAmount: number | null | undefined,
  secondaryCurrency: string | null | undefined,
  secondaryExchangeRate: number | null | undefined,
  displayMode: AmountDisplayMode,
  compact: boolean,
  numberFormat: NumberFormat
): string[] {
  const lines: string[] = [];
  const opts = { compact, numberFormat };

  /** Appends the rate hint to an amount string when available. */
  const withRate = (
    amountStr: string,
    from: string,
    to: string,
    rate: number | null | undefined
  ): string => {
    const rateStr = formatRate(from, to, rate);
    return rateStr ? `${amountStr}  (${rateStr})` : amountStr;
  };

  if (displayMode === 'base') {
    if (isConverted && convertedAmount != null) {
      lines.push(
        withRate(
          formatCurrency(amount, currency, opts),
          currency,
          baseCurrency ?? currency,
          exchangeRate
        )
      );
    }
    if (secondaryAmount != null && secondaryCurrency) {
      lines.push(
        withRate(
          formatCurrency(secondaryAmount, secondaryCurrency, opts),
          currency,
          secondaryCurrency,
          secondaryExchangeRate
        )
      );
    }
  } else if (displayMode === 'native') {
    if (isConverted && convertedAmount != null && baseCurrency) {
      lines.push(
        withRate(
          formatCurrency(convertedAmount, baseCurrency, opts),
          currency,
          baseCurrency,
          exchangeRate
        )
      );
    }
    if (secondaryAmount != null && secondaryCurrency) {
      lines.push(
        withRate(
          formatCurrency(secondaryAmount, secondaryCurrency, opts),
          currency,
          secondaryCurrency,
          secondaryExchangeRate
        )
      );
    }
  } else {
    if (secondaryAmount != null && secondaryCurrency) {
      lines.push(
        withRate(
          formatCurrency(secondaryAmount, secondaryCurrency, opts),
          currency,
          secondaryCurrency,
          secondaryExchangeRate
        )
      );
    }
  }

  return lines;
}

// ---------------------------------------------------------------------------
// Primary display computation — REQ-7.1, REQ-7.2
// ---------------------------------------------------------------------------

/**
 * Builds the primary display node for the given display mode.
 *
 * Requirement REQ-7.1: 'base' shows base amount when available, else native.
 * Requirement REQ-7.2: 'native' always shows native.
 * Requirement REQ-7.4: 'both' shows "base · native" inline, or native only if same currency.
 */
function buildPrimaryDisplay(
  amount: number,
  currency: string,
  convertedAmount: number | null | undefined,
  baseCurrency: string | null | undefined,
  isConverted: boolean | undefined,
  displayMode: AmountDisplayMode,
  compact: boolean,
  inline: boolean,
  numberFormat: NumberFormat
): ReactNode {
  const opts = { compact, numberFormat };
  const canShowBase = isConverted === true && convertedAmount != null && baseCurrency;

  if (displayMode === 'base') {
    return canShowBase ? (
      <PrivateAmount inline={inline}>{formatCurrency(convertedAmount!, baseCurrency!, opts)}</PrivateAmount>
    ) : (
      <PrivateAmount inline={inline}>{formatCurrency(amount, currency, opts)}</PrivateAmount>
    );
  }

  if (displayMode === 'native') {
    return <PrivateAmount inline={inline}>{formatCurrency(amount, currency, opts)}</PrivateAmount>;
  }

  // 'both'
  if (canShowBase && currency !== baseCurrency) {
    return (
      <span className="inline-flex items-baseline gap-1">
        <PrivateAmount inline>{formatCurrency(convertedAmount!, baseCurrency!, opts)}</PrivateAmount>
        <span className="text-muted-foreground mx-0.5" aria-hidden="true">·</span>
        <PrivateAmount inline className="text-muted-foreground text-sm">
          {formatCurrency(amount, currency, opts)}
        </PrivateAmount>
      </span>
    );
  }

  return <PrivateAmount inline={inline}>{formatCurrency(amount, currency, opts)}</PrivateAmount>;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * ConvertedAmount component.
 *
 * Renders a financial amount honouring the user's currency display preference
 * and the privacy visibility toggle. Tooltip is rendered via Radix Tooltip
 * to avoid z-index and overflow clipping issues.
 *
 * Requirement REQ-9.1: No badge/icon rendered in any scenario.
 * Requirement REQ-9.2: CurrencyBadge is not imported or used.
 * Requirement REQ-9.3: Tooltip uses role="tooltip" with aria-describedby linkage.
 */
export function ConvertedAmount({
  amount,
  currency,
  convertedAmount,
  baseCurrency,
  exchangeRate,
  isConverted = false,
  secondaryAmount,
  secondaryCurrency: secondaryCurrencyProp,
  secondaryExchangeRate,
  compact = false,
  className,
  inline = false,
}: ConvertedAmountProps) {
  const { displayMode, secondaryCurrency: ctxSecondaryCurrency } = useCurrencyDisplay();
  const { numberFormat } = useNumberFormat();
  const tooltipId = useId();

  const resolvedSecondaryCurrency = secondaryCurrencyProp ?? ctxSecondaryCurrency;

  const tooltipLines = useMemo(
    () =>
      buildTooltipLines(
        amount,
        currency,
        convertedAmount,
        baseCurrency,
        exchangeRate,
        isConverted,
        secondaryAmount,
        resolvedSecondaryCurrency,
        secondaryExchangeRate,
        displayMode,
        compact,
        numberFormat
      ),
    [
      amount,
      currency,
      convertedAmount,
      baseCurrency,
      exchangeRate,
      isConverted,
      secondaryAmount,
      resolvedSecondaryCurrency,
      secondaryExchangeRate,
      displayMode,
      compact,
      numberFormat,
    ]
  );

  const primaryDisplay = useMemo(
    () =>
      buildPrimaryDisplay(
        amount,
        currency,
        convertedAmount,
        baseCurrency,
        isConverted,
        displayMode,
        compact,
        inline,
        numberFormat
      ),
    [amount, currency, convertedAmount, baseCurrency, isConverted, displayMode, compact, inline, numberFormat]
  );

  const hasTooltip = tooltipLines.length > 0;

  const content = (
    <span
      data-testid="converted-amount"
      className={cn('inline-block', className)}
      tabIndex={hasTooltip ? 0 : undefined}
      aria-describedby={hasTooltip ? tooltipId : undefined}
    >
      {primaryDisplay}
    </span>
  );

  if (!hasTooltip) {
    return content;
  }

  return (
    <TooltipProvider delayDuration={150}>
      <Tooltip>
        <TooltipTrigger asChild>
          {content}
        </TooltipTrigger>
        <TooltipContent
          id={tooltipId}
          className="bg-surface-elevated text-xs whitespace-nowrap border-border shadow-md"
          sideOffset={4}
        >
          {tooltipLines.map((line, i) => (
            <span key={i} className="block">{line}</span>
          ))}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
