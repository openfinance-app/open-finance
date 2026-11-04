import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '@/test/i18n-test';
import { DEFAULT_BUY_RENT_INPUTS, type SavedSimulation } from '@/types/realEstateTools';
import { createInvestmentInputs } from '@/test/simulation-fixtures';
import { RealEstateCalculationService } from '@/services/realEstateCalculationService';
import {
  downloadFile,
  exportBuyRentToCSV,
  exportInvestmentToCSV,
  exportToJSON,
  generatePDFContent,
  importFromJSON,
  printToPDF,
} from '@/components/real-estate-tools/exportImport';
import { PRINT_PREVIEW_DELAY_MS } from '@/constants/timing';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
const inputs = { ...DEFAULT_BUY_RENT_INPUTS, currency: 'EUR' };
const results = RealEstateCalculationService.calculateBuyRentComparison(inputs);
const simulation: SavedSimulation = {
  metadata: {
    id: 'original',
    name: 'Home',
    type: 'buy_rent',
    createdAt: new Date('2026-01-01T00:00:00Z'),
    updatedAt: new Date('2026-01-02T00:00:00Z'),
  },
  data: inputs,
};

describe('simulation data portability', () => {
  it('exports and imports inputs without reusing an existing simulation identifier', () => {
    const exported = exportToJSON(simulation, results);
    const parsed = JSON.parse(exported);
    expect(parsed.version).toBe('1.0.0');
    expect(parsed.inputs.currency).toBe('EUR');
    expect(parsed.results.years).toHaveLength(results.years.length);
    const imported = importFromJSON(exported);
    expect(imported.success).toBe(true);
    expect(imported.data?.metadata.id).not.toBe('original');
    expect(imported.data?.metadata.createdAt).toEqual(simulation.metadata.createdAt);
    expect(imported.data?.data).toEqual(inputs);
    expect(JSON.parse(exportToJSON(simulation)).results).toBeNull();
  });
  it.each(['not json', '{}', 'null', '{"metadata":{}}'])('rejects incomplete imports: %s', json => {
    expect(importFromJSON(json)).toEqual({ success: false, error: expect.any(String) });
  });
  it('keeps currency and net worth values in the semicolon-separated buy/rent export', () => {
    const lines = exportBuyRentToCSV(inputs, results).split('\n');
    expect(lines).toHaveLength(results.years.length + 2);
    expect(lines[1].split(';')).toHaveLength(12);
    expect(lines[1].split(';')[9]).toBe(results.years[0].buy.netWorth!.toFixed(2));
    expect(lines.every(line => line.endsWith('EUR') || line === lines[0])).toBe(true);
    expect(lines.at(-1)?.split(';')[9]).toBe(results.summary.buy.netWorth.toFixed(2));
  });
  it('exports all four investment regimes and leaves unknown tax amounts blank', () => {
    const investment = createInvestmentInputs();
    investment.tax!.otherHouseholdIncome = null;
    investment.revenue.monthlyRent = 4000;
    const computed = RealEstateCalculationService.calculateInvestment(investment);
    const csv = exportInvestmentToCSV(investment, computed);
    expect(csv.split('\n')).toHaveLength(5);
    expect(csv).toContain('Micro-Foncier');
    expect(csv).toContain('LMNP Réel');
    expect(csv).not.toMatch(/NaN|undefined|null/);
    const html = generatePDFContent('Rental', investment, computed, 'rental_investment', 'EUR');
    expect(html).toContain('LMNP Réel');
    expect(html).not.toMatch(/NaN|undefined/);
  });
  it('renders a simulation title as text rather than executable markup in a print window', () => {
    const title = '<img src=x onerror="alert(1)"> & Home';
    const html = generatePDFContent(title, inputs, results, 'buy_rent', 'EUR');
    const document = new DOMParser().parseFromString(html, 'text/html');
    expect(document.querySelector('img')).toBeNull();
    expect(document.querySelector('h1')?.textContent).toBe(title);
    expect(document.title).toBe(title);
    expect(document.querySelectorAll('tbody tr')).toHaveLength(results.years.length);
  });
});

describe('browser export lifecycle', () => {
  it('downloads the requested file then releases its temporary URL and link', () => {
    const create = vi.fn(() => 'blob:export');
    const revoke = vi.fn();
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke }));
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement
    ) {
      expect(this.download).toBe('simulation.json');
      expect(this.href).toBe('blob:export');
      expect(this.isConnected).toBe(true);
    });
    downloadFile('{"value":42}', 'simulation.json', 'application/json');
    expect(create).toHaveBeenCalledWith(expect.any(Blob));
    expect(click).toHaveBeenCalledOnce();
    expect(revoke).toHaveBeenCalledWith('blob:export');
    expect(document.querySelector('a[download]')).toBeNull();
  });
  it('reports blocked popups and waits for document preparation before printing', () => {
    vi.useFakeTimers();
    const alert = vi.spyOn(window, 'alert').mockImplementation(() => {});
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    printToPDF('<p>Report</p>', 'Report');
    expect(alert).toHaveBeenCalledOnce();
    const print = vi.fn();
    const printDocument = { write: vi.fn(), close: vi.fn(), title: '' };
    open.mockReturnValue({ document: printDocument, print } as unknown as Window);
    printToPDF('<p>Report</p>', 'Report');
    expect(printDocument.write).toHaveBeenCalledWith('<p>Report</p>');
    expect(printDocument.title).toBe('Report');
    expect(print).not.toHaveBeenCalled();
    vi.advanceTimersByTime(PRINT_PREVIEW_DELAY_MS);
    expect(print).toHaveBeenCalledOnce();
  });
});
