import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { BuyRentComparator } from '@/components/real-estate-tools/BuyRentComparator';
import { DEFAULT_BUY_RENT_INPUTS, type BuyRentInputs } from '@/types/realEstateTools';
import i18n from '@/test/i18n-test';

const state = vi.hoisted(() => ({
  currency: 'EUR',
  inputs: {} as BuyRentInputs,
  errors: [] as { field: string; message: string }[],
  saving: false,
  calculating: false,
  conversionPending: false,
  duplicate: false,
  storageError: null as string | null,
  save: vi.fn(),
  load: vi.fn(),
  convert: vi.fn(),
  setInputs: vi.fn(),
  calculate: vi.fn(),
  reset: vi.fn(),
}));
vi.mock('@/context/AuthContext', async importOriginal => {
  const actual = await importOriginal<typeof import('@/context/AuthContext')>();
  return {
    ...actual,
    useAuthContext: () => ({ ...actual.useAuthContext(), baseCurrency: state.currency }),
  };
});
vi.mock('@/hooks/useCurrency', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useCurrency')>()),
  useConvertCurrency: () => ({ mutateAsync: state.convert, isPending: state.conversionPending }),
}));
vi.mock('@/hooks/useBuyRentCalculations', () => ({
  useBuyRentCalculations: () => ({
    inputs: state.inputs,
    results: null,
    isCalculating: state.calculating,
    errors: state.errors,
    derivedValues: {
      totalPrice: 200000,
      borrowedAmount: 150000,
      monthlyPayment: 1000,
      totalCreditCost: 30000,
    },
    setInputs: state.setInputs,
    calculate: state.calculate,
    reset: state.reset,
    updatePurchaseInput: vi.fn(),
    updateRentalInput: vi.fn(),
    updateMarketInput: vi.fn(),
    updateResaleInput: vi.fn(),
    getYearNAnalysis: vi.fn(),
    isValidResaleYear: true,
  }),
}));
vi.mock('@/hooks/useSimulationStorage', () => ({
  useSimulationStorage: () => ({
    simulations: [],
    saveSimulation: state.save,
    loadSimulation: state.load,
    deleteSimulation: vi.fn(),
    hasSimulationWithName: () => state.duplicate,
    error: state.storageError,
    isSaving: state.saving,
  }),
}));
vi.mock('@/components/real-estate-tools/SimulationHeader', () => ({
  SimulationHeader: ({
    simulationName,
    onNameChange,
    onSave,
    onLoad,
    canSave,
  }: {
    simulationName: string;
    onNameChange: (name: string) => void;
    onSave: () => void;
    onLoad: (id: string) => void;
    canSave: boolean;
  }) => (
    <div>
      <input
        aria-label="Simulation name"
        value={simulationName}
        onChange={event => onNameChange(event.target.value)}
      />
      <button disabled={!canSave} onClick={onSave}>
        Save simulation
      </button>
      <button onClick={() => onLoad('saved')}>Load simulation</button>
    </div>
  ),
}));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({
    value,
    onValueChange,
  }: {
    value?: string;
    onValueChange: (currency: string) => void;
  }) => (
    <select
      aria-label="Original currency"
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value)}
    >
      <option value="">Choose</option>
      <option value="EUR">EUR</option>
      <option value="USD">USD</option>
    </select>
  ),
}));
vi.mock('@/components/real-estate-tools/PropertySelector', () => ({
  PropertySelector: () => null,
}));
vi.mock('@/components/real-estate-tools/BuyRentForm/PurchaseSection', () => ({
  PurchaseSection: () => null,
}));
vi.mock('@/components/real-estate-tools/BuyRentForm/RentalSection', () => ({
  RentalSection: () => null,
}));
vi.mock('@/components/real-estate-tools/BuyRentForm/MarketSection', () => ({
  MarketSection: () => null,
}));
vi.mock('@/components/real-estate-tools/BuyRentForm/ResaleSection', () => ({
  ResaleSection: () => null,
}));
const tr = (key: string) => i18n.t(key, { ns: 'realEstate' });

beforeEach(() => {
  vi.clearAllMocks();
  mockAuthentication();
  state.currency = 'EUR';
  state.inputs = structuredClone(DEFAULT_BUY_RENT_INPUTS);
  state.errors = [];
  state.saving = false;
  state.calculating = false;
  state.conversionPending = false;
  state.duplicate = false;
  state.storageError = null;
  state.save.mockResolvedValue(true);
  state.convert.mockResolvedValue({ exchangeRate: 0.8 });
});

function saved(data: unknown = { ...state.inputs, currency: 'USD' }, type = 'buy_rent') {
  return { metadata: { id: 'saved', type }, data };
}

