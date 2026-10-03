/**
 * BuyRentComparator Component
 *
 * Main container component for the Buy vs Rent comparison tool
 * Requirements: REQ-1.1.x - REQ-1.7.x
 *
 * Redesigned: single-page layout without tabs, compact grid structure
 */

import React from 'react';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { convertBuyRentInputs } from '@/utils/simulation-currency';
import type { BuyRentInputs } from '@/types/realEstateTools';
import { isBuyRentInputs } from '@/validators/simulationShape';
import { useConvertCurrency } from '@/hooks/useCurrency';
import { multiply, roundToDecimals } from '@/utils/money';
import { Calculator, Save, ArrowRight, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ACCORDION_SYNC_BREAKPOINT } from '@/constants/breakpoints';
import { Card } from '@/components/ui/Card';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/PageHeader';
import { useBuyRentCalculations } from '@/hooks/useBuyRentCalculations';
import { useSimulationStorage } from '@/hooks/useSimulationStorage';
import { SimulationHeader } from './SimulationHeader';
import { PurchaseSection } from './BuyRentForm/PurchaseSection';
import { RentalSection } from './BuyRentForm/RentalSection';
import { MarketSection } from './BuyRentForm/MarketSection';
import { ResaleSection } from './BuyRentForm/ResaleSection';
import { ResultsPanel } from './ResultsPanel';
import type { SharedPropertyData } from '@/types/realEstateTools';
import { PropertySelector } from './PropertySelector';
import { useAuthContext } from '@/context/AuthContext';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useCountryToolConfig } from '@/hooks/useCountryToolConfig';

export interface BuyRentComparatorProps {
  onNavigateToRentalSimulator?: (sharedData: SharedPropertyData) => void;
}

