import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError } from 'axios';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { AssetPurchaseFlow } from '@/components/assets/AssetPurchaseFlow';
import { AssetSaleForm } from '@/components/assets/AssetSaleForm';
import { OwnershipActions } from '@/components/assets/OwnershipActions';
import type { AssetRequest } from '@/types/asset';

const mocks = vi.hoisted(() => ({ mutateAsync: vi.fn() }));
vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: () => ({
    data: [
      { id: 5, name: 'Checking', currency: 'EUR', balance: 20000, ownBalance: 20000 },
      { id: 6, name: 'USD account', currency: 'USD', balance: 20000, ownBalance: 20000 },
    ],
  }),
}));
vi.mock('@/hooks/useLiabilities', () => ({ useLiabilities: () => ({ data: [] }) }));
vi.mock('@/components/ui/AccountSelector', () => ({
  AccountSelector: ({
    id,
    value,
    onValueChange,
    disabled,
  }: {
    id?: string;
    value?: number;
    onValueChange: (value: number) => void;
    disabled?: boolean;
  }) => (
    <select
      id={id}
      value={value ?? ''}
      disabled={disabled}
      onChange={event => onValueChange(Number(event.target.value))}
    >
      <option value="">Choose account</option>
      <option value="5">Checking</option>
    </select>
  ),
}));
vi.mock('@/hooks/useOwnershipOperation', () => ({
  useOwnershipOperation: () => ({ mutateAsync: mocks.mutateAsync, isPending: false, error: null }),
}));
vi.mock('@/components/assets/AssetForm', () => ({
  AssetForm: ({ onSubmit }: { onSubmit: (data: AssetRequest) => void }) => (
    <button
      onClick={() =>
        onSubmit({
          name: 'Car',
          type: 'VEHICLE',
          quantity: '1',
          purchasePrice: '15000',
          currentPrice: '15000',
          currency: 'EUR',
          purchaseDate: '2025-01-01',
        })
      }
    >
      Review payment
    </button>
  ),
}));
vi.mock('@/components/assets/PurchasePaymentSelector', () => ({
  PurchasePaymentSelector: ({ onChange }: { onChange: (payment: { id: number }) => void }) => (
    <button onClick={() => onChange({ id: 88 })}>Imported purchase</button>
  ),
}));
vi.mock('@/components/ui/DateInput', () => ({
  DateInput: ({ value, onChange }: { value?: string; onChange: (value: string) => void }) => (
    <input aria-label="Sale date" value={value} onChange={event => onChange(event.target.value)} />
  ),
}));

const target = {
  id: 9,
  name: 'Shares',
  currency: 'EUR',
  quantity: '10',
  currentValue: '1200',
  purchaseDate: '2025-01-01',
  financial: true,
  accountId: 5,
};

