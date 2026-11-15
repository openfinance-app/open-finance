import { fireEvent, screen, waitFor } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import type { Asset, AssetRequest } from '@/types/asset';
import type { FundingStepState } from '@/components/real-estate/wizard/types';
import { AssetPurchaseFlow } from '@/components/assets/AssetPurchaseFlow';
const state = vi.hoisted(() => ({
  draft: {} as AssetRequest,
  patch: {} as Partial<FundingStepState>,
  loans: [{ id: 3, currency: 'EUR' }],
  accounts: [{ id: 2, currency: 'EUR' }],
  pending: false,
  error: null as unknown,
  submit: vi.fn(),
  form: vi.fn(),
}));
vi.mock('@/hooks/useLiabilities', () => ({ useLiabilities: () => ({ data: state.loans }) }));
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => ({ data: state.accounts }) }));
vi.mock('@/hooks/useOwnershipOperation', () => ({
  useOwnershipOperation: () => ({
    mutateAsync: state.submit,
    isPending: state.pending,
    error: state.error,
  }),
}));
vi.mock('@/components/assets/AssetForm', () => ({
  AssetForm: (props: ComponentProps<typeof import('@/components/assets/AssetForm').AssetForm>) => {
    state.form(props);
    return (
      <section aria-label="Purchase details">
        <button onClick={() => props.onSubmit(state.draft)}>Review purchase</button>
        <button onClick={props.onCancel}>Cancel purchase</button>
      </section>
    );
  },
}));
vi.mock('@/components/real-estate/wizard/FundingStep', () => ({
  FundingStep: ({
    funding,
    onChange,
  }: ComponentProps<typeof import('@/components/real-estate/wizard/FundingStep').FundingStep>) => (
    <section aria-label="Funding details">
      <output aria-label="Current funding">{JSON.stringify(funding)}</output>
      <button onClick={() => onChange(state.patch)}>Change funding</button>
    </section>
  ),
}));
vi.mock('@/components/real-estate/wizard/ReviewStep', () => ({
  ReviewStep: (
    props: ComponentProps<typeof import('@/components/real-estate/wizard/ReviewStep').ReviewStep>
  ) => <output aria-label="Purchase review">{JSON.stringify(props)}</output>,
}));
const funding = (patch: Partial<FundingStepState>) => {
  state.patch = patch;
  fireEvent.click(screen.getByRole('button', { name: 'Change funding' }));
};
const confirm = () =>
  screen.getByRole('button', { name: i18n.t('assets:ownership.confirmPurchase') });
const review = () => fireEvent.click(screen.getByRole('button', { name: 'Review purchase' }));
function mount(plan?: Asset) {
  const onClose = vi.fn();
  const onPurchased = vi.fn();
  const view = renderWithProviders(
    <AssetPurchaseFlow plan={plan} onClose={onClose} onPurchased={onPurchased} />
  );
  return { ...view, onClose, onPurchased };
}
function currentFunding(): FundingStepState {
  return JSON.parse(screen.getByLabelText('Current funding').textContent!) as FundingStepState;
}
const plan: Asset = {
  id: 9,
  userId: 7,
  name: 'Planned car',
  type: 'VEHICLE',
  quantity: 1,
  purchasePrice: 9007199254740992,
  currentPrice: 1,
  currency: 'EUR',
  purchaseDate: '2030-01-01',
  createdAt: '',
  updatedAt: '',
  lastUpdated: '',
  totalCost: 1,
  totalValue: 1,
  holdingDays: 0,
  acquisitionType: 'PLANNED',
  exactValues: { purchasePrice: '9007199254740992.01' },
};

