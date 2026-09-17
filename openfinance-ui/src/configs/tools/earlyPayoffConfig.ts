/**
 * Early Mortgage Payoff Calculator — Country-Specific Configuration
 *
 * IRA (Indemnités de Remboursement Anticipé) rules vary by jurisdiction.
 * Each entry defines whether early-repayment penalties apply and how they
 * are calculated.
 *
 * France source: Code de la consommation, art. L313-47
 *   – Penalty capped at the LOWER of:
 *       (a) 6 months' interest on the reimbursed amount
 *       (b) 3 % of the outstanding capital BEFORE the early repayment
 *   – Waiver situations: job loss, forced relocation for a new job,
 *     sale of primary residence following a professional transfer, etc.
 *   – Some lenders require a minimum prepayment of 10 % of the initial amount.
 */

export interface EarlyPayoffCountryConfig {
  /** ISO 3166-1 alpha-2 country code */
  countryCode: string;
  /** Whether early-repayment penalties (IRA / ERC) apply in this country */
  hasIRA: boolean;
  /**
   * Cap rule (a): penalty as a multiple of monthly interest on the reimbursed
   * amount. E.g. 6 = six months' interest. 0 means this cap does not apply.
   */
  iraMonthsInterestCap: number;
  /**
   * Cap rule (b): penalty as a fraction of the outstanding capital before
   * repayment. E.g. 0.03 = 3 %. 0 means this cap does not apply.
   */
  iraCapitalPercentCap: number;
  /**
   * Minimum prepayment required as a fraction of the ORIGINAL loan amount
   * for the IRA to be triggered (some lenders enforce this).
   * 0 means no minimum threshold applies.
   */
  iraMinPrepaymentFraction: number;
}

/** Country configurations for early payoff penalty rules. */
const configs: Record<string, EarlyPayoffCountryConfig> = {
  /**
   * France
   * Source: Code de la consommation, art. L313-47
   */
  FR: {
    countryCode: 'FR',
    hasIRA: true,
    iraMonthsInterestCap: 6, // plafonné à 6 mois d'intérêts sur le capital remboursé
    iraCapitalPercentCap: 0.03, // plafonné à 3 % du CRD avant remboursement
    iraMinPrepaymentFraction: 0, // no legal minimum; some lenders enforce 10%
  },
};

/** Default config for countries with no specific rules (no penalty). */
const DEFAULT_CONFIG: EarlyPayoffCountryConfig = {
  countryCode: 'OTHER',
  hasIRA: false,
  iraMonthsInterestCap: 0,
  iraCapitalPercentCap: 0,
  iraMinPrepaymentFraction: 0,
};

/**
 * Returns the early-payoff country configuration for the given ISO country code.
 * Falls back to a no-penalty default for unsupported countries.
 */
export function getEarlyPayoffConfig(countryCode: string): EarlyPayoffCountryConfig {
  return configs[countryCode?.toUpperCase()] ?? DEFAULT_CONFIG;
}
