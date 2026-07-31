import { type ReactNode, useLayoutEffect } from 'react';
import { useCurrencies } from '@/hooks/useCurrency';
import { setCryptoCurrencyCodes } from '@/utils/currency';

/**
 * Pushes the set of CRYPTO currency codes from the currencies API into the `currency.ts` module
 * state so `isCryptoCurrency`/`getCurrencyDecimals` reflect the DB `type` source of truth.
 * Mirrors DecimalPlacesProvider (useLayoutEffect -> setter, runs before paint).
 */
export function CryptoCurrenciesProvider({ children }: { children: ReactNode }) {
  const { data: currencies } = useCurrencies();

  useLayoutEffect(() => {
    setCryptoCurrencyCodes((currencies ?? []).filter(c => c.type === 'CRYPTO').map(c => c.code));
  }, [currencies]);

  return <>{children}</>;
}
