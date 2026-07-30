/**
 * ResultsPanel Component
 * 
 * Main results display container for Buy/Rent comparison
 * Requirements: REQ-1.6.x
 */

import React, { useState } from 'react';
import { Download, Table, BarChart3, PieChart, FileText, FileJson, Printer } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
import { cn } from '@/lib/utils';
import { useTranslation } from 'react-i18next';
import { SummaryCards } from './SummaryCards';
import { EvolutionChart } from './EvolutionChart';
import { YearlyTable } from './YearlyTable';
import { ComparisonAnalysis } from './ComparisonAnalysis';
import { YearNAnalysisCard } from './YearNAnalysisCard';
import { CalculationErrorBoundary } from '../CalculationErrorBoundary';
import { exportBuyRentToCSV, exportToJSON, generatePDFContent, downloadFile, printToPDF } from '../exportImport';
import type { BuyRentResults, BuyRentInputs, YearNAnalysis } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';

export interface ResultsPanelProps {
  results: BuyRentResults;
  inputs: BuyRentInputs;
  getYearNAnalysis: (year: number) => YearNAnalysis | null;
  isValidResaleYear: boolean;
}

export const ResultsPanel: React.FC<ResultsPanelProps> = ({
  results,
  inputs,
  getYearNAnalysis,
  isValidResaleYear,
}) => {
  const [activeTab, setActiveTab] = useState('summary');
  const { t } = useTranslation('realEstate');
  const { baseCurrency } = useAuthContext();

  const handleExportCSV = () => {
    const csv = exportBuyRentToCSV(inputs, results);
    downloadFile(
      csv,
      `${t('exportImport.exportCSVFilename')}-${new Date().toISOString().split('T')[0]}.csv`,
      'text/csv;charset=utf-8;'
    );
  };

  const handleExportJSON = () => {
    const simulation = {
      metadata: {
        id: crypto.randomUUID(),
        name: 'Export simulation',
        type: 'buy_rent' as const,
        createdAt: new Date(),
        updatedAt: new Date(),
      },
      data: inputs,
    };
    const json = exportToJSON(simulation, results);
    downloadFile(
      json,
      `${t('exportImport.exportJSONFilename')}-${new Date().toISOString().split('T')[0]}.json`,
      'application/json'
    );
  };

  const handleExportPDF = () => {
    const pdfTitle = t('comparator.title');
    const html = generatePDFContent(
      pdfTitle,
      inputs,
      results,
      'buy_rent',
      baseCurrency
    );
    printToPDF(html, pdfTitle);
  };

  const yearNAnalysis = isValidResaleYear ? getYearNAnalysis(inputs.resale.targetYear) : null;

  return (
    <div className="space-y-6">
      {/* Export Button */}
      <div className="flex justify-end">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline">
              <Download className="mr-2 h-4 w-4" />
              {t('results.export')}
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={handleExportCSV}>
              <Table className="mr-2 h-4 w-4" />
              {t('results.exportCSV')}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={handleExportJSON}>
              <FileJson className="mr-2 h-4 w-4" />
              {t('results.exportJSON')}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={handleExportPDF}>
              <Printer className="mr-2 h-4 w-4" />
              {t('results.printPDF')}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>


      {/* Summary Cards */}
      <SummaryCards results={results} />

      {/* Detailed Results Tabs */}
      <div className="space-y-4">
        {/* Tab Bar */}
        <div className="border-b border-border">
          <div className="flex flex-wrap gap-1 text-sm">
            <button
              onClick={() => setActiveTab('summary')}
              className={cn(
                'flex items-center gap-1.5 px-4 py-2 font-medium border-b-2 transition-colors',
                activeTab === 'summary'
                  ? 'border-primary text-primary'
                  : 'border-transparent text-text-secondary hover:text-text-primary'
              )}
            >
              <BarChart3 className="h-4 w-4" />
              {t('results.overview')}
            </button>
            <button
              onClick={() => setActiveTab('table')}
              className={cn(
                'flex items-center gap-1.5 px-4 py-2 font-medium border-b-2 transition-colors',
                activeTab === 'table'
                  ? 'border-primary text-primary'
                  : 'border-transparent text-text-secondary hover:text-text-primary'
              )}
            >
              <Table className="h-4 w-4" />
              {t('results.table')}
            </button>
            <button
              onClick={() => setActiveTab('chart')}
              className={cn(
                'flex items-center gap-1.5 px-4 py-2 font-medium border-b-2 transition-colors',
                activeTab === 'chart'
                  ? 'border-primary text-primary'
                  : 'border-transparent text-text-secondary hover:text-text-primary'
              )}
            >
              <PieChart className="h-4 w-4" />
              {t('results.chart')}
            </button>
            {yearNAnalysis && (
              <button
                onClick={() => setActiveTab('yearN')}
                className={cn(
                  'flex items-center gap-1.5 px-4 py-2 font-medium border-b-2 transition-colors',
                  activeTab === 'yearN'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-text-secondary hover:text-text-primary'
                )}
              >
                <FileText className="h-4 w-4" />
                {t('results.yearN', { year: inputs.resale.targetYear })}
              </button>
            )}
          </div>
        </div>

        {/* Tab Content */}
        {activeTab === 'summary' && (
          <CalculationErrorBoundary onReset={() => window.location.reload()}>
            <ComparisonAnalysis results={results} />
          </CalculationErrorBoundary>
        )}
        {activeTab === 'table' && (
          <CalculationErrorBoundary onReset={() => window.location.reload()}>
            <YearlyTable results={results} />
          </CalculationErrorBoundary>
        )}
        {activeTab === 'chart' && (
          <CalculationErrorBoundary onReset={() => window.location.reload()}>
            <EvolutionChart results={results} />
          </CalculationErrorBoundary>
        )}
        {activeTab === 'yearN' && yearNAnalysis && (
          <CalculationErrorBoundary onReset={() => window.location.reload()}>
            <YearNAnalysisCard analysis={yearNAnalysis} targetYear={inputs.resale.targetYear} />
          </CalculationErrorBoundary>
        )}
      </div>
    </div>
  );
};

export default ResultsPanel;
