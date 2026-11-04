import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import { BuyRentComparator } from '@/components/real-estate-tools/BuyRentComparator';
import { PropertyRentalSimulator } from '@/components/real-estate-tools/PropertyRentalSimulator';
import {
  DEFAULT_BUY_RENT_INPUTS,
  type SavedSimulation,
  type SharedPropertyData,
} from '@/types/realEstateTools';
import { createInvestmentInputs } from '@/test/simulation-fixtures';

const conversion = vi.hoisted(() => ({ mutateAsync: vi.fn(), isPending: false }));
vi.mock('@/hooks/useCurrency', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useCurrency')>()),
  useConvertCurrency: () => conversion,
}));

const storage = vi.hoisted(() => ({
  simulations: [] as SavedSimulation[],
  saveSimulation: vi.fn(),
  loadSimulation: vi.fn(),
  deleteSimulation: vi.fn(),
  hasSimulationWithName: vi.fn(),
  error: null as string | null,
  isSaving: false,
}));
vi.mock('@/hooks/useSimulationStorage', () => ({ useSimulationStorage: () => storage }));
vi.mock('@/hooks/useRealEstate', () => ({ useProperties: () => ({ data: [], isLoading: false }) }));
const tr = (key: string) => i18n.t(key, { ns: 'realEstate' });
const shared: SharedPropertyData = {
  currency: 'EUR',
  totalPrice: 250000,
  credit: {
    monthlyPayment: 1000,
    annualCost: 12000,
    totalCost: 240000,
    assurance: 500,
    bankFees: 100,
  },
  propertyTax: 2000,
  coOwnershipCharges: 1200,
};

beforeEach(async () => {
  vi.clearAllMocks();
  mockAuthentication();
  await i18n.changeLanguage('en');
  Element.prototype.scrollIntoView = vi.fn();
  Element.prototype.hasPointerCapture = vi.fn(() => false);
  Element.prototype.setPointerCapture = vi.fn();
  Element.prototype.releasePointerCapture = vi.fn();
  storage.simulations = [];
  storage.error = null;
  storage.saveSimulation.mockResolvedValue(true);
  storage.deleteSimulation.mockResolvedValue(true);
  storage.hasSimulationWithName.mockReturnValue(false);
  conversion.mutateAsync.mockReset().mockResolvedValue({ exchangeRate: 1.2 });
});

afterEach(() => vi.unstubAllGlobals());

