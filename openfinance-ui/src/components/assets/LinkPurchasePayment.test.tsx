import type { ComponentProps } from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { LinkPurchasePayment } from '@/components/assets/LinkPurchasePayment';
import type { Transaction } from '@/types/transaction';
import i18n from '@/test/i18n-test';

const operation = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
  reset: vi.fn(),
  error: null as Error | null,
  isPending: false,
}));
vi.mock('@/hooks/useOwnershipOperation', () => ({ useOwnershipOperation: () => operation }));
vi.mock('@/components/assets/PurchasePaymentSelector', () => ({
  PurchasePaymentSelector: ({
    onChange,
    value,
    currency,
  }: ComponentProps<
    typeof import('@/components/assets/PurchasePaymentSelector').PurchasePaymentSelector
  >) => (
    <select
      aria-label="Purchase payment"
      value={value ?? ''}
      onChange={event => onChange({ id: Number(event.target.value) } as Transaction)}
    >
      <option value="">Select {currency} payment</option>
      <option value="42">Seller payment</option>
    </select>
  ),
}));
const tr = (key: string) => i18n.t(`assets:ownership.${key}`);
beforeEach(() => {
  mockAuthentication();
  operation.mutateAsync.mockReset().mockResolvedValue({});
  operation.reset.mockClear();
  operation.error = null;
  operation.isPending = false;
});
afterEach(() => vi.restoreAllMocks());

it('requires a payment selection, links the selected transaction, and clears it after success', async () => {
  const user = userEvent.setup();
  renderWithProviders(<LinkPurchasePayment assetId={9} currency="EUR" purchaseDate="2026-01-01" />);
  await user.click(screen.getByRole('button', { name: tr('linkPayment') }));
  expect(operation.reset).toHaveBeenCalledOnce();
  expect(screen.getByRole('button', { name: tr('linkPayment') })).toBeDisabled();
  await user.selectOptions(screen.getByRole('combobox'), '42');
  await user.click(screen.getByRole('button', { name: tr('linkPayment') }));
  await waitFor(() => expect(screen.queryByRole('combobox')).not.toBeInTheDocument());
  expect(operation.mutateAsync).toHaveBeenCalledWith({
    path: '/assets/9/purchase-payment',
    data: { transactionId: 42 },
  });
  await user.click(screen.getByRole('button', { name: tr('linkPayment') }));
  expect(screen.getByRole('combobox')).toHaveValue('');
  await user.click(screen.getByRole('button', { name: tr('cancel') }));
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
});
it('retains the chosen payment after rejection and reports the error so it can be retried', async () => {
  operation.mutateAsync.mockRejectedValue(new Error('Already linked'));
  operation.error = new Error('Already linked');
  const user = userEvent.setup();
  renderWithProviders(<LinkPurchasePayment assetId={9} currency="EUR" purchaseDate="2026-01-01" />);
  await user.click(screen.getByRole('button', { name: tr('linkPayment') }));
  await user.selectOptions(screen.getByRole('combobox'), '42');
  await user.click(screen.getByRole('button', { name: tr('linkPayment') }));
  expect(screen.getByRole('combobox')).toHaveValue('42');
  expect(screen.getByRole('alert')).toBeInTheDocument();
  expect(operation.mutateAsync).toHaveBeenCalledOnce();
});
