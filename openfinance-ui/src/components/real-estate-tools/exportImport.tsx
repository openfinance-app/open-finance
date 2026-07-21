/**
 * Export/Import Utilities for Real Estate Tools
 * 
 * Export and import simulation data in various formats
 * Requirements: REQ-6.5
 */

import type {
  BuyRentInputs,
  InvestmentInputs,
  SavedSimulation,
  BuyRentResults,
  InvestmentResults,
} from '@/types/realEstateTools';
import { DEFAULT_CURRENCY, formatCurrency } from '@/utils/currency';

export type ExportFormat = 'json' | 'csv' | 'pdf';

/**
 * Export simulation to JSON
 */
export function exportToJSON(
  simulation: SavedSimulation,
  results?: BuyRentResults | InvestmentResults
): string {
  const exportData = {
    metadata: {
      ...simulation.metadata,
      exportedAt: new Date().toISOString(),
    },
    inputs: simulation.data,
    results: results || null,
    version: '1.0.0',
  };

  return JSON.stringify(exportData, null, 2);
}

/**
 * Export Buy/Rent results to CSV
 */
export function exportBuyRentToCSV(
  _inputs: BuyRentInputs,
  results: BuyRentResults
): string {
  const headers = [
    'Année',
    'Coût annuel achat',
    'Coût cumulé achat',
    'Valeur du bien',
    'Capital restant',
    'Prix revente min',
    'Coût annuel location',
    'Coût cumulé location',
    'Épargne cumulée',
    'Patrimoine net achat',
    'Patrimoine net location',
  ];

  const rows = results.years.map((year) => [
    year.year,
    year.buy.annualCost.toFixed(2),
    year.buy.cumulativeCost.toFixed(2),
    year.buy.propertyValue.toFixed(2),
    year.buy.remainingCapital.toFixed(2),
    year.buy.minimumResalePrice.toFixed(2),
    year.rent.annualCost.toFixed(2),
    year.rent.cumulativeCost.toFixed(2),
    year.rent.savings.toFixed(2),
    (year.buy.propertyValue - year.buy.remainingCapital).toFixed(2),
    year.rent.savings.toFixed(2),
  ]);

  // Add summary row
  const summaryRow = [
    'TOTAL',
    '',
    results.summary.buy.totalCost.toFixed(2),
    results.summary.buy.finalPropertyValue.toFixed(2),
    results.summary.buy.remainingCapital.toFixed(2),
    '',
    '',
    results.summary.rent.totalCost.toFixed(2),
    results.summary.rent.accumulatedSavings.toFixed(2),
    results.summary.buy.netWorth.toFixed(2),
    results.summary.rent.netWorth.toFixed(2),
  ];

  return [
    headers.join(';'),
    ...rows.map((row) => row.join(';')),
    summaryRow.join(';'),
  ].join('\n');
}

/**
 * Export Investment results to CSV
 */
export function exportInvestmentToCSV(
  _inputs: InvestmentInputs,
  results: InvestmentResults
): string {
  const headers = [
    'Régime',
    'Éligible',
    'Revenu brut',
    'Déduction',
    'Revenu imposable',
    'Impôt sur le revenu',
    'Prélèvements sociaux',
    'Total impôts',
    'Cash-flow mensuel',
    'Rentabilité brute',
    'Rentabilité nette',
  ];

  const regimes = [
    { key: 'microFoncier', name: 'Micro-Foncier' },
    { key: 'reelFoncier', name: 'Réel Foncier' },
    { key: 'lmnpReel', name: 'LMNP Réel' },
    { key: 'microBic', name: 'Micro-BIC' },
  ] as const;

  const rows = regimes.map(({ key, name }) => {
    const result = results[key];
    return [
      name,
      result.eligible ? 'Oui' : 'Non',
      result.revenue.gross.toFixed(2),
      result.revenue.deduction.toFixed(2),
      result.revenue.taxable.toFixed(2),
      result.taxation.incomeTax.toFixed(2),
      result.taxation.socialContributions.toFixed(2),
      result.taxation.totalTaxes.toFixed(2),
      result.performance.monthlyCashFlow.toFixed(2),
      result.performance.grossYield.toFixed(2),
      result.performance.netYield.toFixed(2),
    ];
  });

  return [headers.join(';'), ...rows.map((row) => row.join(';'))].join('\n');
}

/**
 * Generate PDF content (returns HTML for print-to-PDF)
 */
