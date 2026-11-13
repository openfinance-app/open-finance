import { useAuthContext } from '@/context/AuthContext';
import { useExchangeRate, useLatestExchangeRate } from '@/hooks/useCurrency';
import { ConvertedAmount, type ConvertedAmountProps } from '@/components/ui/ConvertedAmount';
import { divide, multiply } from '@/utils/money';

/**
 * Read-only monetary values whose API does not supply a converted amount.
 * Reuse authoritative DTO rates/results first, otherwise resolve one cached quote.
 * Dated postings use conversionDate; current positions and projections use today's quote.
 * Native input/review amounts and native-only reports continue to use ConvertedAmount.
 */
export function CurrencyAmount(props: ConvertedAmountProps) {
  const { baseCurrency: userBaseCurrency } = useAuthContext();
  const baseCurrency = props.baseCurrency ?? userBaseCurrency;
  const foreign = props.currency !== baseCurrency;
  const recorded = props.recordedAmounts?.find(value => value.currency === baseCurrency);
  const suppliedAmount = props.isConverted === true ? props.convertedAmount : undefined;
  const suppliedRate =
    props.baseCurrency === baseCurrency && props.isConverted !== false
      ? props.exchangeRate
      : undefined;
  const hasRate = suppliedRate != null && Number.isFinite(suppliedRate) && suppliedRate > 0;
  // A failed server conversion is authoritative too. Never replace it with a 1:1 value.
  const needsQuote =
    foreign && props.isConverted !== false && suppliedAmount == null && !recorded && !hasRate;
  const { data: latest } = useLatestExchangeRate(
    props.currency,
    baseCurrency,
    undefined,
    needsQuote && !props.conversionDate
  );
  const { data: dated } = useExchangeRate(
    props.currency,
    baseCurrency,
    props.conversionDate,
    needsQuote && !!props.conversionDate
  );
  const quote = needsQuote ? (props.conversionDate ? dated : latest) : undefined;
  const rate = hasRate ? suppliedRate : quote?.rate;
  const usableRate = rate != null && Number.isFinite(rate) && rate > 0 ? rate : undefined;
  const converted =
    suppliedAmount ??
    recorded?.amount ??
    (usableRate == null ? undefined : multiply(props.amount, usableRate));

  return (
    <ConvertedAmount
      {...props}
      baseCurrency={baseCurrency}
      convertedAmount={converted}
      exchangeRate={
        recorded && props.amount !== 0 ? divide(recorded.amount, props.amount) : usableRate
      }
      isConverted={foreign && converted != null}
    />
  );
}