describe('buy or rent workflow with real calculation and form components', () => {
  it.each([375, 1280])(
    'preserves assumptions while collapsing panels at a %spx viewport',
    async width => {
      vi.stubGlobal('innerWidth', width);
      const user = userEvent.setup();
      renderWithProviders(<BuyRentComparator />);
      fireEvent.change(screen.getByLabelText(tr('purchaseSection.propertyPrice')), {
        target: { value: '281000' },
      });
      fireEvent.blur(screen.getByLabelText(tr('purchaseSection.propertyPrice')));
      const heading = (section: string) =>
        screen.getByRole('heading', { name: tr(`${section}Section.title`) });
      const content = (section: string) => heading(section).parentElement!.nextElementSibling!;
      for (const [first, second] of [
        ['purchase', 'rental'],
        ['market', 'resale'],
      ]) {
        await user.click(heading(first));
        expect(content(first)).toHaveClass('max-h-0');
        if (width >= 1024) expect(content(second)).toHaveClass('max-h-0');
        else expect(content(second)).not.toHaveClass('max-h-0');
        await user.click(heading(second));
        if (width >= 1024) {
          expect(content(first)).not.toHaveClass('max-h-0');
          expect(content(second)).not.toHaveClass('max-h-0');
        } else {
          expect(content(first)).toHaveClass('max-h-0');
          expect(content(second)).toHaveClass('max-h-0');
        }
      }
      expect(screen.getByLabelText(tr('purchaseSection.propertyPrice'))).toHaveValue('281,000');
    }
  );
  it('preserves financing, fees and rental assumptions when saving an edited calculation', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BuyRentComparator />);
    const change = (id: string, value: number) => {
      const input = document.getElementById(id)!;
      expect(input).toBeInTheDocument();
      fireEvent.change(input, { target: { value: String(value) } });
      fireEvent.blur(input);
    };
    change('renovationAmount', 9000);
    change('notaryFeesPercent', 7.5);
    change('agencyFees', 2500);
    await user.click(screen.getByRole('button', { name: tr('purchaseSection.financing') }));
    change('downPayment', 50000);
    change('loanDuration', 15);
    change('interestRate', 2.5);
    await user.click(screen.getByRole('button', { name: tr('purchaseSection.insuranceAndFees') }));
    change('totalInsurance', 6000);
    change('applicationFees', 800);
    change('guaranteeFees', 1200);
    change('accountFees', 75);
    await user.click(screen.getByRole('button', { name: tr('purchaseSection.recurringCharges') }));
    change('propertyTax', 1800);
    change('coOwnershipCharges', 2100);
    change('maintenancePercent', 0.5);
    change('homeInsurance', 250);
    change('bankFees', 60);
    change('garbageTax', 130);
    change('monthlyRent', 1100);
    change('monthlyCharges', 120);
    change('securityDeposit', 2200);
    change('rentalInsurance', 200);
    change('rentalGarbageTax', 90);
    change('priceEvolution', 1.5);
    change('rentEvolution', 1);
    change('investmentReturn', 4);
    change('inflation', 2);
    change('targetYear', 10);
    change('desiredProfit', 20000);
    change('resaleFeesPercent', 3);
    await user.type(
      screen.getByPlaceholderText(tr('comparator.simulationName')),
      'Detailed assumptions'
    );
    await user.click(screen.getByRole('button', { name: tr('comparator.save') }));
    await waitFor(() =>
      expect(storage.saveSimulation).toHaveBeenCalledWith(
        'Detailed assumptions',
        'buy_rent',
        expect.objectContaining({
          purchase: expect.objectContaining({
            renovationAmount: 9000,
            notaryFeesPercent: 7.5,
            agencyFees: 2500,
            downPayment: 50000,
            loanDuration: 15,
            interestRate: 2.5,
            totalInsurance: 6000,
            applicationFees: 800,
            guaranteeFees: 1200,
            accountFees: 75,
            propertyTax: 1800,
            coOwnershipCharges: 2100,
            maintenancePercent: 0.5,
            homeInsurance: 250,
            bankFees: 60,
            garbageTax: 130,
          }),
          rental: expect.objectContaining({
            monthlyRent: 1100,
            monthlyCharges: 120,
            securityDeposit: 2200,
            rentalInsurance: 200,
            garbageTax: 90,
            initialSavings: 47800,
          }),
          market: { priceEvolution: 1.5, rentEvolution: 1, investmentReturn: 4, inflation: 2 },
          resale: { targetYear: 10, desiredProfit: 20000, resaleFeesPercent: 3 },
        })
      )
    );
  });

  it('converts saved foreign-currency simulations and keeps the current inputs on conversion failure', async () => {
    const user = userEvent.setup();
    const saved: SavedSimulation = {
      metadata: {
        id: 'foreign',
        name: 'Dollar home',
        type: 'buy_rent',
        createdAt: new Date(),
        updatedAt: new Date(),
      },
      data: { ...structuredClone(DEFAULT_BUY_RENT_INPUTS), currency: 'USD' },
    };
    storage.simulations = [
      saved,
      { ...saved, metadata: { ...saved.metadata, id: 'second', name: 'Other home' } },
    ];
    storage.loadSimulation.mockReturnValue(saved);
    renderWithProviders(<BuyRentComparator />);
    await user.click(screen.getAllByRole('combobox')[0]);
    await user.click(screen.getByRole('option', { name: /Dollar home/ }));
    await waitFor(() =>
      expect(screen.getByLabelText(tr('purchaseSection.propertyPrice'))).toHaveValue('360,000')
    );
    expect(conversion.mutateAsync).toHaveBeenCalledWith({
      amount: 1,
      fromCurrency: 'USD',
      toCurrency: 'EUR',
    });
    conversion.mutateAsync.mockRejectedValue(new Error('No rate'));
    await user.click(screen.getAllByRole('combobox')[0]);
    await user.click(screen.getByRole('option', { name: /Other home/ }));
    expect(await screen.findByText(tr('comparator.conversionFailed'))).toBeInTheDocument();
    expect(screen.getByLabelText(tr('purchaseSection.propertyPrice'))).toHaveValue('360,000');
  });
  it('calculates, switches result views, saves the edited inputs, and transfers credit data', async () => {
    const user = userEvent.setup();
    const transfer = vi.fn();
    renderWithProviders(<BuyRentComparator onNavigateToRentalSimulator={transfer} />);
    fireEvent.change(screen.getByLabelText(tr('purchaseSection.propertyPrice')), {
      target: { value: '320000' },
    });
    fireEvent.blur(screen.getByLabelText(tr('purchaseSection.propertyPrice')));
    await user.click(screen.getByRole('button', { name: tr('comparator.calculate') }));
    await screen.findByRole('button', { name: tr('results.table') });
    await user.click(screen.getByRole('button', { name: tr('results.table') }));
    expect(screen.getAllByRole('row').length).toBeGreaterThan(10);
    await user.click(screen.getByRole('button', { name: tr('results.chart') }));
    await user.click(screen.getByRole('button', { name: tr('results.overview') }));
    await user.type(screen.getByPlaceholderText(tr('comparator.simulationName')), 'Paris purchase');
    await user.click(screen.getByRole('button', { name: tr('comparator.save') }));
    await waitFor(() =>
      expect(storage.saveSimulation).toHaveBeenCalledWith(
        'Paris purchase',
        'buy_rent',
        expect.objectContaining({
          purchase: expect.objectContaining({ propertyPrice: 320000 }),
          currency: expect.any(String),
        })
      )
    );
    expect(screen.getByPlaceholderText(tr('comparator.simulationName'))).toHaveValue('');
    await user.click(screen.getByRole('button', { name: tr('comparator.simulateRental') }));
    await waitFor(() =>
      expect(transfer).toHaveBeenCalledWith(
        expect.objectContaining({
          currency: 'EUR',
          totalPrice: expect.any(Number),
          credit: expect.objectContaining({ monthlyPayment: expect.any(Number) }),
        })
      )
    );
    await user.click(screen.getByRole('button', { name: tr('comparator.reset') }));
    expect(screen.getByLabelText(tr('purchaseSection.propertyPrice'))).toHaveValue('300,000');
  });

  it('loads a saved simulation of the correct type and requires confirmation to delete it', async () => {
    const user = userEvent.setup();
    const saved: SavedSimulation = {
      metadata: {
        id: 'saved',
        name: 'Saved home',
        type: 'buy_rent',
        createdAt: new Date(),
        updatedAt: new Date(),
      },
      data: {
        ...structuredClone(DEFAULT_BUY_RENT_INPUTS),
        currency: 'EUR',
        purchase: { ...DEFAULT_BUY_RENT_INPUTS.purchase, propertyPrice: 410000 },
      },
    };
    storage.simulations = [saved];
    storage.loadSimulation.mockReturnValue(saved);
    renderWithProviders(<BuyRentComparator />);
    await user.click(screen.getAllByRole('combobox')[0]);
    await user.click(screen.getByRole('option', { name: /Saved home/ }));
    await waitFor(() =>
      expect(screen.getByLabelText(tr('purchaseSection.propertyPrice'))).toHaveValue('410,000')
    );
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    await user.click(screen.getByText(tr('comparator.delete')));
    await user.click(screen.getByRole('option', { name: 'Saved home' }));
    expect(storage.deleteSimulation).not.toHaveBeenCalled();
    confirm.mockReturnValue(true);
    await user.click(screen.getByText(tr('comparator.delete')));
    await user.click(screen.getByRole('option', { name: 'Saved home' }));
    await waitFor(() => expect(storage.deleteSimulation).toHaveBeenCalledWith('saved'));
    confirm.mockRestore();
  });
});

