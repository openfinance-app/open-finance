/**
 * RegimeComparisonGrid Component
 * 
 * Grid layout showing all 4 tax regimes side-by-side
 * Requirements: REQ-2.6.x
 */

import React, { useState } from 'react';
import { Download } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ACCORDION_SYNC_BREAKPOINT_NARROW } from '@/constants/breakpoints';
import { Card, CardContent } from '@/components/ui/Card';
import { RegimeCard } from './RegimeCard';
import type { InvestmentResults, TaxRegime, RegimeCalculationResult } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useTranslation } from 'react-i18next';

export interface RegimeComparisonGridProps {
  results: InvestmentResults;
  recommendedRegime: TaxRegime | null;
  eligibleRegimes: TaxRegime[];
  getRegimeResult: (regime: TaxRegime) => RegimeCalculationResult | null;
  isRegimeEligible: (regime: TaxRegime) => boolean;
  forceCollapse?: number;
}

export const RegimeComparisonGrid: React.FC<RegimeComparisonGridProps> = ({
  results,
  recommendedRegime,
  eligibleRegimes,
  getRegimeResult,
  isRegimeEligible,
  forceCollapse,
}) => {
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('realEstate');
  const [microFoncierOpen, setMicroFoncierOpen] = useState(false);
  const [reelFoncierOpen, setReelFoncierOpen] = useState(false);
  const [lmnpReelOpen, setLmnpReelOpen] = useState(false);
  const [microBicOpen, setMicroBicOpen] = useState(false);

  React.useEffect(() => {
    if (forceCollapse) {
      setMicroFoncierOpen(false);
      setReelFoncierOpen(false);
      setLmnpReelOpen(false);
      setMicroBicOpen(false);
    }
  }, [forceCollapse]);

  const regimes: TaxRegime[] = ['micro_foncier', 'reel_foncier', 'lmnp_reel', 'micro_bic'];

  const handleExport = () => {
    // Create export data
    const exportData = {
      date: new Date().toISOString(),
      regimes: {
        microFoncier: results.microFoncier,
        reelFoncier: results.reelFoncier,
        lmnpReel: results.lmnpReel,
        microBic: results.microBic,
      },
    };

    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `${t('regimeGrid.exportFilename')}-${new Date().toISOString().split('T')[0]}.json`;
    link.click();
  };

  const toggleMicroFoncier = () => {
    setMicroFoncierOpen(p => {
      const n = !p;
      if (window.innerWidth >= ACCORDION_SYNC_BREAKPOINT_NARROW) setReelFoncierOpen(n);
      return n;
    });
  };

  const toggleReelFoncier = () => {
    setReelFoncierOpen(p => {
      const n = !p;
      if (window.innerWidth >= ACCORDION_SYNC_BREAKPOINT_NARROW) setMicroFoncierOpen(n);
      return n;
    });
  };

  const toggleLmnpReel = () => {
    setLmnpReelOpen(p => {
      const n = !p;
      if (window.innerWidth >= ACCORDION_SYNC_BREAKPOINT_NARROW) setMicroBicOpen(n);
      return n;
    });
  };

  const toggleMicroBic = () => {
    setMicroBicOpen(p => {
      const n = !p;
      if (window.innerWidth >= ACCORDION_SYNC_BREAKPOINT_NARROW) setLmnpReelOpen(n);
      return n;
    });
  };

  return (
    <div className="space-y-6">
      {/* Export Button */}
      <div className="flex justify-end">
        <Button variant="outline" onClick={handleExport}>
          <Download className="mr-2 h-4 w-4" />
          {t('regimeGrid.exportResults')}
        </Button>
      </div>

      {/* Summary */}
      <Card className="bg-success/5 border-success/20">
        <CardContent className="p-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-center">
            <div>
              <p className="text-sm text-muted-foreground">{t('regimeGrid.eligibleRegimes')}</p>
              <p className="text-2xl font-bold">{eligibleRegimes.length}/4</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('regimeGrid.bestCashFlow')}</p>
              <p className="text-2xl font-bold text-green-600">
                <ConvertedAmount
                  amount={Math.max(
                    ...regimes.map(r => getRegimeResult(r)?.performance.monthlyCashFlow || 0)
                  )}
                  currency={baseCurrency}
                  inline
                />
              </p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('regimeGrid.bestNetYield')}</p>
              <p className="text-2xl font-bold text-primary">
                {Math.max(
                  ...regimes.map(r => getRegimeResult(r)?.performance.netYield || 0)
                ).toFixed(2)}%
              </p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('regimeGrid.recommendedRegime')}</p>
              <p className="text-lg font-bold text-success">
                {recommendedRegime ?
                  recommendedRegime === 'micro_foncier' ? t('regimeGrid.regimeNameMicroFoncier') :
                    recommendedRegime === 'reel_foncier' ? t('regimeGrid.regimeNameReelFoncier') :
                      recommendedRegime === 'lmnp_reel' ? t('regimeGrid.regimeNameLmnpReel') : t('regimeGrid.regimeNameMicroBic')
                  : '-'
                }
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Regime Cards — 2×2 grid (each card individually collapsible) */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {regimes.map((regime) => {
          const result = getRegimeResult(regime);
          if (!result) return null;

          let isOpen = true;
          let onToggle = () => { };

          switch (regime) {
            case 'micro_foncier':
              isOpen = microFoncierOpen;
              onToggle = toggleMicroFoncier;
              break;
            case 'reel_foncier':
              isOpen = reelFoncierOpen;
              onToggle = toggleReelFoncier;
              break;
            case 'lmnp_reel':
              isOpen = lmnpReelOpen;
              onToggle = toggleLmnpReel;
              break;
            case 'micro_bic':
              isOpen = microBicOpen;
              onToggle = toggleMicroBic;
              break;
          }

          return (
            <RegimeCard
              key={regime}
              regime={regime}
              result={result}
              isRecommended={regime === recommendedRegime}
              isOpen={isOpen}
              onToggle={onToggle}
            />
          );
        })}
      </div>

      {/* Comparison Table */}
      <Card>
        <CardContent className="p-6">
          <h3 className="text-lg font-semibold mb-4">{t('regimeGrid.comparisonTable')}</h3>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-2 px-4">{t('regimeGrid.criteria')}</th>
                  {regimes.map((regime) => (
                    <th key={regime} className="text-center py-2 px-4">
                      {regime === 'micro_foncier' ? t('regimeGrid.regimeNameMicroFoncier') :
                        regime === 'reel_foncier' ? t('regimeGrid.regimeNameReelFoncier') :
                          regime === 'lmnp_reel' ? t('regimeGrid.regimeNameLmnpReel') : t('regimeGrid.regimeNameMicroBic')}
                      {regime === recommendedRegime && (
                        <span className="ml-2 text-xs bg-green-100 text-green-800 px-2 py-1 rounded">
                          {t('regimeCard.recommended')}
                        </span>
                      )}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                <tr className="border-b">
                  <td className="py-2 px-4 font-medium">{t('regimeGrid.eligible')}</td>
                  {regimes.map((regime) => (
                    <td key={regime} className="text-center py-2 px-4">
                      {isRegimeEligible(regime) ? (
                        <span className="text-green-600">✓</span>
                      ) : (
                        <span className="text-red-600">✗</span>
                      )}
                    </td>
                  ))}
                </tr>
                <tr className="border-b">
                  <td className="py-2 px-4 font-medium">{t('regimeGrid.monthlyCashFlow')}</td>
                  {regimes.map((regime) => {
                    const result = getRegimeResult(regime);
                    return (
                      <td key={regime} className="text-center py-2 px-4">
                        {result ? (
                          <ConvertedAmount
                            amount={result.performance.monthlyCashFlow}
                            currency={baseCurrency}
                            inline
                          />
                        ) : (
                          '-'
                        )}
                      </td>
                    );
                  })}
                </tr>
                <tr className="border-b">
                  <td className="py-2 px-4 font-medium">{t('regimeGrid.grossProfitability')}</td>
                  {regimes.map((regime) => {
                    const result = getRegimeResult(regime);
                    return (
                      <td key={regime} className="text-center py-2 px-4">
                        {result ? `${result.performance.grossYield.toFixed(2)}%` : '-'}
                      </td>
                    );
                  })}
                </tr>
                <tr className="border-b">
                  <td className="py-2 px-4 font-medium">{t('regimeGrid.netProfitability')}</td>
                  {regimes.map((regime) => {
                    const result = getRegimeResult(regime);
                    return (
                      <td key={regime} className={`text-center py-2 px-4 font-semibold ${regime === recommendedRegime ? 'text-green-600' : ''
                        }`}>
                        {result ? `${result.performance.netYield.toFixed(2)}%` : '-'}
                      </td>
                    );
                  })}
                </tr>
                <tr className="border-b">
                  <td className="py-2 px-4 font-medium">{t('regimeGrid.taxableIncome')}</td>
                  {regimes.map((regime) => {
                    const result = getRegimeResult(regime);
                    return (
                      <td key={regime} className="text-center py-2 px-4">
                        {result ? (
                          <ConvertedAmount
                            amount={result.revenue.taxable}
                            currency={baseCurrency}
                            inline
                          />
                        ) : (
                          '-'
                        )}
                      </td>
                    );
                  })}
                </tr>
                <tr className="border-b">
                  <td className="py-2 px-4 font-medium">{t('regimeGrid.totalTaxes')}</td>
                  {regimes.map((regime) => {
                    const result = getRegimeResult(regime);
                    return (
                      <td key={regime} className="text-center py-2 px-4 text-red-600">
                        {result ? (
                          <ConvertedAmount
                            amount={result.taxation.totalTaxes}
                            currency={baseCurrency}
                            inline
                          />
                        ) : (
                          '-'
                        )}
                      </td>
                    );
                  })}
                </tr>
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default RegimeComparisonGrid;
