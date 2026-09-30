import { screen, act, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { BuyPropertyWizard } from './BuyPropertyWizard';
import * as useLiabilitiesModule from '@/hooks/useLiabilities';
import * as useRealEstateModule from '@/hooks/useRealEstate';
import * as useAccountsModule from '@/hooks/useAccounts';
import type { Account } from '@/types/account';

beforeAll(() => {
  window.HTMLElement.prototype.scrollIntoView = vi.fn();
});

vi.mock('@/hooks/useLiabilities', async importOriginal => {
  const actual = await importOriginal<typeof useLiabilitiesModule>();
  return {
    ...actual,
    useLiabilities: vi.fn(),
  };
});

vi.mock('@/hooks/useRealEstate', async importOriginal => {
  const actual = await importOriginal<typeof useRealEstateModule>();
  return {
    ...actual,
    usePurchaseProperty: vi.fn(),
  };
});

vi.mock('@/hooks/useAccounts', async importOriginal => {
  const actual = await importOriginal<typeof useAccountsModule>();
  return {
    ...actual,
    useAccounts: vi.fn(),
  };
});

vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({
    value,
    onValueChange,
  }: {
    value?: string;
    onValueChange: (v: string) => void;
  }) => (
    <select
      data-testid="wizard-currency"
      value={value ?? ''}
      onChange={e => onValueChange(e.target.value)}
    >
      <option value="EUR">EUR</option>
      <option value="USD">USD</option>
    </select>
  ),
}));

vi.mock('@/components/ui/AccountSelector', () => ({
  AccountSelector: ({ onValueChange }: { onValueChange: (v: number | undefined) => void }) => (
    <button type="button" data-testid="wizard-account" onClick={() => onValueChange(1)}>
      Account
    </button>
  ),
}));

vi.mock('@/components/ui/LiabilitySelector', () => ({
  LiabilitySelector: ({ value, onValueChange }: any) => (
    <select
      data-testid="wizard-mortgage"
      value={value ?? ''}
      onChange={(e: any) => onValueChange(e.target.value ? Number(e.target.value) : undefined)}
    >
      <option value="">-- none --</option>
      <option value="9">Existing Mortgage</option>
    </select>
  ),
}));

const mockUseLiabilities = vi.mocked(useLiabilitiesModule.useLiabilities);
const mockUsePurchase = vi.mocked(useRealEstateModule.usePurchaseProperty);
const mockUseAccounts = vi.mocked(useAccountsModule.useAccounts);

const mockAccounts: Account[] = [
  {
    id: 1,
    name: 'Checking',
    type: 'CHECKING',
    currency: 'USD',
    balance: 50000,
    userId: 1,
    isActive: true,
    createdAt: '2024-01-01',
  },
];

function renderWizard(onClose = vi.fn()) {
  renderWithProviders(<BuyPropertyWizard accounts={mockAccounts} onClose={onClose} />);
  return { onClose };
}

/** Fills step 1 (property fields, USD) and advances. */
async function fillPropertyStep() {
  fireEvent.change(screen.getByLabelText(/property name/i), { target: { value: 'Villa' } });
  fireEvent.change(screen.getByLabelText(/address/i), { target: { value: '1 Sea Rd' } });
  fireEvent.change(screen.getByLabelText(/purchase price/i), { target: { value: '300000' } });
  fireEvent.change(screen.getByLabelText(/current value/i), { target: { value: '0' } });
  fireEvent.change(screen.getByTestId('wizard-currency'), { target: { value: 'USD' } });
  await act(async () => {
    screen.getByRole('button', { name: /next/i }).click();
  });
}

describe('BuyPropertyWizard', () => {
  const submit = vi.fn();
  beforeEach(() => {
    vi.clearAllMocks();
    submit.mockReset().mockResolvedValue({ propertyId: 55, mortgageId: 77 });
    mockAuthentication();
    mockUseLiabilities.mockReturnValue({
      data: [{ id: 9, name: 'Existing Mortgage', currency: 'USD' }],
    } as ReturnType<typeof useLiabilitiesModule.useLiabilities>);
    mockUseAccounts.mockReturnValue({ data: mockAccounts } as ReturnType<
      typeof useAccountsModule.useAccounts
    >);
    mockUsePurchase.mockReturnValue({ mutateAsync: submit } as unknown as ReturnType<
      typeof useRealEstateModule.usePurchaseProperty
    >);
  });

  async function fillFunding(source = 'new', route = 'direct', selectAccount = true) {
    fireEvent.change(screen.getByLabelText(/funding source/i), { target: { value: source } });
    if (source === 'new')
      fireEvent.change(screen.getByLabelText(/mortgage name/i), { target: { value: 'Home loan' } });
    if (source === 'existing')
      fireEvent.change(screen.getByTestId('wizard-mortgage'), { target: { value: '9' } });
    if (source !== 'none') {
      fireEvent.change(screen.getByLabelText(/loan amount|amount to disburse now/i), {
        target: { value: '240000' },
      });
      fireEvent.change(screen.getByLabelText(/disbursement route/i), { target: { value: route } });
    }
    fireEvent.change(screen.getByLabelText(/down payment amount/i), {
      target: { value: source === 'none' ? '300000' : '60000' },
    });
    if (selectAccount) fireEvent.click(screen.getByTestId('wizard-account'));
  }

  it.each([
    ['new', 'direct', 'DIRECT'],
    ['existing', 'account', 'ACCOUNT'],
    ['none', 'direct', 'DIRECT'],
  ])(
    'submits the complete %s purchase through one atomic operation',
    async (source, route, expectedRoute) => {
      const { onClose } = renderWizard();
      await fillPropertyStep();
      await fillFunding(source, route);
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      fireEvent.click(screen.getByRole('button', { name: /confirm/i }));
      await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
      const request = submit.mock.calls[0][0];
      expect(request).toMatchObject({
        operationId: expect.any(String),
        property: { name: 'Villa', currentValue: '0', purchasePrice: '300000' },
        loanAmount: source === 'none' ? '0' : '240000',
        downPayment: source === 'none' ? '300000' : '60000',
        route: expectedRoute,
        accountId: 1,
      });
      expect(request.newMortgage?.principal).toBe(source === 'new' ? '240000' : undefined);
      expect(request.existingMortgageId).toBe(source === 'existing' ? 9 : undefined);
    }
  );

  it('retries an unknown outcome with exactly the same operation and keeps its details locked', async () => {
    submit.mockRejectedValueOnce(new Error('Connection lost after commit'));
    const { onClose } = renderWizard();
    await fillPropertyStep();
    await fillFunding();
    fireEvent.click(screen.getByRole('button', { name: /next/i }));
    fireEvent.click(screen.getByRole('button', { name: /confirm/i }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Connection lost'));
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /back/i })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: /confirm/i }));
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
    expect(submit.mock.calls[1][0]).toEqual(submit.mock.calls[0][0]);
  });

  it('requires an account for an account-funded purchase', async () => {
    renderWizard();
    await fillPropertyStep();
    await fillFunding('new', 'account', false);
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
    expect(submit).not.toHaveBeenCalled();
  });

  it('rejects a payment account in another currency', async () => {
    renderWizard();
    await fillPropertyStep();
    fireEvent.click(screen.getByRole('button', { name: /back/i }));
    fireEvent.change(screen.getByTestId('wizard-currency'), { target: { value: 'EUR' } });
    fireEvent.click(screen.getByRole('button', { name: /next/i }));
    await fillFunding('none');
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
    expect(submit).not.toHaveBeenCalled();
  });
});