export const BuyRentComparator: React.FC<BuyRentComparatorProps> = ({
  onNavigateToRentalSimulator,
}) => {
  const [simulationName, setSimulationName] = React.useState('');
  const [legacyInputs, setLegacyInputs] = React.useState<BuyRentInputs | null>(null);
  const [legacyCurrency, setLegacyCurrency] = React.useState<string>();
  const [currencyNotice, setCurrencyNotice] = React.useState<string | null>(null);
  const [purchaseOpen, setPurchaseOpen] = React.useState(true);
  const [rentalOpen, setRentalOpen] = React.useState(true);
  const [marketOpen, setMarketOpen] = React.useState(true);
  const [resaleOpen, setResaleOpen] = React.useState(true);
  const { baseCurrency } = useAuthContext();
  const conversion = useConvertCurrency();
  const [transferError, setTransferError] = React.useState<string | null>(null);
  const { buyVsRentInitialInputs } = useCountryToolConfig();
  const { t } = useTranslation('realEstate');

  const {
    inputs,
    results,
    isCalculating,
    errors,
    derivedValues,
    updatePurchaseInput,
    updateRentalInput,
    updateMarketInput,
    updateResaleInput,
    calculate,
    reset,
    setInputs,
    getYearNAnalysis,
    isValidResaleYear,
  } = useBuyRentCalculations(buyVsRentInitialInputs);

  const {
    simulations,
    saveSimulation,
    loadSimulation,
    deleteSimulation,
    hasSimulationWithName,
    error: storageError,
    isSaving,
  } = useSimulationStorage();

  const hasErrors = errors.length > 0;
  const generalErrors = errors.filter(e => e.field === 'general');
  const [nameError, setNameError] = React.useState<string | null>(null);

  const handleSaveSimulation = async () => {
    if (!simulationName.trim()) {
      setNameError(t('comparator.emptyNameError'));
      return;
    }
    setNameError(null);

    const success = await saveSimulation(simulationName, 'buy_rent', {
      ...inputs,
      currency: baseCurrency,
    });
    if (success) {
      setSimulationName('');
    }
  };

  const loadInCurrentCurrency = async (data: BuyRentInputs, sourceCurrency: string) => {
    setNameError(null);
    setCurrencyNotice(null);
    try {
      const rate =
        sourceCurrency === baseCurrency
          ? 1
          : (
              await conversion.mutateAsync({
                amount: 1,
                fromCurrency: sourceCurrency,
                toCurrency: baseCurrency,
              })
            ).exchangeRate;
      setInputs(convertBuyRentInputs(data, baseCurrency, rate));
      setLegacyInputs(null);
      if (sourceCurrency !== baseCurrency) {
        setCurrencyNotice(
          t('comparator.convertedSimulation', { from: sourceCurrency, to: baseCurrency })
        );
      }
    } catch {
      setNameError(t('comparator.conversionFailed'));
    }
  };

  const handleLoadSimulation = (id: string) => {
    const simulation = loadSimulation(id);
    setLegacyInputs(null);
    setCurrencyNotice(null);
    if (simulation?.metadata.type !== 'buy_rent' || !isBuyRentInputs(simulation.data)) {
      setNameError(t('validation.invalidSimulation'));
      return;
    }
    if (!simulation.data.currency) {
      setNameError(null);
      setLegacyCurrency(undefined);
      setLegacyInputs(simulation.data);
      return;
    }
    void loadInCurrentCurrency(simulation.data, simulation.data.currency);
  };

  const handleNavigateToRental = async () => {
    setTransferError(null);
    if (onNavigateToRentalSimulator) {
      const sharedData: SharedPropertyData = {
        currency: baseCurrency,
        totalPrice: derivedValues.totalPrice,
        credit: {
          monthlyPayment: derivedValues.monthlyPayment,
          annualCost: derivedValues.monthlyPayment * 12,
          totalCost: 0,
          assurance: inputs.purchase.totalInsurance / inputs.purchase.loanDuration,
          bankFees:
            (inputs.purchase.applicationFees +
              inputs.purchase.guaranteeFees +
              inputs.purchase.accountFees) /
            inputs.purchase.loanDuration,
        },
        propertyTax: inputs.purchase.propertyTax,
        coOwnershipCharges: inputs.purchase.coOwnershipCharges,
      };
      try {
        const rate =
          baseCurrency === 'EUR'
            ? 1
            : (
                await conversion.mutateAsync({
                  amount: 1,
                  fromCurrency: baseCurrency,
                  toCurrency: 'EUR',
                })
              ).exchangeRate;
        if (!Number.isFinite(rate) || rate <= 0) throw new Error('Invalid exchange rate');
        const amount = (value: number): number => roundToDecimals(multiply(value, rate), 2);
        onNavigateToRentalSimulator({
          currency: 'EUR',
          totalPrice: amount(sharedData.totalPrice),
          propertyTax: amount(sharedData.propertyTax),
          coOwnershipCharges: amount(sharedData.coOwnershipCharges),
          credit: {
            monthlyPayment: amount(sharedData.credit.monthlyPayment),
            annualCost: amount(sharedData.credit.annualCost),
            totalCost: amount(sharedData.credit.totalCost),
            assurance: amount(sharedData.credit.assurance),
            bankFees: amount(sharedData.credit.bankFees),
          },
        });
      } catch {
        setTransferError(t('validation.currencyConversion'));
      }
    }
  };

  const handlePropertySelect = (propertyData: Partial<typeof inputs>) => {
    calculate({
      purchase: { ...inputs.purchase, ...propertyData.purchase },
      rental: { ...inputs.rental, ...propertyData.rental },
      market: { ...inputs.market, ...propertyData.market },
      resale: { ...inputs.resale, ...propertyData.resale },
    });
  };

  const handleCalculate = () => {
    calculate();
    setPurchaseOpen(false);
    setRentalOpen(false);
    setMarketOpen(false);
    setResaleOpen(false);
  };

  const togglePurchase = () => {
    setPurchaseOpen(prev => {
      const next = !prev;
      if (window.innerWidth >= ACCORDION_SYNC_BREAKPOINT) setRentalOpen(next);
      return next;
    });
  };

  const toggleRental = () => {
    setRentalOpen(prev => {
      const next = !prev;
      if (window.innerWidth >= ACCORDION_SYNC_BREAKPOINT) setPurchaseOpen(next);
      return next;
    });
  };

  const toggleMarket = () => {
    setMarketOpen(prev => {
      const next = !prev;
      if (window.innerWidth >= ACCORDION_SYNC_BREAKPOINT) setResaleOpen(next);
      return next;
    });
  };

  const toggleResale = () => {
    setResaleOpen(prev => {
      const next = !prev;
      if (window.innerWidth >= ACCORDION_SYNC_BREAKPOINT) setMarketOpen(next);
      return next;
    });
  };

  return (
    <div className="container mx-auto px-4 py-8 max-w-7xl">
      <PageHeader title={t('comparator.title')} description={t('comparator.description')} />
      {storageError && (
        <Alert variant="error" role="alert" className="mb-4">
          <AlertDescription>{storageError}</AlertDescription>
        </Alert>
      )}

      {/* Top Bar: Simulation + Property Selector */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
        <SimulationHeader
          simulationType="buy_rent"
          simulationName={simulationName}
          onNameChange={setSimulationName}
          onSave={handleSaveSimulation}
          onLoad={handleLoadSimulation}
          onDelete={deleteSimulation}
          simulations={simulations}
          canSave={
            !isSaving &&
            !hasErrors &&
            !hasSimulationWithName(simulationName) &&
            simulationName.trim().length > 0
          }
        />

        <div className="flex items-center">
          <PropertySelector
            onPropertySelect={handlePropertySelect}
            placeholder={t('comparator.loadProperty')}
            className="w-full"
          />
        </div>
      </div>

      {transferError && (
        <Alert variant="error" className="mb-6">
          <AlertDescription>{transferError}</AlertDescription>
        </Alert>
      )}
      {/* Error Alerts */}
      {generalErrors.length > 0 && (
        <Alert variant="error" className="mb-6">
          <AlertDescription>{generalErrors.map(e => e.message).join(', ')}</AlertDescription>
        </Alert>
      )}

      {nameError && (
        <Alert variant="error" className="mb-6">
          <AlertDescription>{nameError}</AlertDescription>
        </Alert>
      )}

      {legacyInputs && (
        <div className="mb-6 space-y-3" role="group" aria-label={t('comparator.originalCurrency')}>
          <p>{t('comparator.legacyCurrency')}</p>
          <CurrencySelector
            value={legacyCurrency}
            onValueChange={setLegacyCurrency}
            placeholder={t('comparator.originalCurrency')}
          />
          <Button
            disabled={!legacyCurrency || conversion.isPending}
            onClick={() =>
              legacyCurrency && void loadInCurrentCurrency(legacyInputs, legacyCurrency)
            }
          >
            {t('comparator.loadWithCurrency')}
          </Button>
        </div>
      )}
      {currencyNotice && (
        <p role="status" className="mb-6 text-sm">
          {currencyNotice}
        </p>
      )}
      <p className="mb-4 text-sm text-muted-foreground">{t('comparator.commonBudget')}</p>

      {/* Summary Card */}
      <Card className="p-4 bg-muted/50 mb-6">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
          <div>
            <p className="text-muted-foreground">{t('comparator.totalPrice')}</p>
            <p className="font-semibold">
              <ConvertedAmount amount={derivedValues.totalPrice} currency={baseCurrency} inline />
            </p>
          </div>
          <div>
            <p className="text-muted-foreground">{t('comparator.borrowedAmount')}</p>
            <p className="font-semibold">
              <ConvertedAmount
                amount={derivedValues.borrowedAmount}
                currency={baseCurrency}
                inline
              />
            </p>
          </div>
          <div>
            <p className="text-muted-foreground">{t('comparator.monthlyPayment')}</p>
            <p className="font-semibold">
              <ConvertedAmount
                amount={derivedValues.monthlyPayment}
                currency={baseCurrency}
                inline
              />
              {t('comparator.monthly')}
            </p>
          </div>
          <div>
            <p className="text-muted-foreground">{t('comparator.suggestedSavings')}</p>
            <p className="font-semibold">
              <ConvertedAmount
                amount={derivedValues.suggestedMonthlySavings}
                currency={baseCurrency}
                inline
              />
              {t('comparator.monthly')}
            </p>
          </div>
        </div>
      </Card>

      {/* Input Sections Grid - 2 columns, paired collapse on desktop */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <PurchaseSection
          inputs={inputs.purchase}
          derivedValues={derivedValues}
          errors={errors.filter(e => e.field.startsWith('purchase.'))}
          onUpdate={updatePurchaseInput}
          isOpen={purchaseOpen}
          onToggle={togglePurchase}
        />

        <RentalSection
          inputs={inputs.rental}
          errors={errors.filter(e => e.field.startsWith('rental.'))}
          onUpdate={updateRentalInput}
          isOpen={rentalOpen}
          onToggle={toggleRental}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <MarketSection
          inputs={inputs.market}
          errors={errors.filter(e => e.field.startsWith('market.'))}
          onUpdate={updateMarketInput}
          isOpen={marketOpen}
          onToggle={toggleMarket}
        />

        <ResaleSection
          inputs={inputs.resale}
          loanDuration={inputs.purchase.loanDuration}
          errors={errors.filter(e => e.field.startsWith('resale.'))}
          onUpdate={updateResaleInput}
          isValid={isValidResaleYear}
          isOpen={resaleOpen}
          onToggle={toggleResale}
        />
      </div>

      {/* Action Buttons */}
      <div className="flex flex-col sm:flex-row gap-3 justify-center mb-8">
        <Button
          size="lg"
          onClick={handleCalculate}
          disabled={isCalculating || hasErrors}
          className="min-w-[200px]"
        >
          {isCalculating ? (
            <>
              <div className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
              {t('comparator.calculating')}
            </>
          ) : (
            <>
              <Calculator className="mr-2 h-4 w-4" />
              {t('comparator.calculate')}
            </>
          )}
        </Button>

        <Button variant="outline" size="lg" onClick={reset} disabled={isCalculating}>
          <RefreshCw className="mr-2 h-4 w-4" />
          {t('comparator.reset')}
        </Button>

        {onNavigateToRentalSimulator && (
          <Button
            variant="secondary"
            size="lg"
            onClick={handleNavigateToRental}
            disabled={isCalculating || hasErrors || conversion.isPending}
          >
            <Save className="mr-2 h-4 w-4" />
            {t('comparator.simulateRental')}
            <ArrowRight className="ml-2 h-4 w-4" />
          </Button>
        )}
      </div>

      {/* Results - shown inline below inputs when available */}
      {results && (
        <ResultsPanel
          results={results}
          inputs={{ ...inputs, currency: baseCurrency }}
          getYearNAnalysis={getYearNAnalysis}
          isValidResaleYear={isValidResaleYear}
        />
      )}
    </div>
  );
};

export default BuyRentComparator;
