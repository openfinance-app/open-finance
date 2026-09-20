/**
 * Unit tests for BuyPropertyWizard (Task 9)
 *
 * Walks the 3 steps (property → funding → review) with mocked hooks and asserts
 * the endpoint call sequence: create liability → create property (mortgageId) →
 * disburse → down-payment CAPITAL_IMPROVEMENT transaction, then close.
 */
import { screen, act, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { BuyPropertyWizard } from './BuyPropertyWizard';
import * as useLiabilitiesModule from '@/hooks/useLiabilities';
import * as useRealEstateModule from '@/hooks/useRealEstate';
import * as useTransactionsModule from '@/hooks/useTransactions';
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
    useCreateLiability: vi.fn(),
    useDisburseLiability: vi.fn(),
  };
});

vi.mock('@/hooks/useRealEstate', async importOriginal => {
  const actual = await importOriginal<typeof useRealEstateModule>();
  return {
    ...actual,
    useCreateProperty: vi.fn(),
  };
});

vi.mock('@/hooks/useTransactions', async importOriginal => {
  const actual = await importOriginal<typeof useTransactionsModule>();
  return {
    ...actual,
    useCreateTransaction: vi.fn(),
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
const mockUseCreateLiability = vi.mocked(useLiabilitiesModule.useCreateLiability);
const mockUseDisburse = vi.mocked(useLiabilitiesModule.useDisburseLiability);
const mockUseCreateProperty = vi.mocked(useRealEstateModule.useCreateProperty);
const mockUseCreateTransaction = vi.mocked(useTransactionsModule.useCreateTransaction);
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

function mockMutation<T>() {
  const mutateAsync = vi.fn().mockResolvedValue({ id: 77 } as T);
  return { mutateAsync, mock: { mutateAsync, isLoading: false, isError: false } };
}

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
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();

    mockUseLiabilities.mockReturnValue({
      data: [
        {
          id: 9,
          name: 'Existing Mortgage',
          userId: 1,
          type: 'MORTGAGE',
          currentBalance: 100000,
          currency: 'USD',
          interestRate: 3.5,
          startDate: '2020-01-01',
          isActive: true,
          createdAt: '2020-01-01',
        },
      ],
      isLoading: false,
      isError: false,
    } as any);
    mockUseAccounts.mockReturnValue({ data: mockAccounts, isLoading: false } as any);
    mockUseCreateLiability.mockReturnValue(mockMutation().mock as any);
    mockUseDisburse.mockReturnValue(mockMutation().mock as any);
    mockUseCreateProperty.mockReturnValue(mockMutation().mock as any);
    mockUseCreateTransaction.mockReturnValue(mockMutation().mock as any);
  });

  it('rejects a funding account in a different currency before creating records', async () => {
    renderWizard();
    fireEvent.change(screen.getByLabelText(/property name/i), { target: { value: 'FX Villa' } });
    fireEvent.change(screen.getByLabelText(/address/i), { target: { value: '1 Audit Rd' } });
    fireEvent.change(screen.getByLabelText(/purchase price/i), { target: { value: '1000' } });
    fireEvent.change(screen.getByLabelText(/current value/i), { target: { value: '1000' } });
    fireEvent.change(screen.getByTestId('wizard-currency'), { target: { value: 'EUR' } });
    await act(async () => {
      screen.getByRole('button', { name: /next/i }).click();
    });
    fireEvent.change(screen.getByLabelText(/funding source/i), { target: { value: 'none' } });
    fireEvent.change(screen.getByLabelText(/down payment amount/i), { target: { value: '100' } });
    await act(async () => {
      screen.getByRole('button', { name: 'Account' }).click();
    });
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
    expect(mockUseCreateProperty().mutateAsync).not.toHaveBeenCalled();
    expect(mockUseCreateTransaction().mutateAsync).not.toHaveBeenCalled();
  });

  it('renders the property step first with no funding fields', () => {
    renderWizard();

    expect(screen.getByLabelText(/property name/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/loan amount/i)).not.toBeInTheDocument();
  });

  it('creates liability, property, disbursement and down-payment in order, then closes', async () => {
    const liabilityMutation = mockMutation();
    const disburseMutation = mockMutation();
    const propertyMutation = mockMutation<{ id: number }>();
    propertyMutation.mock.mutateAsync.mockResolvedValue({ id: 55 } as { id: number });
    const txMutation = mockMutation();

    mockUseCreateLiability.mockReturnValue(liabilityMutation.mock as any);
    mockUseDisburse.mockReturnValue(disburseMutation.mock as any);
    mockUseCreateProperty.mockReturnValue(propertyMutation.mock as any);
    mockUseCreateTransaction.mockReturnValue(txMutation.mock as any);

    const { onClose } = renderWizard();

    // Step 1: property
    await fillPropertyStep();

    // Step 2: funding — new mortgage, direct to seller, 60000 down payment from account 1
    fireEvent.change(screen.getByLabelText(/funding source/i), { target: { value: 'new' } });
    fireEvent.change(screen.getByLabelText(/mortgage name/i), { target: { value: 'Home loan' } });
    fireEvent.change(screen.getByLabelText(/loan amount/i), { target: { value: '240000' } });
    fireEvent.change(screen.getByLabelText(/interest rate/i), { target: { value: '3.5' } });
    fireEvent.change(screen.getByLabelText(/disbursement route/i), { target: { value: 'direct' } });
    fireEvent.change(screen.getByLabelText(/down payment amount/i), { target: { value: '60000' } });
    await act(async () => {
      screen.getByTestId('wizard-account').click();
    });
    await act(async () => {
      screen.getByRole('button', { name: /next/i }).click();
    });

    // Step 3: review + confirm
    expect(screen.getByText(/villa/i)).toBeInTheDocument();
    await act(async () => {
      screen.getByRole('button', { name: /confirm/i }).click();
    });

    await waitFor(() => expect(onClose).toHaveBeenCalled());

    // Sequence: liability before property before disbursement before down-payment
    const order = {
      liability: liabilityMutation.mutateAsync.mock.invocationCallOrder[0],
      property: propertyMutation.mutateAsync.mock.invocationCallOrder[0],
      disburse: disburseMutation.mutateAsync.mock.invocationCallOrder[0],
      downPayment: txMutation.mutateAsync.mock.invocationCallOrder[0],
    };
    expect(order.liability).toBeLessThan(order.property);
    expect(order.property).toBeLessThan(order.disburse);
    expect(order.disburse).toBeLessThan(order.downPayment);

    expect(liabilityMutation.mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Home loan',
        type: 'MORTGAGE',
        principal: '240000',
        currentBalance: '0',
        currency: 'USD',
      })
    );
    expect(propertyMutation.mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Villa',
        mortgageId: 77,
        purchasePrice: '300000',
        currentValue: '0',
      })
    );
    expect(disburseMutation.mutateAsync).toHaveBeenCalledWith({
      liabilityId: 77,
      request: expect.objectContaining({ directRealEstateId: 55, amount: 240000 }),
    });
    expect(txMutation.mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        realEstateId: 55,
        accountId: 1,
        amount: 60000,
        type: 'EXPENSE',
      })
    );
  });

  it('uses an existing mortgage and disburses to the checking account when selected', async () => {
    const disburseMutation = mockMutation();
    const propertyMutation = mockMutation<{ id: number }>();
    propertyMutation.mock.mutateAsync.mockResolvedValue({ id: 55 } as { id: number });
    mockUseDisburse.mockReturnValue(disburseMutation.mock as any);
    mockUseCreateProperty.mockReturnValue(propertyMutation.mock as any);

    renderWizard();

    await fillPropertyStep();

    fireEvent.change(screen.getByLabelText(/funding source/i), { target: { value: 'existing' } });
    fireEvent.change(screen.getByTestId('wizard-mortgage'), { target: { value: '9' } });
    fireEvent.change(screen.getByLabelText(/disburse now/i), { target: { value: '240000' } });
    fireEvent.change(screen.getByLabelText(/disbursement route/i), {
      target: { value: 'account' },
    });
    await act(async () => {
      screen.getByTestId('wizard-account').click();
    });
    await act(async () => {
      screen.getByRole('button', { name: /next/i }).click();
    });
    await act(async () => {
      screen.getByRole('button', { name: /confirm/i }).click();
    });

    await waitFor(() =>
      expect(disburseMutation.mutateAsync).toHaveBeenCalledWith({
        liabilityId: 9,
        request: expect.objectContaining({ toAccountId: 1 }),
      })
    );
    // No new mortgage was created
    expect(mockUseCreateLiability().mutateAsync).not.toHaveBeenCalled();
  });

  it('supports a pure cash purchase: no liability, no disbursement, only the down payment', async () => {
    const propertyMutation = mockMutation<{ id: number }>();
    propertyMutation.mock.mutateAsync.mockResolvedValue({ id: 55 } as { id: number });
    const txMutation = mockMutation();
    mockUseCreateProperty.mockReturnValue(propertyMutation.mock as any);
    mockUseCreateTransaction.mockReturnValue(txMutation.mock as any);
    const disburseMutation = mockMutation();
    mockUseDisburse.mockReturnValue(disburseMutation.mock as any);

    renderWizard();

    await fillPropertyStep();

    fireEvent.change(screen.getByLabelText(/funding source/i), { target: { value: 'none' } });
    fireEvent.change(screen.getByLabelText(/down payment amount/i), {
      target: { value: '300000' },
    });
    await act(async () => {
      screen.getByTestId('wizard-account').click();
    });
    await act(async () => {
      screen.getByRole('button', { name: /next/i }).click();
    });
    await act(async () => {
      screen.getByRole('button', { name: /confirm/i }).click();
    });

    await waitFor(() => expect(txMutation.mutateAsync).toHaveBeenCalled());
    expect(mockUseCreateLiability().mutateAsync).not.toHaveBeenCalled();
    expect(disburseMutation.mutateAsync).not.toHaveBeenCalled();
    expect(txMutation.mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ amount: 300000, realEstateId: 55 })
    );
  });

  it('skips re-creating the liability and property when retrying after a disbursement failure', async () => {
    const liabilityMutation = mockMutation();
    const propertyMutation = mockMutation<{ id: number }>();
    propertyMutation.mock.mutateAsync.mockResolvedValue({ id: 55 } as { id: number });
    const disburseMutation = mockMutation();
    // First attempt fails, retry succeeds
    disburseMutation.mock.mutateAsync
      .mockRejectedValueOnce(new Error('disbursement failed'))
      .mockResolvedValue({ id: 77 });
    const txMutation = mockMutation();

    mockUseCreateLiability.mockReturnValue(liabilityMutation.mock as any);
    mockUseCreateProperty.mockReturnValue(propertyMutation.mock as any);
    mockUseDisburse.mockReturnValue(disburseMutation.mock as any);
    mockUseCreateTransaction.mockReturnValue(txMutation.mock as any);

    const { onClose } = renderWizard();

    await fillPropertyStep();

    fireEvent.change(screen.getByLabelText(/funding source/i), { target: { value: 'new' } });
    fireEvent.change(screen.getByLabelText(/mortgage name/i), { target: { value: 'Home loan' } });
    fireEvent.change(screen.getByLabelText(/loan amount/i), { target: { value: '240000' } });
    fireEvent.change(screen.getByLabelText(/disbursement route/i), { target: { value: 'direct' } });
    await act(async () => {
      screen.getByRole('button', { name: /next/i }).click();
    });

    // First confirm: the disbursement fails and the wizard shows the error
    await act(async () => {
      screen.getByRole('button', { name: /confirm/i }).click();
    });
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/disbursement failed/i);
    });
    expect(onClose).not.toHaveBeenCalled();

    // Retry: the already-created liability and property are reused (no duplicates)
    await act(async () => {
      screen.getByRole('button', { name: /confirm/i }).click();
    });

    await waitFor(() => expect(onClose).toHaveBeenCalled());

    expect(liabilityMutation.mutateAsync).toHaveBeenCalledTimes(1);
    expect(propertyMutation.mutateAsync).toHaveBeenCalledTimes(1);
    expect(disburseMutation.mutateAsync).toHaveBeenCalledTimes(2);
    expect(disburseMutation.mutateAsync).toHaveBeenLastCalledWith({
      liabilityId: 77,
      request: expect.objectContaining({ directRealEstateId: 55, amount: 240000 }),
    });
  });

  it('does not re-disburse when retrying after a down-payment failure', async () => {
    const liabilityMutation = mockMutation();
    const propertyMutation = mockMutation<{ id: number }>();
    propertyMutation.mock.mutateAsync.mockResolvedValue({ id: 55 } as { id: number });
    const disburseMutation = mockMutation();
    const txMutation = mockMutation();
    // First attempt fails on the down-payment transaction, retry succeeds
    txMutation.mock.mutateAsync
      .mockRejectedValueOnce(new Error('down-payment failed'))
      .mockResolvedValue({ id: 88 });

    mockUseCreateLiability.mockReturnValue(liabilityMutation.mock as any);
    mockUseCreateProperty.mockReturnValue(propertyMutation.mock as any);
    mockUseDisburse.mockReturnValue(disburseMutation.mock as any);
    mockUseCreateTransaction.mockReturnValue(txMutation.mock as any);

    const { onClose } = renderWizard();

    await fillPropertyStep();

    fireEvent.change(screen.getByLabelText(/funding source/i), { target: { value: 'new' } });
    fireEvent.change(screen.getByLabelText(/mortgage name/i), { target: { value: 'Home loan' } });
    fireEvent.change(screen.getByLabelText(/loan amount/i), { target: { value: '240000' } });
    fireEvent.change(screen.getByLabelText(/down payment amount/i), { target: { value: '60000' } });
    await act(async () => {
      screen.getByTestId('wizard-account').click();
    });
    await act(async () => {
      screen.getByRole('button', { name: /next/i }).click();
    });

    // First confirm: the down payment fails, the disbursement already succeeded
    await act(async () => {
      screen.getByRole('button', { name: /confirm/i }).click();
    });
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/down-payment failed/i);
    });
    expect(disburseMutation.mutateAsync).toHaveBeenCalledTimes(1);

    // Retry: the completed disbursement must not be replayed
    await act(async () => {
      screen.getByRole('button', { name: /confirm/i }).click();
    });

    await waitFor(() => expect(onClose).toHaveBeenCalled());

    expect(liabilityMutation.mutateAsync).toHaveBeenCalledTimes(1);
    expect(propertyMutation.mutateAsync).toHaveBeenCalledTimes(1);
    expect(disburseMutation.mutateAsync).toHaveBeenCalledTimes(1);
    expect(txMutation.mutateAsync).toHaveBeenCalledTimes(2);
  });

  it('locks the funding source after the property has been created', async () => {
    const propertyMutation = mockMutation<{ id: number }>();
    propertyMutation.mock.mutateAsync.mockResolvedValue({ id: 55 } as { id: number });
    const disburseMutation = mockMutation();
    disburseMutation.mock.mutateAsync.mockRejectedValue(new Error('disbursement failed'));
    mockUseCreateProperty.mockReturnValue(propertyMutation.mock as any);
    mockUseDisburse.mockReturnValue(disburseMutation.mock as any);

    renderWizard();

    await fillPropertyStep();

    fireEvent.change(screen.getByLabelText(/funding source/i), { target: { value: 'new' } });
    fireEvent.change(screen.getByLabelText(/mortgage name/i), { target: { value: 'Home loan' } });
    fireEvent.change(screen.getByLabelText(/loan amount/i), { target: { value: '240000' } });
    await act(async () => {
      screen.getByRole('button', { name: /next/i }).click();
    });

    // First confirm fails at the disbursement: liability and property are already created
    await act(async () => {
      screen.getByRole('button', { name: /confirm/i }).click();
    });
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/disbursement failed/i);
    });

    // Back to the funding step: the property's mortgage link is fixed, so the
    // funding source can no longer be switched
    await act(async () => {
      screen.getByRole('button', { name: /back/i }).click();
    });

    expect(screen.getByLabelText(/funding source/i)).toBeDisabled();
    expect(screen.getByText(/locked/i)).toBeInTheDocument();
  });

  it('blocks advancing to review when the account route has no account selected', async () => {
    renderWizard();

    await fillPropertyStep();

    fireEvent.change(screen.getByLabelText(/funding source/i), { target: { value: 'new' } });
    fireEvent.change(screen.getByLabelText(/mortgage name/i), { target: { value: 'Home loan' } });
    fireEvent.change(screen.getByLabelText(/loan amount/i), { target: { value: '240000' } });
    fireEvent.change(screen.getByLabelText(/disbursement route/i), {
      target: { value: 'account' },
    });

    // No down payment account selected: Next is disabled and a validation message is shown
    const nextButton = screen.getByRole('button', { name: /next/i });
    expect(nextButton).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent(/select the account/i);

    // Selecting the account clears the error and re-enables Next
    await act(async () => {
      screen.getByTestId('wizard-account').click();
    });
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
