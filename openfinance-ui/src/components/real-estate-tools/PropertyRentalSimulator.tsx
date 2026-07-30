import React from 'react';
import { Calculator, ArrowLeft, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ACCORDION_SYNC_BREAKPOINT } from '@/constants/breakpoints';
import { Card } from '@/components/ui/Card';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { PageHeader } from '@/components/layout/PageHeader';
import { useTranslation } from 'react-i18next';
import { useRentalSimulator } from '@/hooks/useRentalSimulator';
import { useSimulationStorage } from '@/hooks/useSimulationStorage';
import { SimulationHeader } from './SimulationHeader';
import { SharedParametersPanel } from './SharedParametersPanel';
import { PropertySection } from './InvestmentForm/PropertySection';
import { RevenueSection } from './InvestmentForm/RevenueSection';
import { ExpensesSection } from './InvestmentForm/ExpensesSection';
import { RegimeComparisonGrid } from './RegimeComparisonGrid';
import type { SharedPropertyData } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';

export interface PropertyRentalSimulatorProps {
  sharedData?: SharedPropertyData;
  onNavigateBack?: () => void;
}

export const PropertyRentalSimulator: React.FC<PropertyRentalSimulatorProps> = ({
  sharedData,
  onNavigateBack,
}) => {
  const { t } = useTranslation('realEstate');
  const [simulationName, setSimulationName] = React.useState('');
  const [propertyOpen, setPropertyOpen] = React.useState(true);
  const [revenueOpen, setRevenueOpen] = React.useState(true);
  const [expensesOpen, setExpensesOpen] = React.useState(true);
  const [resultsCollapseCount, setResultsCollapseCount] = React.useState(0);
  const { baseCurrency } = useAuthContext();
  const { format: formatCurrency } = useFormatCurrency();

  const {
    inputs,
    results,
    isCalculating,
    errors,
    recommendedRegime,
    eligibleRegimes,
    updatePropertyInput,
    updateRevenueInput,
    updateExpenseInput,
    calculate,
    reset,
    setInputs,
    getRegimeResult,
    isRegimeEligible,
  } = useRentalSimulator(sharedData);

  const {
    simulations,
    saveSimulation,
    loadSimulation,
    deleteSimulation,
    hasSimulationWithName,
  } = useSimulationStorage();

  const hasErrors = errors.length > 0;
  const generalErrors = errors.filter(e => e.field === 'general');

  const handleSaveSimulation = async () => {
    if (!simulationName.trim()) return;
    const success = await saveSimulation(simulationName, 'rental_investment', inputs);
    if (success) setSimulationName('');
  };

  const handleLoadSimulation = (id: string) => {
    const simulation = loadSimulation(id);
    if (simulation) setInputs(simulation.data as typeof inputs);
  };

  const handleCalculate = () => {
    calculate();
    setPropertyOpen(false);
    setRevenueOpen(false);
    setExpensesOpen(false);
    setResultsCollapseCount(c => c + 1);
  };

  const toggleProperty = () => {
    setPropertyOpen(p => {
      const n = !p;
      if (window.innerWidth >= ACCORDION_SYNC_BREAKPOINT) {
        setRevenueOpen(n);
        setExpensesOpen(n);
      }
      return n;
    });
  };

  const toggleRevenue = () => {
    setRevenueOpen(p => {
      const n = !p;
      if (window.innerWidth >= ACCORDION_SYNC_BREAKPOINT) {
        setPropertyOpen(n);
        setExpensesOpen(n);
      }
      return n;
    });
  };

  const toggleExpenses = () => {
    setExpensesOpen(p => {
      const n = !p;
      if (window.innerWidth >= ACCORDION_SYNC_BREAKPOINT) {
        setPropertyOpen(n);
        setRevenueOpen(n);
      }
      return n;
    });
  };


  return (
    <div className="container mx-auto px-4 py-8 max-w-7xl">
      <PageHeader
        title={t('rentalSimulator.title')}
        description={t('rentalSimulator.description')}
      />

      {/* Back Button */}
      {onNavigateBack && (
        <Button
          variant="outline"
          onClick={onNavigateBack}
          className="mb-6"
        >
          <ArrowLeft className="mr-2 h-4 w-4" />
          {t('rentalSimulator.backToComparator')}
        </Button>
      )}

      {/* Shared Data Panel */}
      {sharedData && (
        <SharedParametersPanel sharedData={sharedData} />
      )}

      {/* Simulation Header */}
      <SimulationHeader
        simulationName={simulationName}
        onNameChange={setSimulationName}
        onSave={handleSaveSimulation}
        onLoad={handleLoadSimulation}
        onDelete={deleteSimulation}
        simulations={simulations}
        canSave={!hasSimulationWithName(simulationName) && simulationName.trim().length > 0}
      />

      {/* Error Alerts */}
      {generalErrors.length > 0 && (
        <Alert variant="error" className="mb-6">
          <AlertDescription>
            {generalErrors.map(e => e.message).join(', ')}
          </AlertDescription>
        </Alert>
      )}

      {/* Summary Card */}
      <Card className="p-4 bg-muted/50 mb-6">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
          <div>
            <p className="text-muted-foreground">{t('rentalSimulator.totalPrice')}</p>
            <p className="font-semibold">{formatCurrency(inputs.property.totalPrice, baseCurrency)}</p>
          </div>
          <div>
            <p className="text-muted-foreground">{t('rentalSimulator.monthlyRent')}</p>
            <p className="font-semibold">{formatCurrency(inputs.revenue.monthlyRent, baseCurrency)}</p>
          </div>
          <div>
            <p className="text-muted-foreground">{t('rentalSimulator.creditPayment')}</p>
            <p className="font-semibold">{formatCurrency(inputs.credit.monthlyPayment, baseCurrency)}/{t('comparison.perMonth')}</p>
          </div>
          <div>
            <p className="text-muted-foreground">{t('rentalSimulator.eligibleRegimes')}</p>
            <p className="font-semibold">{eligibleRegimes.length}/4</p>
          </div>
        </div>
      </Card>

      {/* Input Sections Grid — individually collapsible, synced on desktop */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        <PropertySection
          inputs={inputs.property}
          errors={errors.filter(e => e.field.startsWith('property.'))}
          onUpdate={updatePropertyInput}
          isOpen={propertyOpen}
          onToggle={toggleProperty}
        />

        <RevenueSection
          inputs={inputs.revenue}
          errors={errors.filter(e => e.field.startsWith('revenue.'))}
          onUpdate={updateRevenueInput}
          isOpen={revenueOpen}
          onToggle={toggleRevenue}
        />

        <ExpensesSection
          inputs={inputs.expenses}
          errors={errors.filter(e => e.field.startsWith('expenses.'))}
          onUpdate={updateExpenseInput}
          isOpen={expensesOpen}
          onToggle={toggleExpenses}
        />
      </div>

      {/* Action Buttons */}
      <div className="flex flex-col sm:flex-row gap-4 justify-center mb-8">
        <Button
          size="lg"
          onClick={handleCalculate}
          disabled={isCalculating || hasErrors}
          className="min-w-[200px]"
        >
          {isCalculating ? (
            <>
              <div className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
              {t('rentalSimulator.calculating')}
            </>
          ) : (
            <>
              <Calculator className="mr-2 h-4 w-4" />
              {t('rentalSimulator.calculateAllRegimes')}
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
          {t('rentalSimulator.reset')}
        </Button>
      </div>

      {/* Results — shown inline below inputs */}
      {results && (
        <RegimeComparisonGrid
          results={results}
          recommendedRegime={recommendedRegime}
          eligibleRegimes={eligibleRegimes}
          getRegimeResult={getRegimeResult}
          isRegimeEligible={isRegimeEligible}
          forceCollapse={resultsCollapseCount}
        />
      )}
    </div>
  );
};

export default PropertyRentalSimulator;
