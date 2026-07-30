/**
 * BuyRentComparator Component
 * 
 * Main container component for the Buy vs Rent comparison tool
 * Requirements: REQ-1.1.x - REQ-1.7.x
 * 
 * Redesigned: single-page layout without tabs, compact grid structure
 */

import React from 'react';
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
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useCountryToolConfig } from '@/hooks/useCountryToolConfig';

export interface BuyRentComparatorProps {
  onNavigateToRentalSimulator?: (sharedData: SharedPropertyData) => void;
}

export const BuyRentComparator: React.FC<BuyRentComparatorProps> = ({
  onNavigateToRentalSimulator,
}) => {
  const [simulationName, setSimulationName] = React.useState('');
  const [purchaseOpen, setPurchaseOpen] = React.useState(true);
  const [rentalOpen, setRentalOpen] = React.useState(true);
  const [marketOpen, setMarketOpen] = React.useState(true);
  const [resaleOpen, setResaleOpen] = React.useState(true);
  const { baseCurrency } = useAuthContext();
  const { format: formatCurrency } = useFormatCurrency();
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

    const success = await saveSimulation(simulationName, 'buy_rent', inputs);
    if (success) {
      setSimulationName('');
    }
  };

  const handleLoadSimulation = (id: string) => {
    const simulation = loadSimulation(id);
    if (simulation) {
      setInputs(simulation.data as typeof inputs);
    }
  };

  const handleNavigateToRental = () => {
    if (onNavigateToRentalSimulator) {
      const sharedData: SharedPropertyData = {
        totalPrice: derivedValues.totalPrice,
        credit: {
          monthlyPayment: derivedValues.monthlyPayment,
          annualCost: derivedValues.monthlyPayment * 12,
          totalCost: 0,
          assurance: inputs.purchase.totalInsurance / inputs.purchase.loanDuration,
          bankFees: (inputs.purchase.applicationFees + inputs.purchase.guaranteeFees + inputs.purchase.accountFees) / inputs.purchase.loanDuration,
        },
        propertyTax: inputs.purchase.propertyTax,
        coOwnershipCharges: inputs.purchase.coOwnershipCharges,
      };
      onNavigateToRentalSimulator(sharedData);
    }
  };

  const handlePropertySelect = (propertyData: Partial<typeof inputs>) => {
    if (propertyData.purchase) {
      Object.keys(propertyData.purchase).forEach((key) => {
        updatePurchaseInput(key as keyof typeof inputs.purchase, propertyData.purchase![key as keyof typeof inputs.purchase]);
      });
    }
    if (propertyData.rental) {
      Object.keys(propertyData.rental).forEach((key) => {
        updateRentalInput(key as keyof typeof inputs.rental, propertyData.rental![key as keyof typeof inputs.rental]);
      });
    }
    if (propertyData.market) {
      Object.keys(propertyData.market).forEach((key) => {
        updateMarketInput(key as keyof typeof inputs.market, propertyData.market![key as keyof typeof inputs.market]);
      });
    }
    if (propertyData.resale) {
      Object.keys(propertyData.resale).forEach((key) => {
        updateResaleInput(key as keyof typeof inputs.resale, propertyData.resale![key as keyof typeof inputs.resale]);
      });
    }
    calculate();
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
      <PageHeader
        title={t('comparator.title')}
        description={t('comparator.description')}
      />

      {/* Top Bar: Simulation + Property Selector */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
        <SimulationHeader
          simulationName={simulationName}
          onNameChange={setSimulationName}
          onSave={handleSaveSimulation}
          onLoad={handleLoadSimulation}
          onDelete={deleteSimulation}
          simulations={simulations}
          canSave={!hasSimulationWithName(simulationName) && simulationName.trim().length > 0}
        />

        <div className="flex items-center">
          <PropertySelector
            onPropertySelect={handlePropertySelect}
            placeholder={t('comparator.loadProperty')}
            className="w-full"
          />
        </div>
      </div>

      {/* Error Alerts */}
      {generalErrors.length > 0 && (
        <Alert variant="error" className="mb-6">
          <AlertDescription>
            {generalErrors.map(e => e.message).join(', ')}
          </AlertDescription>
        </Alert>
      )}

      {nameError && (
        <Alert variant="error" className="mb-6">
          <AlertDescription>
            {nameError}
          </AlertDescription>
        </Alert>
      )}

      {/* Summary Card */}
      <Card className="p-4 bg-muted/50 mb-6">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
          <div>
            <p className="text-muted-foreground">{t('comparator.totalPrice')}</p>
            <p className="font-semibold">{formatCurrency(derivedValues.totalPrice, baseCurrency)}</p>
          </div>
          <div>
            <p className="text-muted-foreground">{t('comparator.borrowedAmount')}</p>
            <p className="font-semibold">{formatCurrency(derivedValues.borrowedAmount, baseCurrency)}</p>
          </div>
          <div>
            <p className="text-muted-foreground">{t('comparator.monthlyPayment')}</p>
            <p className="font-semibold">{formatCurrency(derivedValues.monthlyPayment, baseCurrency)}{t('comparator.monthly')}</p>
          </div>
          <div>
            <p className="text-muted-foreground">{t('comparator.suggestedSavings')}</p>
            <p className="font-semibold">{formatCurrency(derivedValues.suggestedMonthlySavings, baseCurrency)}{t('comparator.monthly')}</p>
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

        <Button
          variant="outline"
          size="lg"
          onClick={reset}
          disabled={isCalculating}
        >
          <RefreshCw className="mr-2 h-4 w-4" />
          {t('comparator.reset')}
        </Button>

        {onNavigateToRentalSimulator && (
          <Button
            variant="secondary"
            size="lg"
            onClick={handleNavigateToRental}
            disabled={isCalculating}
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
          inputs={inputs}
          getYearNAnalysis={getYearNAnalysis}
          isValidResaleYear={isValidResaleYear}
        />
      )}
    </div>
  );
};

export default BuyRentComparator;
