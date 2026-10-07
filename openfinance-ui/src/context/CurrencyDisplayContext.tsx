import { createContext, useContext, useCallback, useMemo } from 'react';
import type { ReactNode } from 'react';
import { useAuthContext } from '@/context/AuthContext';
import { useUserSettings, useUpdateUserSettings } from '@/hooks/useUserSettings';

export type AmountDisplayMode = 'base' | 'native' | 'both';

interface CurrencyDisplayContextType {
  displayMode: AmountDisplayMode;
  setDisplayMode: (mode: AmountDisplayMode) => Promise<void>;
  secondaryCurrency: string | null;
  setSecondaryCurrency: (code: string | null) => Promise<void>;
}

const CurrencyDisplayContext = createContext<CurrencyDisplayContextType | undefined>(undefined);

/** Server settings are authoritative and scoped to the authenticated user. */
export function CurrencyDisplayProvider({ children }: { children: ReactNode }) {
  const { user, isAuthenticated } = useAuthContext();
  const { data: settings } = useUserSettings();
  const { mutateAsync } = useUpdateUserSettings();
  const currentSettings = isAuthenticated && settings?.userId === user?.id ? settings : undefined;
  const displayMode = currentSettings?.amountDisplayMode ?? 'base';
  const secondaryCurrency = currentSettings?.secondaryCurrency || null;

  const setDisplayMode = useCallback(
    async (mode: AmountDisplayMode) => {
      await mutateAsync({ amountDisplayMode: mode });
    },
    [mutateAsync]
  );

  const setSecondaryCurrency = useCallback(
    async (code: string | null) => {
      await mutateAsync({ secondaryCurrency: code?.trim() ?? '' });
    },
    [mutateAsync]
  );

  const value = useMemo(
    () => ({ displayMode, setDisplayMode, secondaryCurrency, setSecondaryCurrency }),
    [displayMode, setDisplayMode, secondaryCurrency, setSecondaryCurrency]
  );
  return (
    <CurrencyDisplayContext.Provider value={value}>{children}</CurrencyDisplayContext.Provider>
  );
}

export function useCurrencyDisplay(): CurrencyDisplayContextType {
  const context = useContext(CurrencyDisplayContext);
  if (context === undefined) {
    throw new Error('useCurrencyDisplay must be used within a CurrencyDisplayProvider');
  }
  return context;
}
