import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { PurchasePaymentSelector } from '@/components/assets/PurchasePaymentSelector';
import type { Transaction } from '@/types/transaction';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';

const source = vi.hoisted(() => ({
  query: vi.fn(),
  loading: false,
  error: null as Error | null,
  data: undefined as { content: Transaction[]; totalPages: number } | undefined,
}));
vi.mock('@/hooks/useTransactions', () => ({
  useTransactions: (query: unknown) => {
    source.query(query);
    return { data: source.data, isLoading: source.loading, error: source.error };
  },
}));
const payment: Transaction = {
  id: 1,
  userId: 1,
  accountId: 1,
  accountName: 'Checking',
  description: 'Purchase payment',
  amount: 100,
  currency: 'EUR',
  type: 'EXPENSE',
  date: '2025-01-02',
  createdAt: '',
  updatedAt: '',
  isReconciled: false,
};
const choose = vi.fn();
const label = (key: string) => i18n.t(`ownership.${key}`, { ns: 'assets' });
function renderSelector(props: Partial<ComponentProps<typeof PurchasePaymentSelector>> = {}) {
  return renderWithProviders(
    <PurchasePaymentSelector currency="EUR" onChange={choose} {...props} />
  );
}

describe('Purchase payment eligibility and pagination', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    source.loading = false;
    source.error = null;
    source.data = { content: [payment], totalPages: 1 };
  });

  it.each([
    { currency: 'USD' },
    { transferId: 'transfer-1' },
    { assetId: 4 },
    { realEstateId: 4 },
    { liabilityId: 4 },
    { movementType: 'MAINTENANCE' as const },
    { investmentQuantity: 0 },
    { investmentQuantity: 1 },
  ])('excludes an already linked or incompatible payment (%j)', excluded => {
    source.data!.content = [
      payment,
      { ...payment, ...excluded, id: 2, description: 'Ineligible payment' },
    ];
    renderSelector();
    expect(screen.getAllByRole('radio')).toHaveLength(1);
    expect(screen.queryByText('Ineligible payment')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio'));
    expect(choose).toHaveBeenCalledExactlyOnceWith(payment);
  });

  it.each([
    [4, 'DISBURSEMENT', true],
    [5, 'DISBURSEMENT', false],
    [undefined, 'DISBURSEMENT', false],
    [4, 'REPAYMENT', false],
    [4, undefined, false],
  ] as const)(
    'matches loan %s and movement %s for a disbursement receipt',
    (liabilityId, movementType, eligible) => {
      source.data!.content = [{ ...payment, type: 'INCOME', liabilityId, movementType }];
      renderSelector({ loanId: 4 });
      expect(screen.queryAllByRole('radio')).toHaveLength(eligible ? 1 : 0);
      expect(screen.getByText(label('chooseReceipt'))).toBeInTheDocument();
      expect(source.query).toHaveBeenLastCalledWith(
        expect.objectContaining({ type: 'INCOME', excludeTransfers: true })
      );
    }
  );

  it.each([
    ['9007199254740992.01', true],
    ['9007199254740992.02', false],
    ['9007199254740992.0100', true],
    [undefined, true],
    ['0', false],
  ] as const)('compares requested amount %s without rounding away cents', (amount, eligible) => {
    source.data!.content = [
      { ...payment, amount: 9007199254740992, exactValues: { amount: '9007199254740992.01' } },
    ];
    renderSelector({ amount });
    expect(screen.queryAllByRole('radio')).toHaveLength(eligible ? 1 : 0);
    if (eligible) expect(screen.getByText(/9,007,199,254,740,992\.01/)).toBeInTheDocument();
  });

  it.each([
    [false, undefined, '2025-01-02', '2024-12-01', '2025-01-02'],
    [true, undefined, '2025-01-02', '2024-12-01', '2024-12-01'],
    [false, 4, '2025-01-02', '2024-12-01', '2024-12-01'],
    [false, undefined, undefined, '2024-12-01', '2024-12-01'],
    [true, undefined, '2025-01-02', undefined, undefined],
  ] as const)(
    'sends the allowed payment date window (%s,%s,%s,%s)',
    (allowEarlier, loanId, date, fromDate, dateFrom) => {
      renderSelector({ allowEarlier, loanId, date, fromDate, accountId: 9 });
      expect(source.query).toHaveBeenLastCalledWith(
        expect.objectContaining({
          accountId: 9,
          dateFrom,
          dateTo: date,
          page: 0,
          size: 20,
          sort: 'date,desc',
        })
      );
    }
  );

  it.each(['loading', 'error', 'empty', 'unavailable'] as const)(
    'renders the %s state without selectable stale payments',
    state => {
      if (state === 'loading') source.loading = true;
      if (state === 'error') source.error = new Error('Unavailable');
      if (state === 'empty') source.data!.content = [];
      if (state === 'unavailable') source.data = undefined;
      renderSelector();
      expect(screen.queryByRole('radio')).not.toBeInTheDocument();
      if (state === 'loading')
        expect(screen.getByRole('status')).toHaveTextContent(label('loading'));
      else if (state === 'error')
        expect(screen.getByRole('alert')).toHaveTextContent(label('loadPaymentsError'));
      else expect(screen.getByText(label('noPayments'))).toBeInTheDocument();
    }
  );

  it('pages in both directions, bounds navigation and resets the page when searching', () => {
    source.data!.totalPages = 2;
    renderSelector();
    const previous = screen.getByRole('button', { name: label('previous') });
    const next = screen.getByRole('button', { name: label('next') });
    expect(previous).toBeDisabled();
    fireEvent.click(next);
    expect(source.query).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
    expect(next).toBeDisabled();
    expect(previous).toBeEnabled();
    fireEvent.click(previous);
    expect(source.query).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 }));
    fireEvent.click(next);
    fireEvent.change(screen.getByRole('textbox', { name: label('searchPayments') }), {
      target: { value: 'Deposit' },
    });
    expect(source.query).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0, keyword: 'Deposit' })
    );
    fireEvent.change(screen.getByRole('textbox', { name: label('searchPayments') }), {
      target: { value: '' },
    });
    expect(source.query).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: undefined }));
  });

  it('keeps back navigation available when a later page loses its results', () => {
    source.data!.totalPages = 2;
    const { rerender } = renderSelector();
    fireEvent.click(screen.getByRole('button', { name: label('next') }));
    source.data = undefined;
    rerender(<PurchasePaymentSelector currency="EUR" onChange={choose} />);
    expect(screen.getByRole('button', { name: label('next') })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: label('previous') }));
    expect(source.query).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 }));
  });

  it('reflects the selected payment and leaves selection controlled by its parent', () => {
    source.data!.content.push({ ...payment, id: 2, description: 'Other payment' });
    renderSelector({ value: 1 });
    const radios = screen.getAllByRole('radio');
    expect(radios[0]).toBeChecked();
    expect(radios[1]).not.toBeChecked();
    fireEvent.click(radios[1]);
    expect(choose).toHaveBeenCalledWith(expect.objectContaining({ id: 2 }));
    expect(radios[0]).toBeChecked();
  });
});
