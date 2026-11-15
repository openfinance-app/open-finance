import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DisburseForm } from '@/components/liabilities/DisburseForm';
import { renderWithProviders, mockAuthentication, userEvent } from '@/test/test-utils';
import { server } from '@/test/mocks/server';
import type { Liability, LiabilityTranche } from '@/types/liability';
import type { Asset } from '@/types/asset';
const state = vi.hoisted(() => ({
  save: vi.fn(),
  pending: false,
  error: null as unknown,
  assets: [] as Pick<
    Asset,
    | 'id'
    | 'name'
    | 'type'
    | 'currency'
    | 'realEstateId'
    | 'isPhysical'
    | 'isActive'
    | 'soldDate'
    | 'acquisitionType'
  >[],
  accounts: [
    { id: 1, name: 'Euro account', currency: 'EUR' },
    { id: 2, name: 'Dollar account', currency: 'USD' },
  ],
}));
vi.mock('@/hooks/useLiabilities', () => ({
  useDisburseLiability: () => ({
    mutateAsync: state.save,
    isError: state.error != null,
    error: state.error,
    isPending: state.pending,
  }),
}));
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => ({ data: state.accounts }) }));
vi.mock('@/hooks/useAssets', () => ({ useAssets: () => ({ data: state.assets }) }));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: { amount: string | number }) => <span>{amount}</span>,
}));
const loan: Liability = {
  id: 5,
  name: 'Loan',
  type: 'PERSONAL_LOAN',
  principal: 100,
  currentBalance: 20,
  currency: 'EUR',
  startDate: '2024-01-01',
  createdAt: '2024-01-01',
  updatedAt: '2024-01-01',
};
const tranche: LiabilityTranche = {
  id: 7,
  liabilityId: 5,
  trancheNo: 1,
  plannedAmount: 50,
  remaining: 30,
  status: 'PLANNED',
  currency: 'EUR',
};
const car = { id: 10, name: 'Car', type: 'VEHICLE' as const, isPhysical: true, currency: 'EUR' };

