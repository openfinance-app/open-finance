import { fireEvent, screen, waitFor } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import { BuyPropertyWizard } from '@/components/real-estate/BuyPropertyWizard';
import type { FundingStepState, PropertyStepState } from '@/components/real-estate/wizard/types';
import type { Account } from '@/types/account';
import type { RealEstateProperty } from '@/types/realEstate';

const state = vi.hoisted(() => ({
  propertyPatch: {} as Partial<PropertyStepState>,
  fundingPatch: {} as Partial<FundingStepState>,
  loans: [{ id: 3, currency: 'EUR' }],
  submit: vi.fn(),
}));
vi.mock('@/hooks/useLiabilities', () => ({ useLiabilities: () => ({ data: state.loans }) }));
vi.mock('@/hooks/useRealEstate', () => ({
  usePurchaseProperty: () => ({ mutateAsync: state.submit }),
}));
vi.mock('@/components/real-estate/wizard/PropertyStep', () => ({
  PropertyStep: ({
    property,
    onChange,
  }: ComponentProps<
    typeof import('@/components/real-estate/wizard/PropertyStep').PropertyStep
  >) => (
    <section aria-label="Property details">
      <output aria-label="Current property">{JSON.stringify(property)}</output>
      <button onClick={() => onChange(state.propertyPatch)}>Change property</button>
    </section>
  ),
}));
vi.mock('@/components/real-estate/wizard/FundingStep', () => ({
  FundingStep: ({
    funding,
    onChange,
  }: ComponentProps<typeof import('@/components/real-estate/wizard/FundingStep').FundingStep>) => (
    <section aria-label="Funding">
      <output aria-label="Current funding">{JSON.stringify(funding)}</output>
      <button onClick={() => onChange(state.fundingPatch)}>Change funding</button>
    </section>
  ),
}));
vi.mock('@/components/real-estate/wizard/ReviewStep', () => ({
  ReviewStep: (
    props: ComponentProps<typeof import('@/components/real-estate/wizard/ReviewStep').ReviewStep>
  ) => <output aria-label="Purchase review">{JSON.stringify(props)}</output>,
}));
const accounts = [{ id: 2, currency: 'EUR' }] as Account[];
const plan = {
  id: 9,
  name: '  Planned home ',
  address: ' Road ',
  currency: 'EUR',
  propertyType: 'RESIDENTIAL',
  purchasePrice: 9007199254740992,
  exactValues: { purchasePrice: '9007199254740993.01', rentalIncome: '1200.005' },
  acquisitionType: 'PLANNED',
  rentalIncome: 1200,
  notes: 'Keep these notes',
  latitude: 0,
  longitude: 0,
  documents: ['deed.pdf'],
} as unknown as RealEstateProperty;
function property(patch: Partial<PropertyStepState>): void {
  state.propertyPatch = patch;
  fireEvent.click(screen.getByRole('button', { name: 'Change property' }));
}
function funding(patch: Partial<FundingStepState>): void {
  state.fundingPatch = patch;
  fireEvent.click(screen.getByRole('button', { name: 'Change funding' }));
}
function next(): void {
  fireEvent.click(screen.getByRole('button', { name: i18n.t('realEstate:wizard.next') }));
}
function back(): void {
  fireEvent.click(screen.getByRole('button', { name: i18n.t('realEstate:wizard.back') }));
}
function confirm(): void {
  fireEvent.click(screen.getByRole('button', { name: i18n.t('realEstate:wizard.confirm') }));
}
function mount(planned?: RealEstateProperty) {
  const onClose = vi.fn();
  const onPurchased = vi.fn();
  const view = renderWithProviders(
    <BuyPropertyWizard
      accounts={accounts}
      plan={planned}
      onClose={onClose}
      onPurchased={onPurchased}
    />
  );
  return { ...view, onClose, onPurchased };
}
function prepare(): void {
  property({
    name: 'Home',
    address: 'Road',
    purchasePrice: '100.01',
    currency: 'EUR',
    purchaseDate: '2025-01-01',
  });
  next();
}
function currentFunding(): FundingStepState {
  return JSON.parse(screen.getByLabelText('Current funding').textContent!) as FundingStepState;
}
function currentProperty(): PropertyStepState {
  return JSON.parse(screen.getByLabelText('Current property').textContent!) as PropertyStepState;
}

beforeEach(() => {
  vi.resetAllMocks();
  mockAuthentication();
  state.loans = [{ id: 3, currency: 'EUR' }];
  state.submit.mockResolvedValue({ propertyId: 45 });
});

