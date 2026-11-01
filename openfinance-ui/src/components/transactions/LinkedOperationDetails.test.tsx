import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LinkedOperationDetails } from '@/components/transactions/LinkedOperationDetails';
import { LinkedOperationHint } from '@/components/transactions/LinkedOperationHint';
import { useAsset } from '@/hooks/useAssets';
import { useLiability } from '@/hooks/useLiabilities';
import { useProperty } from '@/hooks/useRealEstate';
import { getTransferSource } from '@/hooks/useTransactions';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import type { Transaction } from '@/types/transaction';

vi.mock('@/hooks/useAssets', () => ({ useAsset: vi.fn() }));
vi.mock('@/hooks/useLiabilities', () => ({ useLiability: vi.fn() }));
vi.mock('@/hooks/useRealEstate', () => ({ useProperty: vi.fn() }));
vi.mock('@/hooks/useTransactions', () => ({ getTransferSource: vi.fn() }));

const base: Transaction = {
  id: 1,
  userId: 1,
  accountId: 10,
  type: 'EXPENSE',
  amount: 377.65,
  currency: 'EUR',
  date: '2026-06-24',
  isReconciled: false,
  createdAt: '2026-06-24',
};
const investment: Transaction = {
  ...base,
  assetId: 8,
  assetName: 'Bitcoin',
  investmentQuantity: -0.0037765,
  investmentCash: false,
  investmentValue: 377.65,
  investmentPrice: 100000,
  investmentCurrency: 'EUR',
  investmentPriceDate: '2026-06-24',
  investmentPriceSource: 'HISTORICAL_CLOSE',
  exactValues: {
    investmentQuantity: '-0.003776500000000001',
    investmentPrice: '100000.00000000001',
  },
};

function renderDetails(transaction: Transaction = investment) {
  const onNavigate = vi.fn();
  renderWithProviders(
    <dl>
      <LinkedOperationDetails transaction={transaction} onNavigate={onNavigate} />
    </dl>
  );
  return { onNavigate };
}

beforeEach(() => {
  vi.resetAllMocks();
  mockAuthentication();
  localStorage.setItem('open_finance_amounts_visible', 'true');
  vi.mocked(useAsset).mockReturnValue({
    data: { id: 8, name: 'Bitcoin', currency: 'EUR', currentValue: 72494 },
  } as ReturnType<typeof useAsset>);
  vi.mocked(useLiability).mockReturnValue({
    data: { id: 4, name: 'Home mortgage', currency: 'USD', currentBalance: 100000 },
  } as ReturnType<typeof useLiability>);
  vi.mocked(useProperty).mockReturnValue({ data: { id: 9, name: 'Family home' } } as ReturnType<
    typeof useProperty
  >);
});

