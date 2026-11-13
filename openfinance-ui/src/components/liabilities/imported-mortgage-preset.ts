import type { LiabilityConversionCandidate } from '@/hooks/useLiabilityConversion';

/** Optional import preset, matched only to identifiers supplied by the deployment. */
export function matchesMortgagePreset(source: LiabilityConversionCandidate): boolean {
  const accountName = import.meta.env.VITE_IMPORT_MORTGAGE_PRESET_ACCOUNT_NAME?.trim();
  return Boolean(
    accountName &&
    source.currency === 'EUR' &&
    source.name.replace(/\s/g, '') === accountName.replace(/\s/g, '')
  );
}

export function matchesMortgagePresetProperty(name: string): boolean {
  const propertyName = import.meta.env.VITE_IMPORT_MORTGAGE_PRESET_PROPERTY_NAME?.trim();
  return Boolean(propertyName && name.trim().toLowerCase() === propertyName.toLowerCase());
}

export function mortgagePresetTerms(language: string) {
  return {
    type: 'MORTGAGE' as const,
    principal: '215000',
    interestRate: '3.65',
    insurancePercentage: '0.20',
    minimumPayment: '1136.66',
    startDate: '2025-06-17',
    endDate: '2050-06-05',
    amortizationStartDate: '2027-01-05',
    notes: language.startsWith('fr')
      ? 'Contrat : opération 225 000 €, apport personnel prévu 10 000 €, prêt 215 000 €, taux nominal 3,65 %, TAEG 4,19 %. Durée totale 300 mois : différé de capital 18 mois (intérêts sur fonds débloqués, maximum 653,96 € + assurance 35,83 €), puis 282 échéances de 1 172,49 € (1 136,66 € + assurance 35,83 €). Fin contractuelle 05/06/2050 ; premier remboursement de capital déduit : 05/01/2027. Commission d’ouverture 1 075 € comprise dans l’échéance importée du 07/07/2025. Garantie contractuelle : fonds mutuel 2 143,50 € et commission 650 € ; leur mention ici ne crée pas de frais ni de paiement.'
      : 'Contract: operation €225,000, planned personal contribution €10,000, approved loan €215,000, nominal rate 3.65%, TAEG 4.19%. Total term 300 months: 18 months of principal deferment (interest on drawn funds, maximum €653.96 + €35.83 insurance), then 282 payments of €1,172.49 (€1,136.66 + €35.83 insurance). Contract ends 2050-06-05; first principal repayment inferred as 2027-01-05. The €1,075 opening fee is included in the imported 2025-07-07 payment. Contractual guarantee: €2,143.50 mutual fund contribution and €650 commission; recording them here does not create fees or payments.',
  };
}
