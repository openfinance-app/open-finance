/**
 * useCountryToolConfig
 *
 * Returns country-specific configuration objects for real estate tools based
 * on the authenticated user's `country` setting.
 *
 * Usage:
 *   const { buyVsRentConfig, isPropertyRentalAvailable } = useCountryToolConfig();
 */

import { useMemo } from 'react';
import { useUserSettings } from '@/hooks/useUserSettings';
import { getBuyVsRentConfig, type BuyVsRentCountryConfig } from '@/configs/tools/buyVsRentConfig';
import { isPropertyRentalAvailable as checkRentalAvailable } from '@/configs/tools/propertyRentalConfig';
import {
  getEarlyPayoffConfig,
  type EarlyPayoffCountryConfig,
} from '@/configs/tools/earlyPayoffConfig';
import type { BuyRentInputs } from '@/types/realEstateTools';
import { DEFAULT_BUY_RENT_INPUTS } from '@/types/realEstateTools';

export interface CountryToolConfig {
  /** ISO 3166-1 alpha-2 code from the user's settings (default "FR") */
  countryCode: string;
  /** Buy vs Rent country-specific configuration */
  buyVsRentConfig: BuyVsRentCountryConfig;
  /**
   * BuyRentInputs with purchase / market defaults overridden by country config.
   * Use this as the `initialInputs` for `useBuyRentCalculations`.
   */
  buyVsRentInitialInputs: BuyRentInputs;
  /** True when the Property Rental Simulator is available for this country */
  isPropertyRentalAvailable: boolean;
  /** Early payoff penalty (IRA/ERC) rules for this country */
  earlyPayoffConfig: EarlyPayoffCountryConfig;
}

/**
 * Merges a BuyVsRentCountryConfig into DEFAULT_BUY_RENT_INPUTS, overriding
 * only the fields that vary by country.
 */
function buildInitialInputs(cfg: BuyVsRentCountryConfig): BuyRentInputs {
  return {
    ...DEFAULT_BUY_RENT_INPUTS,
    purchase: {
      ...DEFAULT_BUY_RENT_INPUTS.purchase,
      notaryFeesPercent: cfg.notaryFeesPercent,
      guaranteeFees: cfg.guaranteeFees,
      applicationFees: cfg.applicationFees,
      accountFees: cfg.accountFees,
      garbageTax: cfg.garbageTax,
      interestRate: cfg.interestRate,
    },
    rental: {
      ...DEFAULT_BUY_RENT_INPUTS.rental,
      garbageTax: cfg.garbageTax,
    },
    market: {
      ...DEFAULT_BUY_RENT_INPUTS.market,
      priceEvolution: cfg.priceEvolution,
      rentEvolution: cfg.rentEvolution,
    },
  };
}

export function useCountryToolConfig(): CountryToolConfig {
  const { data: settings } = useUserSettings();
  const countryCode = settings?.country ?? 'FR';

  return useMemo(() => {
    const cfg = getBuyVsRentConfig(countryCode);
    return {
      countryCode,
      buyVsRentConfig: cfg,
      buyVsRentInitialInputs: buildInitialInputs(cfg),
      isPropertyRentalAvailable: checkRentalAvailable(countryCode),
      earlyPayoffConfig: getEarlyPayoffConfig(countryCode),
    };
  }, [countryCode]);
}
