import type { ComponentProps, ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import { AccountDetailModal } from '@/components/accounts/AccountDetailModal';
import type { AccountForm } from '@/components/accounts/AccountForm';
import type { Account, AccountRequest } from '@/types/account';
import type { BalanceHistoryPoint } from '@/hooks/useAccounts';
import type { AmountDisplayMode } from '@/context/CurrencyDisplayContext';
import type { ConvertedAmountProps } from '@/components/ui/ConvertedAmount';
import { useViewStateStore } from '@/stores/viewState';

type Point = BalanceHistoryPoint & { exactValues?: Record<string, string> };
const state = vi.hoisted(() => ({
  account: vi.fn(),
  history: vi.fn(),
  transactions: vi.fn(),
  update: vi.fn(),
  chart: vi.fn(),
  mode: 'base' as AmountDisplayMode,
  active: true,
  hovered: undefined as Point | undefined,
  label: '2024-01-01' as string | undefined,
}));
vi.mock('@/hooks/useAccounts', () => ({
  useAccount: () => state.account(),
  useAccountBalanceHistory: (id: number, period: string) => state.history(id, period),
  useUpdateAccount: () => ({ mutateAsync: state.update, isPending: false }),
}));
vi.mock('@/hooks/useTransactions', () => ({ useTransactions: () => state.transactions() }));
vi.mock('@/context/CurrencyDisplayContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/CurrencyDisplayContext')>()),
  useCurrencyDisplay: () => ({ displayMode: state.mode, secondaryCurrency: null }),
}));
vi.mock('@/components/accounts/AccountForm', () => ({
  AccountForm: ({ onSubmit }: ComponentProps<typeof AccountForm>) => (
    <section aria-label="Account editor">
      <button
        onClick={() =>
          onSubmit({
            name: 'Updated account',
            type: 'CHECKING',
            currency: 'EUR',
            initialBalance: '12.34',
          })
        }
      >
        Save account
      </button>
    </section>
  ),
}));
vi.mock('@/components/accounts/InterestRateVariationsSection', () => ({
  InterestRateVariationsSection: () => null,
}));
vi.mock('@/components/attachments', () => ({
  AttachmentList: () => null,
  AttachmentUpload: () => null,
}));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: (props: ConvertedAmountProps) => (
    <output aria-label="Monetary details">{JSON.stringify(props)}</output>
  ),
}));
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  LineChart: ({ children, data }: { children: ReactNode; data: unknown }) => {
    state.chart(data);
    return <div aria-label="Balance chart">{children}</div>;
  },
  Tooltip: ({
    content,
  }: {
    content: (props: {
      active: boolean;
      payload: { payload: Point }[];
      label?: string;
    }) => ReactNode;
  }) => (
    <div role="tooltip">
      {content({
        active: state.active,
        payload: state.hovered ? [{ payload: state.hovered }] : [],
        label: state.label,
      })}
    </div>
  ),
  XAxis: ({ tickFormatter }: { tickFormatter: (value: string) => string }) => (
    <span aria-label="Date axis">{tickFormatter('2024-01-01')}</span>
  ),
  YAxis: ({ tickFormatter }: { tickFormatter: (value: number) => string }) => (
    <span aria-label="Amount axis">{tickFormatter(100)}</span>
  ),
  Line: () => null,
  CartesianGrid: () => null,
}));

const account: Account = {
  id: 1,
  userId: 1,
  name: 'Current account',
  type: 'CHECKING',
  currency: 'USD',
  baseCurrency: 'EUR',
  balance: 100,
  ownBalance: 100,
  isActive: true,
  createdAt: '2024-01-01',
};
const point: Point = {
  date: '2024-01-01',
  currency: 'USD',
  balance: 100,
  balanceInBaseCurrency: 90,
  baseCurrency: 'EUR',
  isConverted: true,
};