describe('Ownership workflows', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
    mocks.mutateAsync.mockReset();
    mocks.mutateAsync.mockResolvedValue({ assetId: 9 });
  });
  it('links an imported purchase with no second cash debit', async () => {
    const user = userEvent.setup();
    const onPurchased = vi.fn();
    renderWithProviders(<AssetPurchaseFlow onClose={vi.fn()} onPurchased={onPurchased} />);
    await user.click(screen.getByRole('button', { name: 'Review payment' }));
    await user.selectOptions(screen.getByLabelText('Paid from'), '5');
    await user.selectOptions(screen.getByLabelText('Payment entry'), 'existing');
    expect(screen.getByRole('button', { name: 'Confirm purchase' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Imported purchase' }));
    expect(
      screen.getByText('Cash change', { selector: 'dt' }).nextElementSibling
    ).toHaveTextContent('€0.00');
    await user.click(screen.getByRole('button', { name: 'Confirm purchase' }));
    await waitFor(() => expect(onPurchased).toHaveBeenCalledWith(9));
    expect(mocks.mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        path: '/assets/purchase',
        data: expect.objectContaining({ accountId: 5, existingPaymentId: 88 }),
      })
    );
  });
  it('keeps the same payload and locks editing after an uncertain network failure', async () => {
    mocks.mutateAsync
      .mockRejectedValueOnce(new AxiosError('Network unavailable'))
      .mockResolvedValueOnce({ assetId: 9 });
    const user = userEvent.setup();
    renderWithProviders(<AssetPurchaseFlow onClose={vi.fn()} onPurchased={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Review payment' }));
    await user.selectOptions(screen.getByLabelText('Paid from'), '5');
    await user.click(screen.getByRole('button', { name: 'Confirm purchase' }));
    expect(screen.getByLabelText('Paid from')).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Confirm purchase' }));
    expect(mocks.mutateAsync.mock.calls[1][0].data).toEqual(
      mocks.mutateAsync.mock.calls[0][0].data
    );
  });
  it('clears payment matching when a direct loan covers the entire purchase', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AssetPurchaseFlow onClose={vi.fn()} onPurchased={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Review payment' }));
    await user.selectOptions(screen.getByLabelText('Paid from'), '5');
    await user.selectOptions(screen.getByLabelText('Payment entry'), 'existing');
    await user.click(screen.getByRole('button', { name: 'Imported purchase' }));
    await user.selectOptions(screen.getByLabelText(/funding source/i), 'new');
    await user.type(screen.getByLabelText(/loan name/i), 'Car loan');
    fireEvent.change(screen.getByLabelText(/loan amount/i), { target: { value: '15000' } });
    expect(screen.queryByLabelText('Payment entry')).not.toBeInTheDocument();
    expect(
      screen.getByText('Cash change', { selector: 'dt' }).nextElementSibling
    ).toHaveTextContent('€0.00');
    expect(screen.getByRole('button', { name: 'Confirm purchase' })).toBeEnabled();
    fireEvent.change(screen.getByLabelText(/loan amount/i), { target: { value: '14000' } });
    expect(screen.getByLabelText('Payment entry')).toHaveValue('new');
    fireEvent.change(screen.getByLabelText(/loan amount/i), { target: { value: '15000' } });
    await user.click(screen.getByRole('button', { name: 'Confirm purchase' }));
    await waitFor(() => expect(mocks.mutateAsync).toHaveBeenCalledOnce());
    expect(mocks.mutateAsync.mock.calls[0][0].data).toMatchObject({
      loanAmount: '15000',
      route: 'DIRECT',
      existingPaymentId: undefined,
    });
  });
  it('reviews net sale proceeds and sends exact fees and units', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderWithProviders(<AssetSaleForm target={target} onClose={onClose} />);
    await user.clear(screen.getByLabelText('Sale fees'));
    await user.type(screen.getByLabelText('Sale fees'), '20');
    expect(screen.getByText('Net sale proceeds').nextElementSibling).toHaveTextContent('€1,180.00');
    await user.click(screen.getByRole('button', { name: 'Confirm sale' }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(mocks.mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        path: '/assets/9/sale',
        data: expect.objectContaining({ fees: '20', quantity: '10', proceeds: '1200' }),
      })
    );
  });
  it('rejects an oversale before making a request', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AssetSaleForm target={target} onClose={vi.fn()} />);
    await user.clear(screen.getByLabelText('Units to sell'));
    await user.type(screen.getByLabelText('Units to sell'), '11');
    await user.click(screen.getByRole('button', { name: 'Confirm sale' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('cannot exceed');
    expect(mocks.mutateAsync).not.toHaveBeenCalled();
  });
  it('shows retained sale results and prevents another sale when closed', () => {
    renderWithProviders(<OwnershipActions target={target} closed realizedGain={200} />);
    expect(screen.getByText('Realized gain / loss').nextElementSibling).toHaveTextContent(
      '€200.00'
    );
    expect(screen.queryByRole('button', { name: 'Sell', exact: true })).not.toBeInTheDocument();
  });

  it('allows buying again into a zero-unit investment while retaining its history', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OwnershipActions
        target={{ ...target, quantity: '0', currentValue: '0' }}
        closed
        realizedGain={200}
      />
    );
    await user.click(screen.getByRole('button', { name: 'Buy more', exact: true }));
    await user.type(screen.getByLabelText('Units to buy'), '2');
    await user.type(screen.getByLabelText(/Total paid/), '250');
    await user.click(screen.getByRole('button', { name: 'Confirm purchase', exact: true }));
    await waitFor(() =>
      expect(mocks.mutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          path: '/assets/9/buy',
          data: expect.objectContaining({ quantity: '2', amount: '250' }),
        })
      )
    );
    expect(screen.getByText('Realized gain / loss').nextElementSibling).toHaveTextContent(
      '€200.00'
    );
    expect(screen.queryByRole('button', { name: 'Sell', exact: true })).not.toBeInTheDocument();
  });

  it('does not offer buying again for a sold physical possession', () => {
    renderWithProviders(<OwnershipActions target={{ ...target, financial: false }} closed />);
    expect(screen.queryByRole('button', { name: 'Buy more', exact: true })).not.toBeInTheDocument();
  });
});