describe('Asset purchase funding and retry boundaries', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
    state.draft = {
      name: 'Car',
      type: 'VEHICLE',
      quantity: '1',
      purchasePrice: '100.01',
      currentPrice: '100.01',
      currency: 'EUR',
      purchaseDate: '2025-01-01',
      acquisitionType: 'PURCHASE',
    };
    state.patch = {};
    state.loans = [{ id: 3, currency: 'EUR' }];
    state.accounts = [{ id: 2, currency: 'EUR' }];
    state.pending = false;
    state.error = null;
    state.submit.mockResolvedValue({ assetId: 8 });
  });

  it('converts a plan to a purchase draft without losing its exact target price', () => {
    mount(plan);
    expect(state.form).toHaveBeenLastCalledWith(
      expect.objectContaining({
        asset: expect.objectContaining({
          acquisitionType: 'PURCHASE',
          purchaseDate: expect.not.stringMatching('2030'),
          exactValues: expect.objectContaining({ currentPrice: '9007199254740992.01' }),
        }),
      })
    );
  });

  it('supports legacy plan prices without exact metadata', () => {
    mount({ ...plan, purchasePrice: 100.01, exactValues: undefined });
    expect(state.form).toHaveBeenLastCalledWith(
      expect.objectContaining({
        asset: expect.objectContaining({ exactValues: { currentPrice: '100.01' } }),
      })
    );
  });

  it('preserves precise quantity and price when returning from review to edit', () => {
    state.draft.quantity = '3.00000001';
    state.draft.purchasePrice = '9007199254740993.01';
    const view = mount();
    review();
    expect(currentFunding().downPaymentAmount).toBe('27021597854294971.5774099301');
    expect(screen.getByLabelText('Purchase review')).toHaveTextContent(
      '27021597854294971.5774099301'
    );
    fireEvent.click(screen.getByRole('button', { name: i18n.t('assets:ownership.previous') }));
    expect(state.form).toHaveBeenLastCalledWith(
      expect.objectContaining({
        asset: expect.objectContaining({
          exactValues: {
            quantity: '3.00000001',
            purchasePrice: '9007199254740993.01',
            currentPrice: '100.01',
          },
        }),
      })
    );
    fireEvent.click(screen.getByRole('button', { name: 'Cancel purchase' }));
    expect(view.onClose).toHaveBeenCalledOnce();
  });

  it('posts a cash purchase against the selected native-currency account', async () => {
    const view = mount();
    review();
    expect(confirm()).toBeDisabled();
    funding({ downPaymentAccountId: 2 });
    fireEvent.click(confirm());
    await waitFor(() => expect(view.onPurchased).toHaveBeenCalledWith(8));
    expect(state.submit).toHaveBeenCalledWith({
      path: '/assets/purchase',
      data: expect.objectContaining({
        asset: state.draft,
        accountId: 2,
        loanAmount: '0',
        route: 'DIRECT',
        newLoan: undefined,
        existingLoanId: undefined,
      }),
    });
  });

  it.each(['VEHICLE', 'ELECTRONICS'] as const)(
    'posts the appropriate new loan type for %s with optional terms',
    async type => {
      state.draft.type = type;
      mount();
      review();
      funding({
        source: 'new',
        mortgageName: '  Purchase loan  ',
        loanAmount: '100.01',
        interestRate: '5.5',
        minimumPayment: '10.01',
        endDate: '2027-01-01',
      });
      fireEvent.click(confirm());
      await waitFor(() => expect(state.submit).toHaveBeenCalledOnce());
      expect(state.submit.mock.calls[0][0].data.newLoan).toEqual({
        name: 'Purchase loan',
        type: type === 'VEHICLE' ? 'AUTO_LOAN' : 'LOAN',
        principal: '100.01',
        currentBalance: '0',
        currency: 'EUR',
        startDate: '2025-01-01',
        interestRate: 5.5,
        minimumPayment: '10.01',
        endDate: '2027-01-01',
      });
    }
  );

  it('permits omitted optional loan terms and includes a completed plan identifier', async () => {
    mount(plan);
    review();
    funding({ source: 'new', mortgageName: 'Loan', loanAmount: '100.01' });
    fireEvent.click(confirm());
    await waitFor(() => expect(state.submit).toHaveBeenCalledOnce());
    expect(state.submit.mock.calls[0][0].data).toEqual(
      expect.objectContaining({
        plannedAssetId: 9,
        newLoan: expect.objectContaining({
          interestRate: undefined,
          minimumPayment: undefined,
          endDate: undefined,
        }),
      })
    );
  });

  it.each(['missing', 'foreign', 'matching'])(
    'requires a %s existing loan in the asset currency',
    async kind => {
      state.loans =
        kind === 'missing' ? [] : [{ id: 3, currency: kind === 'foreign' ? 'USD' : 'EUR' }];
      mount();
      review();
      funding({ source: 'existing', existingMortgageId: 3, loanAmount: '100.01' });
      if (kind !== 'matching') expect(confirm()).toBeDisabled();
      else {
        fireEvent.click(confirm());
        await waitFor(() => expect(state.submit).toHaveBeenCalledOnce());
        expect(state.submit.mock.calls[0][0].data.existingLoanId).toBe(3);
      }
    }
  );

  it.each([
    { mortgageName: ' ' },
    { loanAmount: '0' },
    { loanAmount: '101' },
    { loanAmount: '-1' },
    { endDate: '2024-12-31' },
  ] as Partial<FundingStepState>[])('rejects inconsistent borrowing terms %j', invalid => {
    mount();
    review();
    funding({
      source: 'new',
      mortgageName: 'Loan',
      loanAmount: '100.01',
      downPaymentAccountId: 2,
      ...invalid,
    });
    expect(confirm()).toBeDisabled();
    expect(state.submit).not.toHaveBeenCalled();
  });

  it('rejects future purchases until their actual purchase date', () => {
    state.draft.purchaseDate = '2099-01-01';
    mount();
    review();
    funding({ downPaymentAccountId: 2 });
    expect(confirm()).toBeDisabled();
  });

  it('requires an account for account-route loan proceeds and the selected receipt when already received', async () => {
    mount();
    review();
    funding({
      source: 'existing',
      existingMortgageId: 3,
      loanAmount: '100.01',
      route: 'account',
      loanAlreadyReceived: true,
    });
    expect(confirm()).toBeDisabled();
    funding({ downPaymentAccountId: 2 });
    expect(confirm()).toBeDisabled();
    funding({ existingLoanReceiptId: 12 });
    expect(confirm()).toBeEnabled();
    fireEvent.click(confirm());
    await waitFor(() => expect(state.submit).toHaveBeenCalledOnce());
    expect(state.submit.mock.calls[0][0].data).toEqual(
      expect.objectContaining({ route: 'ACCOUNT', existingLoanReceiptId: 12 })
    );
  });

  it('requires and submits the selected existing purchase payment', async () => {
    mount();
    review();
    funding({ downPaymentAccountId: 2, paymentMode: 'existing' });
    expect(confirm()).toBeDisabled();
    funding({ existingPaymentId: 11 });
    fireEvent.click(confirm());
    await waitFor(() => expect(state.submit).toHaveBeenCalledOnce());
    expect(state.submit.mock.calls[0][0].data.existingPaymentId).toBe(11);
  });

  it.each([
    { source: 'new' },
    { loanAmount: '50' },
    { route: 'direct' },
    { downPaymentAccountId: 2 },
  ] as Partial<FundingStepState>[])('clears stale payment selections when changing %j', patch => {
    mount();
    review();
    funding({
      source: 'existing',
      existingMortgageId: 3,
      loanAmount: '50',
      route: 'account',
      downPaymentAccountId: 2,
      paymentMode: 'existing',
      loanAlreadyReceived: true,
    });
    funding({ existingPaymentId: 11, existingLoanReceiptId: 12 });
    funding(patch);
    expect(currentFunding().existingPaymentId).toBeUndefined();
    expect(currentFunding().existingLoanReceiptId).toBeUndefined();
  });

  it.each([400, 422, 499, 500, 503, undefined])(
    'only unlocks funding after a definite rejected request (%s)',
    async status => {
      state.submit.mockRejectedValueOnce({
        isAxiosError: true,
        ...(status === undefined ? {} : { response: { status } }),
      });
      mount();
      review();
      funding({ downPaymentAccountId: 2 });
      fireEvent.click(confirm());
      await waitFor(() => expect(state.submit).toHaveBeenCalledOnce());
      const definite = status != null && status < 500;
      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Change funding' })).toHaveProperty(
          'disabled',
          false
        )
      );
      if (definite)
        await waitFor(() =>
          expect(screen.getByRole('button', { name: 'Change funding' })).toBeEnabled()
        );
      else expect(screen.getByRole('button', { name: 'Change funding' })).toBeDisabled();
      const firstRequest = state.submit.mock.calls[0][0].data;
      fireEvent.click(confirm());
      await waitFor(() => expect(state.submit).toHaveBeenCalledTimes(2));
      expect(state.submit.mock.calls[1][0].data).toEqual(firstRequest);
    }
  );

  it('renders a fallback error while preserving the retryable purchase', () => {
    state.error = 'unexpected';
    mount();
    review();
    expect(screen.getByRole('alert')).toHaveTextContent(i18n.t('assets:ownership.error'));
  });
});