describe('buy/rent persistence and currency boundaries', () => {
  it.each(['missing', 'wrong-type', 'invalid-data'])(
    'rejects %s saved simulations without replacing the current assumptions',
    invalid => {
      state.load.mockReturnValue(
        invalid === 'missing'
          ? undefined
          : invalid === 'wrong-type'
            ? saved(state.inputs, 'rental_investment')
            : saved({ currency: 'EUR' })
      );
      renderWithProviders(<BuyRentComparator />);
      fireEvent.click(screen.getByRole('button', { name: 'Load simulation' }));
      expect(screen.getByRole('alert')).toHaveTextContent(tr('validation.invalidSimulation'));
      expect(state.setInputs).not.toHaveBeenCalled();
      expect(state.convert).not.toHaveBeenCalled();
    }
  );

  it.each(['EUR', 'USD'])(
    'requires a currency for legacy plans and loads them from %s',
    currency => {
      const legacy = structuredClone(state.inputs);
      delete legacy.currency;
      state.load.mockReturnValue(saved(legacy));
      renderWithProviders(<BuyRentComparator />);
      fireEvent.click(screen.getByRole('button', { name: 'Load simulation' }));
      const load = screen.getByRole('button', { name: tr('comparator.loadWithCurrency') });
      expect(load).toBeDisabled();
      fireEvent.change(screen.getByRole('combobox', { name: 'Original currency' }), {
        target: { value: currency },
      });
      fireEvent.click(load);
      return waitFor(() => {
        expect(state.setInputs).toHaveBeenCalledWith(
          expect.objectContaining({
            currency: 'EUR',
            purchase: expect.objectContaining({
              propertyPrice: currency === 'USD' ? 240000 : 300000,
            }),
          })
        );
        expect(
          screen.queryByRole('combobox', { name: 'Original currency' })
        ).not.toBeInTheDocument();
        if (currency === 'EUR') expect(state.convert).not.toHaveBeenCalled();
        else expect(screen.getByRole('status')).toBeInTheDocument();
      });
    }
  );

  it.each([0, -1, NaN, Infinity])(
    'rejects invalid conversion rate %s without changing loaded inputs or navigating',
    async rate => {
      state.currency = 'USD';
      state.load.mockReturnValue(saved({ ...state.inputs, currency: 'EUR' }));
      state.convert.mockResolvedValue({ exchangeRate: rate });
      const navigate = vi.fn();
      renderWithProviders(<BuyRentComparator onNavigateToRentalSimulator={navigate} />);
      fireEvent.click(screen.getByRole('button', { name: 'Load simulation' }));
      expect(await screen.findByText(tr('comparator.conversionFailed'))).toBeInTheDocument();
      expect(state.setInputs).not.toHaveBeenCalled();
      fireEvent.click(screen.getByRole('button', { name: tr('comparator.simulateRental') }));
      expect(await screen.findByText(tr('validation.currencyConversion'))).toBeInTheDocument();
      expect(navigate).not.toHaveBeenCalled();
    }
  );

  it('converts all shared monetary fields before navigating to the EUR rental simulator', async () => {
    state.currency = 'USD';
    state.inputs.purchase.loanDuration = 20;
    state.inputs.purchase.totalInsurance = 1000;
    state.inputs.purchase.applicationFees = 100;
    state.inputs.purchase.guaranteeFees = 200;
    state.inputs.purchase.accountFees = 50;
    state.inputs.purchase.propertyTax = 2000;
    state.inputs.purchase.coOwnershipCharges = 1000;
    const navigate = vi.fn();
    renderWithProviders(<BuyRentComparator onNavigateToRentalSimulator={navigate} />);
    fireEvent.click(screen.getByRole('button', { name: tr('comparator.simulateRental') }));
    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        currency: 'EUR',
        totalPrice: 160000,
        propertyTax: 1600,
        coOwnershipCharges: 800,
        credit: {
          monthlyPayment: 800,
          annualCost: 9600,
          totalCost: 0,
          assurance: 40,
          bankFees: 14,
        },
      })
    );
    expect(state.convert).toHaveBeenCalledWith({
      amount: 1,
      fromCurrency: 'USD',
      toCurrency: 'EUR',
    });
  });

  it('retains the name after a failed save and displays the storage error', async () => {
    state.save.mockResolvedValue(false);
    state.storageError = 'Storage is full';
    renderWithProviders(<BuyRentComparator />);
    fireEvent.change(screen.getByLabelText('Simulation name'), {
      target: { value: 'Keep this plan' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save simulation' }));
    await waitFor(() =>
      expect(state.save).toHaveBeenCalledWith(
        'Keep this plan',
        'buy_rent',
        expect.objectContaining({ currency: 'EUR' })
      )
    );
    expect(screen.getByLabelText('Simulation name')).toHaveValue('Keep this plan');
    expect(screen.getByRole('alert')).toHaveTextContent('Storage is full');
  });

  it('surfaces general calculation errors and disables saving and calculation', () => {
    state.errors = [
      { field: 'general', message: 'Calculation unavailable' },
      { field: 'purchase.propertyPrice', message: 'Required' },
    ];
    renderWithProviders(<BuyRentComparator />);
    expect(screen.getByRole('alert')).toHaveTextContent('Calculation unavailable');
    expect(screen.getByRole('button', { name: tr('comparator.calculate') })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Simulation name'), {
      target: { value: 'Invalid plan' },
    });
    expect(screen.getByRole('button', { name: 'Save simulation' })).toBeDisabled();
  });
});