beforeEach(() => {
  vi.resetAllMocks();
  mockAuthentication();
  useViewStateStore.getState().clear();
  Element.prototype.scrollIntoView = vi.fn();
  state.mode = 'base';
  state.active = true;
  state.hovered = undefined;
  state.label = '2024-01-01';
  state.account.mockReturnValue({ data: account, isLoading: false });
  state.history.mockReturnValue({ data: [point], isLoading: false });
  state.transactions.mockReturnValue({ data: { content: [], totalElements: 0 }, isLoading: false });
});

describe('account history and editing behavior', () => {
  it.each<AmountDisplayMode>(['base', 'both', 'native'])(
    'plots a single denomination correctly in %s mode',
    mode => {
      state.mode = mode;
      renderWithProviders(<AccountDetailModal accountId={1} onClose={vi.fn()} />);
      expect(state.chart).toHaveBeenLastCalledWith([
        expect.objectContaining({ displayBalance: mode === 'native' ? 100 : 90 }),
      ]);
      expect(
        screen.getByText(`History in ${mode === 'native' ? 'USD' : 'EUR'}`)
      ).toBeInTheDocument();
    }
  );

  it('switches mixed-denomination history to one base currency even in native mode', () => {
    state.mode = 'native';
    state.history.mockReturnValue({
      data: [point, { date: '2024-02-01', currency: 'EUR', balance: 50 }],
      isLoading: false,
    });
    renderWithProviders(<AccountDetailModal accountId={1} onClose={vi.fn()} />);
    expect(state.chart).toHaveBeenLastCalledWith([
      expect.objectContaining({ displayBalance: 90 }),
      expect.objectContaining({ displayBalance: 50 }),
    ]);
    expect(screen.getByText('History in EUR')).toBeInTheDocument();
  });

  it('leaves a gap instead of a zero or native-currency value when a historical rate is unavailable', () => {
    state.history.mockReturnValue({
      data: [{ ...point, isConverted: false, balanceInBaseCurrency: undefined }],
      isLoading: false,
    });
    renderWithProviders(<AccountDetailModal accountId={1} onClose={vi.fn()} />);
    expect(state.chart).toHaveBeenLastCalledWith([
      expect.objectContaining({ displayBalance: null }),
    ]);
    expect(
      screen.getByText('Some dates have no exchange rate; those points are omitted.')
    ).toHaveAttribute('role', 'status');
  });

  it('uses the account denomination for older history points without an explicit currency', () => {
    state.account.mockReturnValue({ data: { ...account, baseCurrency: undefined } });
    state.history.mockReturnValue({ data: [{ date: '2024-01-01', balance: 100 }] });
    renderWithProviders(<AccountDetailModal accountId={1} onClose={vi.fn()} />);
    expect(state.chart).toHaveBeenLastCalledWith([
      expect.objectContaining({ displayBalance: 100 }),
    ]);
    expect(screen.getByText('History in USD')).toBeInTheDocument();
  });

  it.each([false, true])(
    'keeps the historical hover empty unless active with a point (active=%s)',
    active => {
      state.active = active;
      if (!active) state.hovered = point;
      renderWithProviders(<AccountDetailModal accountId={1} onClose={vi.fn()} />);
      expect(screen.getByRole('tooltip')).toBeEmptyDOMElement();
    }
  );

  it.each([true, false])(
    'passes exact dated values and both conversions to the historical hover (currency supplied=%s)',
    supplied => {
      state.hovered = {
        ...point,
        currency: supplied ? 'GBP' : undefined,
        exactValues: {
          balance: '9007199254740992.01',
          balanceInBaseCurrency: '8106479329266892.809',
          exchangeRate: '0.9',
          balanceInSecondaryCurrency: '4503599627370496.005',
          secondaryExchangeRate: '0.5',
        },
        secondaryCurrency: 'CHF',
        secondaryExchangeRate: 0.5,
        exchangeRate: 0.9,
      };
      if (!supplied) state.label = undefined;
      renderWithProviders(<AccountDetailModal accountId={1} onClose={vi.fn()} />);
      const props = JSON.parse(
        within(screen.getByRole('tooltip')).getByRole('status', { name: 'Monetary details' })
          .textContent!
      );
      expect(props).toMatchObject({
        amount: '9007199254740992.01',
        currency: supplied ? 'GBP' : 'USD',
        convertedAmount: '8106479329266892.809',
        baseCurrency: 'EUR',
        exchangeRate: '0.9',
        secondaryAmount: '4503599627370496.005',
        secondaryCurrency: 'CHF',
        secondaryExchangeRate: '0.5',
        conversionDate: '2024-01-01',
        showComparisons: true,
      });
    }
  );

  it.each([true, false])(
    'closes the editor only after a successful save (success=%s)',
    async success => {
      if (success) state.update.mockResolvedValue(account);
      else state.update.mockRejectedValue(new Error('Save failed'));
      const onEdit = vi.fn();
      const onClose = vi.fn();
      const user = userEvent.setup();
      renderWithProviders(<AccountDetailModal accountId={1} onClose={onClose} onEdit={onEdit} />);
      await user.click(screen.getByRole('button', { name: /^Edit$/ }));
      await user.click(screen.getByRole('button', { name: 'Save account' }));
      const data: AccountRequest = {
        name: 'Updated account',
        type: 'CHECKING',
        currency: 'EUR',
        initialBalance: '12.34',
      };
      await waitFor(() => expect(state.update).toHaveBeenCalledExactlyOnceWith({ id: 1, data }));
      await waitFor(() =>
        expect(screen.queryByRole('region', { name: 'Account editor' }) !== null).toBe(!success)
      );
      expect(onEdit).toHaveBeenCalledTimes(success ? 1 : 0);
      expect(onClose).not.toHaveBeenCalled();
    }
  );

  it('allows a standalone edit without an optional parent callback', async () => {
    state.update.mockResolvedValue(account);
    const user = userEvent.setup();
    renderWithProviders(<AccountDetailModal accountId={1} onClose={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: /^Edit$/ }));
    await user.click(screen.getByRole('button', { name: 'Save account' }));
    await waitFor(() =>
      expect(screen.queryByRole('region', { name: 'Account editor' })).not.toBeInTheDocument()
    );
  });

  it('keeps converted loan accounts read-only', () => {
    state.account.mockReturnValue({ data: { ...account, liabilityConvertedAt: '2024-06-01' } });
    renderWithProviders(<AccountDetailModal accountId={1} onClose={vi.fn()} />);
    expect(screen.getByRole('button', { name: /^Edit$/ })).toBeDisabled();
  });

  it.each([
    ['Merchant', 'Description', 'Merchant', 'INCOME'],
    ['', 'Description', 'Description', 'EXPENSE'],
    [undefined, undefined, 'Transaction', 'TRANSFER'],
  ])(
    'recent transaction links retain identity with payee %s and description %s',
    (payee, description, title, type) => {
      state.transactions.mockReturnValue({
        data: {
          content: [
            {
              id: 42,
              accountId: 1,
              type,
              amount: 12.34,
              currency: 'USD',
              date: '2024-01-01',
              payee,
              description,
              category: type === 'INCOME' ? { name: 'Salary', icon: '💰' } : undefined,
            },
          ],
          totalElements: 1,
        },
      });
      renderWithProviders(<AccountDetailModal accountId={1} onClose={vi.fn()} />);
      const link = screen.getByText(String(title), { selector: 'p' }).closest('a')!;
      const destination = new URL(link.getAttribute('href')!, window.location.origin);
      expect(destination.pathname).toBe('/transactions');
      expect(destination.searchParams.get('accountId')).toBe('1');
      expect(destination.searchParams.get('highlight')).toBe('42');
      if (type === 'INCOME') expect(link).toHaveTextContent('Salary');
      fireEvent.click(screen.getByRole('button', { name: '1Y' }));
      expect(state.history).toHaveBeenLastCalledWith(1, '1Y');
    }
  );
});