describe('drawdown routing and exact preview boundaries', () => {
  const done = vi.fn();
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
    state.save.mockResolvedValue({});
    state.error = null;
    state.pending = false;
    state.assets = [car];
    server.use(
      http.get('*/liabilities/5/asset-links', () =>
        HttpResponse.json([
          { assetId: 10, relationship: 'FINANCING' },
          { assetId: 11, relationship: 'FINANCING' },
        ])
      )
    );
  });
  it.each([
    ['PLANNED', '50'],
    ['DRAWN', '30'],
  ] as const)('prefills %s tranches with their appropriate amount', (status, value) => {
    renderWithProviders(
      <DisburseForm liability={loan} tranche={{ ...tranche, status }} onDone={done} />
    );
    expect(screen.getByLabelText(/Amount/)).toHaveValue(value);
  });
  it('uses approval and funded totals when present instead of principal and current balance', () => {
    renderWithProviders(
      <DisburseForm liability={{ ...loan, approvedAmount: 120, fundedAmount: 100 }} onDone={done} />
    );
    expect(screen.getByLabelText(/Amount/)).toHaveValue('20');
  });
  it('cannot overdraw a fully funded approval', () => {
    renderWithProviders(
      <DisburseForm liability={{ ...loan, approvedAmount: 100, fundedAmount: 120 }} onDone={done} />
    );
    expect(screen.getByLabelText(/Amount/)).toHaveValue('0');
    expect(screen.getByRole('button', { name: 'Confirm disbursement' })).toBeDisabled();
  });
  it('direct physical funding sends the asset ID without a property ID or cash receipt', async () => {
    const user = userEvent.setup();
    renderWithProviders(<DisburseForm liability={loan} onDone={done} />);
    await screen.findByRole('option', { name: 'Directly to the seller' });
    await user.selectOptions(screen.getByLabelText('Route'), 'property');
    const target = await screen.findByLabelText('Financed asset or property');
    await user.selectOptions(target, '10');
    expect(
      screen.queryByRole('checkbox', { name: 'Add to property cost' })
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirm disbursement' }));
    await waitFor(() =>
      expect(state.save).toHaveBeenCalledWith({
        liabilityId: 5,
        request: expect.objectContaining({
          amount: '80',
          directAssetId: 10,
          directRealEstateId: undefined,
          toAccountId: undefined,
          capitalizePropertyCost: false,
        }),
      })
    );
    expect(done).toHaveBeenCalledOnce();
  });
  it('clearing a selected physical target disables submission', async () => {
    const user = userEvent.setup();
    renderWithProviders(<DisburseForm liability={loan} onDone={done} />);
    await screen.findByRole('option', { name: 'Directly to the seller' });
    await user.selectOptions(screen.getByLabelText('Route'), 'property');
    const target = await screen.findByLabelText('Financed asset or property');
    await user.selectOptions(target, '10');
    await user.selectOptions(target, '');
    expect(screen.getByRole('button', { name: 'Confirm disbursement' })).toBeDisabled();
    fireEvent.submit(target.closest('form')!);
    expect(state.save).not.toHaveBeenCalled();
  });
  it('a linked property remains routable when its backing asset is not in the list', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <DisburseForm
        liability={{ ...loan, linkedPropertyId: 8, linkedPropertyName: 'Linked home' }}
        onDone={done}
      />
    );
    await screen.findByRole('option', { name: 'Directly to the seller' });
    await user.selectOptions(screen.getByLabelText('Route'), 'property');
    expect(await screen.findByRole('option', { name: 'Linked home' })).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Confirm disbursement' }));
    await waitFor(() =>
      expect(state.save).toHaveBeenCalledWith({
        liabilityId: 5,
        request: expect.objectContaining({ directRealEstateId: 8, directAssetId: undefined }),
      })
    );
  });
  it('changing from a capitalized property to a physical asset clears both property options', async () => {
    state.assets.push({
      id: 11,
      name: 'Home',
      type: 'REAL_ESTATE',
      realEstateId: 8,
      currency: 'EUR',
    });
    const user = userEvent.setup();
    renderWithProviders(<DisburseForm liability={loan} onDone={done} />);
    await screen.findByRole('option', { name: 'Directly to the seller' });
    await user.selectOptions(screen.getByLabelText('Route'), 'property');
    const target = await screen.findByLabelText('Financed asset or property');
    await user.selectOptions(target, '11');
    await user.click(screen.getByRole('checkbox', { name: 'Add to property cost' }));
    await user.click(
      screen.getByRole('checkbox', { name: 'Also increase the property value by this amount' })
    );
    await user.selectOptions(target, '10');
    await user.click(screen.getByRole('button', { name: 'Confirm disbursement' }));
    await waitFor(() =>
      expect(state.save).toHaveBeenCalledWith({
        liabilityId: 5,
        request: expect.objectContaining({
          directAssetId: 10,
          capitalizePropertyCost: false,
          increasePropertyValue: false,
        }),
      })
    );
  });
  it('clearing the bank account removes the cash preview and prevents submission', async () => {
    const user = userEvent.setup();
    renderWithProviders(<DisburseForm liability={loan} onDone={done} />);
    const account = screen.getByLabelText(/Account/);
    expect(
      within(account).queryByRole('option', { name: 'Dollar account' })
    ).not.toBeInTheDocument();
    await user.selectOptions(account, '1');
    expect(screen.getByRole('button', { name: 'Confirm disbursement' })).toBeEnabled();
    await user.selectOptions(account, '');
    expect(screen.getByRole('button', { name: 'Confirm disbursement' })).toBeDisabled();
  });
  it('cash preview and submitted amount retain precision beyond the JS number range', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <DisburseForm
        liability={{
          ...loan,
          principal: 1e16,
          currentBalance: 0,
          exactValues: { principal: '10000000000000001', currentBalance: '0' },
        }}
        onDone={done}
      />
    );
    await user.selectOptions(screen.getByLabelText(/Account/), '1');
    expect(screen.getAllByText('10000000000000001')).toHaveLength(2);
    await user.click(screen.getByRole('button', { name: 'Confirm disbursement' }));
    await waitFor(() =>
      expect(state.save).toHaveBeenCalledWith({
        liabilityId: 5,
        request: expect.objectContaining({ amount: '10000000000000001', toAccountId: 1 }),
      })
    );
  });
  it.each([
    new Error('offline'),
    { isAxiosError: true, response: { data: { message: 'Loan already drawn' } } },
  ])('shows mutation errors without closing the form', error => {
    state.error = error;
    renderWithProviders(<DisburseForm liability={loan} onDone={done} />);
    expect(screen.getByRole('alert')).not.toBeEmptyDOMElement();
    expect(done).not.toHaveBeenCalled();
  });
  it('keeps the form open after a rejected submission so the user can retry', async () => {
    state.save.mockRejectedValueOnce(new Error('offline'));
    const user = userEvent.setup();
    renderWithProviders(<DisburseForm liability={loan} onDone={done} />);
    await user.selectOptions(screen.getByLabelText(/Account/), '1');
    await user.click(screen.getByRole('button', { name: 'Confirm disbursement' }));
    await waitFor(() => expect(state.save).toHaveBeenCalledOnce());
    expect(done).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Confirm disbursement' }));
    await waitFor(() => expect(done).toHaveBeenCalledOnce());
  });
});
