/**
 * Buy vs Rent Tool — Country-Specific Configuration
 *
 * Each entry overrides the subset of DEFAULT_BUY_RENT_INPUTS that varies by
 * country: acquisition-cost rates, typical recurring taxes, and market defaults.
 *
 * Field semantics (mirrors BuyRentInputs):
 *   notaryFeesPercent   – Transfer/notarial acquisition tax as % of price
 *   guaranteeFees       – Loan guarantee cost in local currency (EUR equivalent)
 *   garbageTax          – Annual municipal waste tax in local currency
 *   resaleFeesPercent   – Agency/transfer fees at resale as % of price
 *   interestRate        – Default annual mortgage rate (TAEG / APR) %
 *   newPropertyTaxExemptionYears – Years of property-tax exemption for new builds
 *   applicationFees     – Typical bank application fees in local currency
 *   accountFees         – Annual account maintenance fees in local currency
 *   priceEvolution      – Default annual property appreciation %
 *   rentEvolution       – Default annual rent increase %
 */

import { COUNTRY_CURRENCY_MAP } from '@/constants/countryCurrency';

export interface BuyVsRentCountryConfig {
    /** ISO 3166-1 alpha-2 code */
    countryCode: string;
    /** Human-readable display name (used in UI) */
    displayName: string;
    /** ISO 4217 currency code relevant for this country */
    currency: string;

    // Acquisition costs
    /** Transfer/notarial fees as % of property price (old property default) */
    notaryFeesPercent: number;
    /** Acquisition fees for NEW property as % of price */
    notaryFeesNewPercent: number;
    /** Loan guarantee cost in currency units (0 if not applicable) */
    guaranteeFees: number;
    /** Bank application/origination fees in currency units */
    applicationFees: number;
    /** Annual account maintenance fees in currency units */
    accountFees: number;

    // Recurring taxes
    /** Annual garbage / waste collection tax in currency units */
    garbageTax: number;
    /** Years of property-tax exemption on new builds (0 if not applicable) */
    newPropertyTaxExemptionYears: number;

    // Market defaults
    /** Default annual mortgage interest rate (APR/TAEG) % */
    interestRate: number;
    /** Default annual property price appreciation % */
    priceEvolution: number;
    /** Default annual rent increase % */
    rentEvolution: number;

    /** Resale agency/transfer fees as % of sale price */
    resaleFeesPercent: number;
}

/**
 * Country configurations.
 * Add new entries as more countries are supported.
 */
const configs: Record<string, BuyVsRentCountryConfig> = {
    /**
     * France
     * Sources: Notaries of France, Banque de France, INSEE.
     */
    FR: {
        countryCode: 'FR',
        displayName: 'France',
        currency: COUNTRY_CURRENCY_MAP.FR,
        notaryFeesPercent: 7,          // ~7-8 % old, regulated notarial costs
        notaryFeesNewPercent: 2.5,     // ~2-3 % new build
        guaranteeFees: 2750,           // caution bancaire / hypothèque
        applicationFees: 2000,         // frais de dossier
        accountFees: 720,              // frais de tenue de compte
        garbageTax: 150,               // TEOM (Taxe d'enlèvement des ordures ménagères)
        newPropertyTaxExemptionYears: 2,
        interestRate: 4.2,             // average TAEG 2024
        priceEvolution: 2,
        rentEvolution: 2,
        resaleFeesPercent: 8,          // typical agency commission + transfer fees
    },

    /**
     * United States
     * Sources: Freddie Mac, NAR, IRS.
     */
    US: {
        countryCode: 'US',
        displayName: 'United States',
        currency: COUNTRY_CURRENCY_MAP.US,
        notaryFeesPercent: 2.5,        // closing costs ≈ 2-3 % (title, escrow, recording)
        notaryFeesNewPercent: 2.5,
        guaranteeFees: 0,              // no caution equivalent; PMI is monthly
        applicationFees: 1000,         // origination / underwriting fees
        accountFees: 0,
        garbageTax: 0,                 // typically bundled in property tax or HOA
        newPropertyTaxExemptionYears: 0,
        interestRate: 7.0,             // 30-year fixed average 2024
        priceEvolution: 3,
        rentEvolution: 3,
        resaleFeesPercent: 6,          // typical realtor commission
    },

    /**
     * United Kingdom
     * Sources: HMRC, Bank of England, ONS.
     */
    GB: {
        countryCode: 'GB',
        displayName: 'United Kingdom',
        currency: COUNTRY_CURRENCY_MAP.GB,
        notaryFeesPercent: 2,          // SDLT + conveyancing ≈ 1.5-3 %
        notaryFeesNewPercent: 1.5,
        guaranteeFees: 0,
        applicationFees: 1500,         // arrangement fee
        accountFees: 0,
        garbageTax: 0,                 // council tax covers waste, paid by occupier
        newPropertyTaxExemptionYears: 0,
        interestRate: 5.0,             // average 2024
        priceEvolution: 2.5,
        rentEvolution: 3,
        resaleFeesPercent: 3,          // estate agent fee ≈ 1-3 %
    },

    /**
     * Germany
     * Sources: Bundesbank, IW Köln, Destatis.
     */
    DE: {
        countryCode: 'DE',
        displayName: 'Germany',
        currency: COUNTRY_CURRENCY_MAP.DE,
        notaryFeesPercent: 5,          // Grunderwerbsteuer 3.5-6.5 % + notary ≈ 5-7 %
        notaryFeesNewPercent: 5,
        guaranteeFees: 0,
        applicationFees: 1500,
        accountFees: 0,
        garbageTax: 200,               // Müllabfuhr varies, ~100-300 €/yr
        newPropertyTaxExemptionYears: 0,
        interestRate: 4.0,
        priceEvolution: 1.5,
        rentEvolution: 2,
        resaleFeesPercent: 7,          // Maklerprovision 3-7 %
    },

    /**
     * Belgium
     * Sources: SPF Finances, BNB.
     */
    BE: {
        countryCode: 'BE',
        displayName: 'Belgium',
        currency: COUNTRY_CURRENCY_MAP.BE,
        notaryFeesPercent: 7,          // droits d'enregistrement 10-12.5 % + notary ~2 %
        notaryFeesNewPercent: 3,       // TVA 21 % on new build price + notary
        guaranteeFees: 1500,
        applicationFees: 1000,
        accountFees: 0,
        garbageTax: 100,
        newPropertyTaxExemptionYears: 0,
        interestRate: 4.5,
        priceEvolution: 2,
        rentEvolution: 2,
        resaleFeesPercent: 5,
    },
};

export default configs;

/**
 * Returns the country config for the given ISO 3166-1 alpha-2 code.
 * Falls back to France config when the country is unknown.
 */
export function getBuyVsRentConfig(countryCode: string): BuyVsRentCountryConfig {
    return configs[countryCode.toUpperCase()] ?? configs['FR'];
}

/** All supported country codes for the Buy vs Rent tool */
export const SUPPORTED_BUY_VS_RENT_COUNTRIES = Object.keys(configs);