describe('Linked operation display', () => {
  it('keeps ordinary transactions free of hints and empty detail sections', () => {
    renderWithProviders(
      <>
        <LinkedOperationHint transaction={base} />
        <LinkedOperationDetails transaction={base} onNavigate={vi.fn()} />
      </>
    );
    expect(screen.queryByRole('region', { name: 'Linked operation' })).not.toBeInTheDocument();
    expect(screen.queryByTitle(/Linked operation:/)).not.toBeInTheDocument();
  });

  it('shows a compact holding-funded transfer hint without fetching linked entities', () => {
    renderWithProviders(
      <LinkedOperationHint transaction={{ ...investment, transferId: 'transfer-1' }} />
    );
    expect(screen.getByText('Holding-funded transfer · Bitcoin')).toBeInTheDocument();
    expect(useAsset).not.toHaveBeenCalled();
    expect(useLiability).not.toHaveBeenCalled();
    expect(getTransferSource).not.toHaveBeenCalled();
  });

  it.each([
    [{ liabilityId: 4, movementType: 'DISBURSEMENT', type: 'INCOME' }, 'Loan disbursement'],
    [{ liabilityId: 4, movementType: 'INTEREST' }, 'Interest payment'],
    [{ liabilityId: 4, movementType: 'INSURANCE' }, 'Insurance payment'],
    [{ liabilityId: 4, movementType: 'FEE' }, 'Fee'],
    [{ liabilityId: 4, movementType: 'REPAYMENT' }, 'Loan payment'],
    [{ realEstateId: 9, movementType: 'MAINTENANCE' }, 'Maintenance expense'],
    [{ type: 'INCOME', movementType: 'INTEREST' }, 'Interest received'],
  ] satisfies [Partial<Transaction>, string][])(
    'describes the recorded classification %j accurately',
    (fields, label) => {
      renderWithProviders(<LinkedOperationHint transaction={{ ...base, ...fields }} />);
      expect(screen.getByText(label)).toBeInTheDocument();
    }
  );

  it('groups the linked item, exact quantity and recorded price under the operation', () => {
    const { onNavigate } = renderDetails();
    const section = screen.getByText('Operation').nextElementSibling!;
    expect(within(section).getByText('Units withdrawn')).toBeInTheDocument();
    expect(within(section).getByText('0.003776500000000001')).toBeInTheDocument();
    expect(within(section).getByText('100,000.00000000001 EUR')).toBeInTheDocument();
    expect(within(section).getByText('Estimated price per unit')).toBeInTheDocument();
    expect(within(section).queryByText('Price source')).not.toBeInTheDocument();
    expect(within(section).queryByText('Recorded investment value')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Linked operation' })).not.toBeInTheDocument();
    const link = within(section).getByRole('link', { name: 'Bitcoin' });
    expect(link).toHaveAttribute('href', '/assets?highlight=8');
    fireEvent.click(link);
    expect(onNavigate).toHaveBeenCalledOnce();
  });

  it('masks recorded quantities and prices when privacy is enabled', () => {
    localStorage.setItem('open_finance_amounts_visible', 'false');
    renderDetails();
    for (const value of ['0.003776500000000001', '100,000.00000000001 EUR']) {
      expect(screen.getByText(value)).toHaveAttribute('aria-hidden', 'true');
    }
  });

  it('retains a recorded investment value only when it differs from the main transaction amount', () => {
    renderDetails({ ...investment, investmentValue: 410, investmentCurrency: 'USD' });
    expect(screen.getByText('Recorded investment value')).toBeInTheDocument();
    expect(screen.getByText('410 USD')).toBeInTheDocument();
  });

  it('keeps a different quote date alongside the unit price', () => {
    renderDetails({ ...investment, investmentPriceDate: '2026-06-23' });
    expect(screen.getByTitle('Price date')).toHaveTextContent('06/23/2026');
  });

  it('shows added units for an acquisition without inventing a market quote', () => {
    renderDetails({
      ...base,
      type: 'INCOME',
      assetId: 8,
      assetName: 'Bitcoin',
      investmentQuantity: 0.00453915,
      investmentCash: false,
    });
    expect(screen.getByText('Investment units received')).toBeInTheDocument();
    expect(screen.getByText('Units added')).toBeInTheDocument();
    expect(screen.getByText('0.00453915')).toBeInTheDocument();
    expect(screen.queryByText('Recorded price per unit')).not.toBeInTheDocument();
  });

  it('shows the loan link and principal in the loan currency, not the account currency', () => {
    renderDetails({ ...base, liabilityId: 4, movementType: 'REPAYMENT', principalAmount: 800 });
    expect(screen.getByRole('link', { name: 'Home mortgage' })).toHaveAttribute(
      'href',
      '/liabilities?highlight=4'
    );
    expect(screen.getByText('800 USD')).toBeInTheDocument();
    expect(screen.queryByText('800 EUR')).not.toBeInTheDocument();
  });

  it('links property maintenance to the property details', () => {
    renderDetails({ ...base, realEstateId: 9, movementType: 'MAINTENANCE' });
    expect(screen.getByRole('link', { name: 'Family home' })).toHaveAttribute(
      'href',
      '/real-estate?highlight=9'
    );
    expect(screen.getByText('Maintenance expense')).toBeInTheDocument();
    expect(screen.queryByText(/Its value is unchanged/)).not.toBeInTheDocument();
  });

  it('keeps a navigable fallback when a linked item name is unavailable', () => {
    vi.mocked(useProperty).mockReturnValue({ data: undefined } as ReturnType<typeof useProperty>);
    renderDetails({ ...base, realEstateId: 9, movementType: 'MAINTENANCE' });
    expect(screen.getByRole('link', { name: 'Property #9' })).toHaveAttribute(
      'href',
      '/real-estate?highlight=9'
    );
  });

  it('resolves funding on a received transfer without assigning units to its destination', async () => {
    vi.mocked(getTransferSource).mockResolvedValue({
      ...investment,
      transferId: 'transfer-1',
      toAccountName: 'Polymarket',
    });
    renderDetails({
      ...base,
      id: 2,
      type: 'INCOME',
      accountId: 30,
      transferId: 'transfer-1',
      toAccountName: 'BTC',
    });
    expect(await screen.findByText('Holding-funded transfer')).toBeInTheDocument();
    expect(screen.getByText('Units withdrawn')).toBeInTheDocument();
    expect(screen.queryByText('Units added')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Bitcoin' })).toHaveAttribute(
      'href',
      '/assets?highlight=8'
    );
    expect(getTransferSource).toHaveBeenCalledWith('transfer-1');
  });

  it('does not claim cash funding when the transfer source is unavailable and allows retry', async () => {
    vi.mocked(getTransferSource).mockRejectedValueOnce(new Error('Unavailable'));
    renderDetails({ ...base, type: 'INCOME', transferId: 'transfer-1' });
    expect(
      await screen.findByText('The transfer funding details could not be loaded.')
    ).toBeInTheDocument();
    expect(screen.queryByText(/Cash is withdrawn/)).not.toBeInTheDocument();
    vi.mocked(getTransferSource).mockResolvedValueOnce({ ...investment, transferId: 'transfer-1' });
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(screen.getByText('Holding-funded transfer')).toBeInTheDocument());
  });
});