export function generatePDFContent(
  title: string,
  _inputs: BuyRentInputs | InvestmentInputs,
  results: BuyRentResults | InvestmentResults,
  type: 'buy_rent' | 'rental_investment',
  baseCurrency: string = DEFAULT_CURRENCY
): string {
  const date = new Date().toLocaleDateString('fr-FR');

  let content = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="UTF-8">
      <title>${title}</title>
      <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        h1 { color: #333; border-bottom: 2px solid #333; padding-bottom: 10px; }
        h2 { color: #666; margin-top: 30px; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f5f5f5; }
        .summary { background-color: #f9f9f9; padding: 15px; margin: 20px 0; }
        .footer { margin-top: 40px; font-size: 12px; color: #999; }
      </style>
    </head>
    <body>
      <h1>${title}</h1>
      <p>Date: ${date}</p>
  `;

  if (type === 'buy_rent') {
    const brResults = results as BuyRentResults;
    content += `
      <div class="summary">
        <h2>Résumé</h2>
        <p><strong>Scénario gagnant:</strong> ${brResults.summary.comparison.winner === 'buy' ? 'Achat' : 'Location'}</p>
        <p><strong>Différence de patrimoine:</strong> ${formatCurrency(brResults.summary.comparison.netWorthDifference, baseCurrency)}</p>
      </div>
      
      <h2>Détails par année</h2>
      <table>
        <thead>
          <tr>
            <th>Année</th>
            <th>Patrimoine Achat</th>
            <th>Patrimoine Location</th>
            <th>Avantage</th>
          </tr>
        </thead>
        <tbody>
          ${brResults.years.map(y => `
            <tr>
              <td>${y.year}</td>
              <td>${formatCurrency(y.buy.propertyValue - y.buy.remainingCapital, baseCurrency)}</td>
              <td>${formatCurrency(y.rent.savings, baseCurrency)}</td>
              <td>${(y.buy.propertyValue - y.buy.remainingCapital) > y.rent.savings ? 'Achat' : 'Location'}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    `;
  } else {
    const invResults = results as InvestmentResults;
    content += `
      <h2>Comparaison des régimes fiscaux</h2>
      <table>
        <thead>
          <tr>
            <th>Régime</th>
            <th>Éligible</th>
            <th>Rentabilité nette</th>
            <th>Cash-flow mensuel</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Micro-Foncier</td>
            <td>${invResults.microFoncier.eligible ? 'Oui' : 'Non'}</td>
            <td>${invResults.microFoncier.performance.netYield.toFixed(2)}%</td>
            <td>${formatCurrency(invResults.microFoncier.performance.monthlyCashFlow, baseCurrency)}</td>
          </tr>
          <tr>
            <td>Réel Foncier</td>
            <td>${invResults.reelFoncier.eligible ? 'Oui' : 'Non'}</td>
            <td>${invResults.reelFoncier.performance.netYield.toFixed(2)}%</td>
            <td>${formatCurrency(invResults.reelFoncier.performance.monthlyCashFlow, baseCurrency)}</td>
          </tr>
          <tr>
            <td>LMNP Réel</td>
            <td>${invResults.lmnpReel.eligible ? 'Oui' : 'Non'}</td>
            <td>${invResults.lmnpReel.performance.netYield.toFixed(2)}%</td>
            <td>${formatCurrency(invResults.lmnpReel.performance.monthlyCashFlow, baseCurrency)}</td>
          </tr>
          <tr>
            <td>Micro-BIC</td>
            <td>${invResults.microBic.eligible ? 'Oui' : 'Non'}</td>
            <td>${invResults.microBic.performance.netYield.toFixed(2)}%</td>
            <td>${formatCurrency(invResults.microBic.performance.monthlyCashFlow, baseCurrency)}</td>
          </tr>
        </tbody>
      </table>
    `;
  }

  content += `
      <div class="footer">
        <p>Généré par Open-Finance - Ces résultats sont fournis à titre indicatif.</p>
      </div>
    </body>
    </html>
  `;

  return content;
}

/**
 * Import simulation from JSON
 */
export function importFromJSON(jsonString: string): {
  success: boolean;
  data?: SavedSimulation;
  error?: string;
} {
  try {
    const parsed = JSON.parse(jsonString);

    // Validate structure
    if (!parsed.metadata || !parsed.inputs) {
      return { success: false, error: 'Format invalide: métadonnées ou données manquantes' };
    }

    // Check version compatibility
    if (parsed.version && parsed.version !== '1.0.0') {
      console.warn(`Version mismatch: ${parsed.version} vs 1.0.0`);
    }

    const simulation: SavedSimulation = {
      metadata: {
        ...parsed.metadata,
        id: crypto.randomUUID(), // Generate new ID to avoid conflicts
        createdAt: new Date(parsed.metadata.createdAt),
        updatedAt: new Date(),
      },
      data: parsed.inputs,
    };

    return { success: true, data: simulation };
  } catch (error) {
    return { success: false, error: 'Erreur de parsing JSON' };
  }
}

/**
 * Download file helper
 */
export function downloadFile(content: string, filename: string, mimeType: string) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/**
 * Print to PDF helper
 */
export function printToPDF(htmlContent: string, title: string) {
  const printWindow = window.open('', '_blank');
  if (!printWindow) {
    alert('Veuillez autoriser les pop-ups pour imprimer en PDF');
    return;
  }

  printWindow.document.write(htmlContent);
  printWindow.document.close();
  printWindow.document.title = title;

  // Wait for content to load then print
  setTimeout(() => {
    printWindow.print();
    // Close window after print dialog (optional)
    // printWindow.close();
  }, 250);
}

export default {
  exportToJSON,
  exportBuyRentToCSV,
  exportInvestmentToCSV,
  generatePDFContent,
  importFromJSON,
  downloadFile,
  printToPDF,
};