describe('Property purchase decisions', () => {
  it.each([1200, undefined])(
    'completes a plan while preserving its documents, coordinates and optional exact rent: %s',
    async rent => {
      const view = mount({ ...plan, rentalIncome: rent });
      expect(currentProperty().purchasePrice).toBe('9007199254740993.01');
      expect(currentProperty().currentValue).toBe('9007199254740993.01');
      next();
      funding({ source: 'none', downPaymentAccountId: 2 });
      next();
      confirm();
      await waitFor(() => expect(view.onPurchased).toHaveBeenCalledWith(45));
      expect(state.submit).toHaveBeenCalledWith(
        expect.objectContaining({
          plannedPropertyId: 9,
          loanAmount: '0',
          downPayment: '9007199254740993.01',
          property: expect.objectContaining({
            name: 'Planned home',
            address: 'Road',
            currentValue: '9007199254740993.01',
            latitude: 0,
            longitude: 0,
            notes: 'Keep these notes',
            documents: ['deed.pdf'],
            rentalIncome: rent == null ? undefined : '1200.005',
          }),
        })
      );
    }
  );

  it('uses the purchase price as the initial valuation until the user edits that valuation', () => {
    mount();
    property({ purchasePrice: '100.01' });
    expect(currentProperty().currentValue).toBe('100.01');
    property({ currentValue: '0' });
    property({ purchasePrice: '200.02' });
    expect(currentProperty().currentValue).toBe('0');
  });

  it('clears an invalid price without carrying an obsolete cash contribution forward', () => {
    mount();
    prepare();
    funding({ source: 'none', downPaymentAccountId: 2 });
    back();
    property({ purchasePrice: '' });
    expect(currentProperty().currentValue).toBe('0');
    expect(screen.getByRole('button', { name: i18n.t('realEstate:wizard.next') })).toBeDisabled();
    property({ purchasePrice: '200' });
    next();
    expect(currentFunding().downPaymentAmount).toBe('200');
  });

  it('keeps all reviewed new-mortgage terms and the selected existing payment', async () => {
    mount();
    prepare();
    funding({
      source: 'new',
      mortgageName: ' Home loan ',
      loanAmount: '80',
      route: 'account',
      downPaymentAccountId: 2,
      interestRate: '3.25',
      minimumPayment: '10.01',
      endDate: '2035-01-01',
      loanAlreadyReceived: true,
      paymentMode: 'existing',
    });
    funding({ existingLoanReceiptId: 101, existingPaymentId: 102 });
    next();
    confirm();
    await waitFor(() => expect(state.submit).toHaveBeenCalledOnce());
    expect(state.submit).toHaveBeenCalledWith(
      expect.objectContaining({
        existingLoanReceiptId: undefined,
        existingPaymentId: 102,
        newMortgage: expect.objectContaining({
          name: 'Home loan',
          principal: '80',
          interestRate: 3.25,
          minimumPayment: '10.01',
          endDate: '2035-01-01',
        }),
      })
    );
  });

  it('links an existing loan receipt without creating another disbursement', async () => {
    mount();
    prepare();
    funding({
      source: 'existing',
      existingMortgageId: 3,
      loanAmount: '80',
      route: 'account',
      downPaymentAccountId: 2,
      loanAlreadyReceived: true,
      paymentMode: 'existing',
    });
    expect(screen.getByRole('button', { name: i18n.t('realEstate:wizard.next') })).toBeDisabled();
    funding({ existingLoanReceiptId: 101, existingPaymentId: 102 });
    next();
    confirm();
    await waitFor(() => expect(state.submit).toHaveBeenCalledOnce());
    expect(state.submit).toHaveBeenCalledWith(
      expect.objectContaining({
        existingLoanReceiptId: 101,
        existingPaymentId: 102,
        existingMortgageId: 3,
        newMortgage: undefined,
      })
    );
  });

  it.each(['foreign', 'missing'])('rejects an %s existing mortgage before review', reason => {
    state.loans = reason === 'missing' ? [] : [{ id: 3, currency: 'USD' }];
    mount();
    prepare();
    funding({ source: 'existing', existingMortgageId: 3, loanAmount: '100.01' });
    expect(screen.getByRole('button', { name: i18n.t('realEstate:wizard.next') })).toBeDisabled();
  });

  it.each(['account', 'loan', 'source', 'route'] as const)(
    'clears stale payment and loan-receipt selections when %s changes',
    field => {
      mount();
      prepare();
      funding({
        source: 'new',
        mortgageName: 'Loan',
        loanAmount: '80',
        route: 'account',
        downPaymentAccountId: 2,
        loanAlreadyReceived: true,
        paymentMode: 'existing',
      });
      funding({ existingLoanReceiptId: 101, existingPaymentId: 102 });
      const patches = {
        account: { downPaymentAccountId: 4 },
        loan: { loanAmount: '70' },
        source: { source: 'existing' as const },
        route: { route: 'direct' as const },
      };
      funding(patches[field]);
      expect(currentFunding().existingPaymentId).toBeUndefined();
      expect(currentFunding().existingLoanReceiptId).toBeUndefined();
    }
  );

  it.each([400, 422, 499, 500, 503])(
    'retains uncertain purchase requests but unlocks validation failures: HTTP %s',
    async status => {
      state.submit.mockRejectedValueOnce({
        isAxiosError: true,
        response: { status, data: { message: 'Server rejected the purchase' } },
      });
      mount();
      prepare();
      funding({ source: 'none', downPaymentAccountId: 2 });
      next();
      confirm();
      expect(await screen.findByRole('alert')).toHaveTextContent('Server rejected the purchase');
      expect(screen.getByRole('button', { name: i18n.t('realEstate:wizard.back') })).toHaveProperty(
        'disabled',
        status >= 500
      );
      confirm();
      await waitFor(() => expect(state.submit).toHaveBeenCalledTimes(2));
      if (status >= 500)
        expect(state.submit.mock.calls[1][0]).toEqual(state.submit.mock.calls[0][0]);
    }
  );

  it.each([
    { isAxiosError: true },
    { isAxiosError: true, response: { status: 400 } },
    { isAxiosError: true, response: { status: 500, data: {} } },
    'network unavailable',
  ])('shows a recoverable fallback when an error has no response message: %j', async error => {
    state.submit.mockRejectedValueOnce(error);
    mount();
    prepare();
    funding({ source: 'none', downPaymentAccountId: 2 });
    next();
    confirm();
    expect(await screen.findByRole('alert')).toHaveTextContent(i18n.t('realEstate:wizard.error'));
  });
});
