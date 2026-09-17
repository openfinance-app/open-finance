/**
 * Property Rental Simulator — Country Availability & Configuration
 *
 * The Property Rental Simulator implements the **French** rental tax-regime
 * framework (Micro-Foncier, Réel Foncier, Micro-BIC, LMNP Réel).
 * These regimes — and the underlying thresholds, abatements, social contribution
 * rates and depreciation rules — are defined by French law and have no direct
 * equivalent in other countries.
 *
 * The simulator is therefore restricted to users whose country setting is in
 * PROPERTY_RENTAL_SUPPORTED_COUNTRIES.  Showing it to non-French users would
 * produce misleading results.
 */

/**
 * Countries for which the Property Rental Simulator is available.
 * Currently France only; extend this list if equivalent simulators are added
 * for other jurisdictions.
 */
export const PROPERTY_RENTAL_SUPPORTED_COUNTRIES: ReadonlyArray<string> = ['FR'];

/**
 * Returns true when the simulator is available for the given country code.
 *
 * @param countryCode – ISO 3166-1 alpha-2 code from UserSettings (e.g. "FR")
 */
export function isPropertyRentalAvailable(countryCode: string): boolean {
  return PROPERTY_RENTAL_SUPPORTED_COUNTRIES.includes(countryCode.toUpperCase());
}

/**
 * French rental simulator configuration.
 *
 * All values are derived from French law / fiscal texts; the constants here
 * are the canonical source-of-truth used by taxRegimeCalculations.ts.
 *
 * DO NOT change these without verifying against current French tax legislation.
 */
export const FRANCE_RENTAL_CONFIG = {
  country: 'FR',

  regimeLimits: {
    /** Micro-Foncier gross revenue ceiling (Art. 32 CGI) */
    microFoncier: 15_000,
    /** Micro-BIC gross revenue ceiling (Art. 50-0 CGI) */
    microBic: 77_700,
    /** LMP vs LMNP threshold: annual rental revenues (Art. 151 septies CGI) */
    lmnpSocialThreshold: 23_000,
  },

  regimeRates: {
    /** Flat-rate deduction for Micro-Foncier */
    microFoncierAbatement: 0.3,
    /** Flat-rate deduction for Micro-BIC */
    microBicAbatement: 0.5,
    /** CSG/CRDS rate for standard rental income (prélèvements sociaux) */
    socialContributionsStandard: 0.172,
    /** Social contribution rate for LMP status (cotisations sociales TNS) */
    socialContributionsLmp: 0.45,
    /** LMNP building straight-line depreciation period in years */
    buildingDepreciationYears: 25,
    /** LMNP furniture straight-line depreciation period in years */
    furnitureDepreciationYears: 5,
  },
} as const;

export type FranceRentalConfig = typeof FRANCE_RENTAL_CONFIG;