describe('rental investment workflow', () => {
  it('persists edited revenue, vacancy, tax, and expense assumptions independently', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PropertyRentalSimulator sharedData={shared} />);
    const values = {
      totalPrice: 280000,
      monthlyRent: 1300,
      recoverableCharges: 100,
      occupancyRate: 90,
      badDebtRate: 2,
      propertyTax: 1900,
      cfe: 450,
      cvae: 0,
      nonRecoverableCharges: 800,
      annualMaintenance: 1200,
      managementFees: 600,
      pnoInsurance: 200,
      accountingFees: 350,
      marginalTaxRate: 30,
    };
    for (const [id, value] of Object.entries(values)) {
      const input = document.getElementById(id)!;
      fireEvent.change(input, { target: { value: String(value) } });
      fireEvent.blur(input);
    }
    fireEvent.change(screen.getByLabelText(tr('taxContext.otherHouseholdIncome')), {
      target: { value: '60000' },
    });
    for (const section of [
      'propertySection.title',
      'revenueSection.title',
      'expensesSection.title',
    ]) {
      await user.click(screen.getByText(tr(section), { exact: true }));
      await user.click(screen.getByText(tr(section), { exact: true }));
    }
    await user.type(
      screen.getByPlaceholderText(tr('comparator.simulationName')),
      'Revised rental assumptions'
    );
    await user.click(screen.getByRole('button', { name: tr('comparator.save') }));
    await waitFor(() =>
      expect(storage.saveSimulation).toHaveBeenCalledWith(
        'Revised rental assumptions',
        'rental_investment',
        expect.objectContaining({
          property: expect.objectContaining({ totalPrice: 280000 }),
          revenue: {
            monthlyRent: 1300,
            recoverableCharges: 100,
            occupancyRate: 90,
            badDebtRate: 2,
          },
          expenses: {
            propertyTax: 1900,
            cfe: 450,
            cvae: 0,
            nonRecoverableCharges: 800,
            annualMaintenance: 1200,
            managementFees: 600,
            pnoInsurance: 200,
            accountingFees: 350,
            marginalTaxRate: 30,
          },
          tax: expect.objectContaining({ otherHouseholdIncome: 60000 }),
          credit: shared.credit,
        })
      )
    );
  });
  it('loads a saved rental plan and rejects an invalid saved payload without losing the valid inputs', async () => {
    const user = userEvent.setup();
    const inputs = createInvestmentInputs();
    inputs.property.totalPrice = 345000;
    const saved: SavedSimulation = {
      metadata: {
        id: 'rental',
        name: 'Rental home',
        type: 'rental_investment',
        createdAt: new Date(),
        updatedAt: new Date(),
      },
      data: inputs,
    };
    storage.simulations = [
      saved,
      { ...saved, metadata: { ...saved.metadata, id: 'invalid', name: 'Broken plan' } },
    ];
    storage.loadSimulation.mockImplementation((id: string) =>
      id === 'rental' ? saved : undefined
    );
    renderWithProviders(<PropertyRentalSimulator />);
    await user.click(screen.getAllByRole('combobox')[0]);
    await user.click(screen.getByRole('option', { name: /Rental home/ }));
    await waitFor(() =>
      expect(screen.getByLabelText(tr('propertySection.totalPrice'))).toHaveValue('345,000')
    );
    await user.click(screen.getAllByRole('combobox')[0]);
    await user.click(screen.getByRole('option', { name: /Broken plan/ }));
    expect(await screen.findByText(tr('validation.invalidSimulation'))).toBeInTheDocument();
    expect(screen.getByLabelText(tr('propertySection.totalPrice'))).toHaveValue('345,000');
  });
  it('uses shared costs, accepts household context, computes regimes, saves and navigates back', async () => {
    const user = userEvent.setup();
    const back = vi.fn();
    renderWithProviders(<PropertyRentalSimulator sharedData={shared} onNavigateBack={back} />);
    expect(screen.getByLabelText(tr('propertySection.totalPrice'))).toHaveValue('250,000');
    fireEvent.change(screen.getByLabelText(tr('taxContext.otherHouseholdIncome')), {
      target: { value: '50000' },
    });
    fireEvent.change(screen.getByLabelText(tr('taxContext.incomeYear')), {
      target: { value: '2025' },
    });
    await user.click(
      screen.getByRole('button', { name: tr('rentalSimulator.calculateAllRegimes') })
    );
    await screen.findAllByText('Micro-Foncier');
    expect(screen.getAllByText(/Micro-BIC/).length).toBeGreaterThan(0);
    await user.type(screen.getByPlaceholderText(tr('comparator.simulationName')), 'Rental plan');
    await user.click(screen.getByRole('button', { name: tr('comparator.save') }));
    await waitFor(() =>
      expect(storage.saveSimulation).toHaveBeenCalledWith(
        'Rental plan',
        'rental_investment',
        expect.objectContaining({
          property: expect.objectContaining({ totalPrice: 250000 }),
          tax: expect.objectContaining({ incomeYear: 2025, otherHouseholdIncome: 50000 }),
        })
      )
    );
    await user.click(screen.getByRole('button', { name: tr('rentalSimulator.backToComparator') }));
    expect(back).toHaveBeenCalledOnce();
  });

  it('blocks saving while the name is duplicate and shows storage failures', () => {
    storage.error = 'Storage unavailable';
    storage.hasSimulationWithName.mockReturnValue(true);
    renderWithProviders(<PropertyRentalSimulator sharedData={shared} />);
    fireEvent.change(screen.getByPlaceholderText(tr('comparator.simulationName')), {
      target: { value: 'Duplicate' },
    });
    expect(screen.getByRole('button', { name: tr('comparator.save') })).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent('Storage unavailable');
  });
});
