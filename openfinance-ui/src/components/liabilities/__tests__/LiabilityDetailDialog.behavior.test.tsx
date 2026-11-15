import { fireEvent, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LiabilityDetailDialog } from '@/components/liabilities/LiabilityDetailDialog';
import type {
  AmortizationSchedule,
  Liability,
  LiabilityBreakdown,
  LiabilityTranche,
} from '@/types/liability';
import type { Transaction } from '@/types/transaction';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { useViewStateStore } from '@/stores/viewState';
import { getToday } from '@/utils/date';
import i18n from '@/test/i18n-test';

const state = vi.hoisted(() => ({
  breakdown: undefined as
    | (LiabilityBreakdown & { exactValues?: Record<string, string> })
    | undefined,
  transactions: [] as Transaction[],
  tranches: [] as LiabilityTranche[],
  loading: false,
  error: false,
  schedule: undefined as AmortizationSchedule | undefined,
  scheduleLoading: false,
  scheduleError: false,
  retry: vi.fn(),
  scheduleQuery: vi.fn(),
}));
vi.mock('@/hooks/useLiabilities', async original => ({
  ...(await original<typeof import('@/hooks/useLiabilities')>()),
  useLiabilityBreakdown: () => ({ data: state.breakdown }),
  useLiabilityTransactions: () => ({
    data: state.transactions,
    isLoading: state.loading,
    error: state.error,
  }),
  useAmortizationSchedule: (liability: Liability | null) => {
    state.scheduleQuery(liability);
    return {
      data: state.schedule,
      isLoading: state.scheduleLoading,
      error: state.scheduleError,
      refetch: state.retry,
    };
  },
}));
vi.mock('@/hooks/useTranches', async original => ({
  ...(await original<typeof import('@/hooks/useTranches')>()),
  useTranches: () => ({ data: state.tranches }),
}));
vi.mock('@/components/liabilities/RecordLoanPayment', () => ({
  RecordLoanPayment: ({ liability }: { liability: Liability }) => (
    <button>Record payment {liability.id}</button>
  ),
}));
vi.mock('@/components/liabilities/LiabilityBreakdownPanel', () => ({
  LiabilityBreakdownPanel: () => <section aria-label="Cost breakdown" />,
}));
vi.mock('@/components/liabilities/AmortizationSchedule', () => ({
  AmortizationSchedule: () => <section aria-label="Payment schedule" />,
}));
vi.mock('@/components/liabilities/TrancheDrawdownsTab', () => ({
  TrancheDrawdownsTab: () => <section aria-label="Drawdowns" />,
}));
vi.mock('@/components/assets/AssetFinancingSection', () => ({
  AssetFinancingSection: ({ liabilityId }: { liabilityId: number }) => (
    <section aria-label={`Financing ${liabilityId}`} />
  ),
}));
vi.mock('@/components/attachments', () => ({
  AttachmentList: () => <section aria-label="Attachments" />,
  AttachmentUpload: () => <button>Upload attachment</button>,
}));
vi.mock('@/context/AuthContext', async original => ({
  ...(await original<typeof import('@/context/AuthContext')>()),
  useAuthContext: () => ({ baseCurrency: 'EUR' }),
}));

const debt: Liability = {
  id: 1,
  name: 'Home loan',
  type: 'MORTGAGE',
  principal: 1000,
  currentBalance: 500,
  currency: 'EUR',
  startDate: '2025-01-01',
  createdAt: '',
  updatedAt: '',
};
const breakdown: LiabilityBreakdown = {
  liabilityId: 1,
  name: 'Home loan',
  currency: 'EUR',
  currentBalance: 500,
  principal: 1000,
  principalPaid: 500,
  interestPaid: 50,
  insurancePaid: 20,
  feesPaid: 10,
  totalPaid: 580,
  projectedInterest: 40,
  projectedInsurance: 30,
  projectedFees: 0,
  totalProjectedCost: 570,
  linkedTransactionCount: 1,
  linkedTransactionsTotalAmount: 580,
};
const schedule: AmortizationSchedule = {
  liabilityId: 1,
  liabilityName: 'Home loan',
  principal: 1000,
  interestRate: 4,
  termMonths: 12,
  monthlyPayment: 100,
  totalPayments: 12,
  totalInterest: 200,
  totalAmount: 1200,
  currency: 'EUR',
  payments: [],
};
const transaction = (id: number, changes: Partial<Transaction> = {}): Transaction => ({
  id,
  accountId: 2,
  type: 'EXPENSE',
  amount: 100,
  currency: 'EUR',
  date: '2025-01-01',
  description: `Payment ${id}`,
  isReconciled: false,
  createdAt: '',
  updatedAt: '',
  ...changes,
});
const tranche = (changes: Partial<LiabilityTranche> = {}): LiabilityTranche => ({
  id: 3,
  liabilityId: 1,
  trancheNo: 2,
  plannedAmount: 100,
  drawnAmount: 100,
  remaining: 0,
  status: 'DRAWN',
  currency: 'EUR',
  ...changes,
});
const label = (key: string, values = {}) => i18n.t(key, { ns: 'liabilities', ...values });
const mount = (
  changes: Partial<Liability> = {},
  initialTab:
    | 'overview'
    | 'schedule'
    | 'payments'
    | 'assets'
    | 'drawdowns'
    | 'attachments' = 'overview'
) =>
  renderWithProviders(
    <LiabilityDetailDialog
      liability={{ ...debt, ...changes }}
      initialTab={initialTab}
      onClose={vi.fn()}
    />
  );

describe('Liability details preserve recorded payments and incomplete schedules', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    useViewStateStore.getState().clear();
    Element.prototype.scrollIntoView = vi.fn();
    state.breakdown = { ...breakdown };
    state.transactions = [];
    state.tranches = [];
    state.loading = false;
    state.error = false;
    state.schedule = undefined;
    state.scheduleLoading = false;
    state.scheduleError = false;
  });

  it('renders no dialog without a selected liability and does not request its schedule', () => {
    renderWithProviders(<LiabilityDetailDialog liability={null} onClose={vi.fn()} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(state.scheduleQuery).toHaveBeenCalledWith(null);
  });

  it.each(['overview', 'payments', 'drawdowns'] as const)(
    'explains account-backed balances on the %s tab and hides duplicate payment tools',
    tab => {
      mount({ representedByAccountId: 8 }, tab);
      expect(screen.getByText(label('form.accountSource'))).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Record payment 1' })).not.toBeInTheDocument();
      expect(
        screen.queryByRole('button', { name: label('detail.schedule') })
      ).not.toBeInTheDocument();
      expect(state.scheduleQuery).toHaveBeenCalledWith(null);
    }
  );

  it.each(['assets', 'drawdowns', 'attachments'] as const)(
    'opens the %s tools for the selected liability',
    tab => {
      mount({}, tab);
      if (tab === 'assets')
        expect(screen.getByRole('region', { name: 'Financing 1' })).toBeInTheDocument();
      if (tab === 'drawdowns')
        expect(screen.getByRole('region', { name: 'Drawdowns' })).toBeInTheDocument();
      if (tab === 'attachments')
        expect(screen.getByRole('button', { name: 'Upload attachment' })).toBeInTheDocument();
    }
  );

  it.each(['missing', 'unavailable-projection'])(
    'omits a misleading lifetime total when breakdown is %s',
    mode => {
      state.breakdown = mode === 'missing' ? undefined : { ...breakdown, totalProjectedCost: null };
      mount();
      expect(screen.queryByText(label('detail.totalLifetimeCost'))).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Record payment 1' })).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: label('detail.payments') }));
      expect(screen.getByRole('button', { name: 'Record payment 1' })).toBeInTheDocument();
    }
  );

  it('treats unavailable projected interest and insurance as absent without hiding paid fees', () => {
    state.breakdown = { ...breakdown, projectedInterest: null, projectedInsurance: null };
    mount();
    expect(screen.getByText(label('detail.totalLifetimeCost')).parentElement).toHaveTextContent(
      '€1,150.00'
    );
    expect(screen.getByText(label('detail.oneTimeFee')).parentElement).toHaveTextContent('€10.00');
  });

  it('preserves exact lifetime totals and rates when converting loan costs', () => {
    state.breakdown = {
      ...breakdown,
      totalPaid: 9007199254740992,
      totalProjectedCost: 0.01,
      exactValues: { totalPaid: '9007199254740993.25', totalProjectedCost: '0.01' },
    };
    mount({
      currency: 'USD',
      isConverted: true,
      exchangeRate: 0.5,
      exactValues: { exchangeRate: '0.5' },
      baseCurrency: 'EUR',
    });
    expect(screen.getByText(label('detail.totalLifetimeCost')).parentElement).toHaveTextContent(
      '€4,503,599,627,370,496.63'
    );
  });

  it('retries a failed schedule without showing stale schedule data', () => {
    state.scheduleError = true;
    state.schedule = schedule;
    mount({}, 'schedule');
    expect(screen.getByRole('alert')).toHaveTextContent(label('detail.scheduleError'));
    expect(screen.queryByRole('region', { name: 'Payment schedule' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: label('detail.retry') }));
    expect(state.retry).toHaveBeenCalledTimes(1);
  });

  it('shows loading feedback while waiting for a schedule', () => {
    state.scheduleLoading = true;
    mount({}, 'schedule');
    expect(screen.getByText(label('detail.loadingSchedule'))).toBeInTheDocument();
  });

  it.each([undefined, 500, 1000])(
    'identifies whether funded principal %s is only a partial commitment',
    fundedAmount => {
      state.schedule = schedule;
      mount({ fundedAmount }, 'schedule');
      expect(screen.getByRole('region', { name: 'Payment schedule' })).toBeInTheDocument();
      if (fundedAmount === 500)
        expect(screen.getByText(label('schedule.drawnBalanceForecast'))).toBeInTheDocument();
      else
        expect(screen.queryByText(label('schedule.drawnBalanceForecast'))).not.toBeInTheDocument();
    }
  );

  it.each(['open', 'today', 'expired', 'planned', 'normal'])(
    'only shows active interest-only phases (%s)',
    phase => {
      state.tranches = [
        tranche({
          status: phase === 'planned' ? 'PLANNED' : 'DRAWN',
          interestOnly: phase !== 'normal',
          interestOnlyUntil:
            phase === 'open' ? null : phase === 'expired' ? '2020-01-01' : getToday(),
        }),
      ];
      mount({}, 'schedule');
      if (phase === 'open')
        expect(screen.getByText(label('schedule.interestOnlyPhaseOpen'))).toBeInTheDocument();
      else if (phase === 'today')
        expect(
          screen.getByText(label('schedule.interestOnlyPhase', { date: getToday() }))
        ).toBeInTheDocument();
      else expect(screen.queryByText(/interest.only phase/i)).not.toBeInTheDocument();
    }
  );

  it('uses the future amortization start when no active interest-only tranche exists', () => {
    mount({ amortizationStartDate: '2099-01-01' }, 'schedule');
    expect(
      screen.getByText(label('schedule.principalStarts', { date: '01/01/2099' }))
    ).toBeInTheDocument();
  });

  it('groups payments and drawdowns and shows allocation, account and fallback description links', () => {
    state.tranches = [tranche()];
    state.transactions = [
      transaction(1, {
        movementType: 'REPAYMENT',
        description: '',
        payee: 'Lender',
        trancheId: 3,
        accountName: 'Current account',
      }),
      transaction(2, {
        movementType: 'DISBURSEMENT',
        principalAllocations: [
          { trancheId: null, amount: 10 },
          { trancheId: 3, amount: 20 },
          { trancheId: 99, amount: 30 },
        ],
      }),
      transaction(3, { description: '', payee: '' }),
    ];
    mount({ convertedFromAccountId: 5, balanceStartDate: '2025-01-01' }, 'payments');
    expect(
      screen.getByRole('region', { name: label('detail.operationGroups.payments') })
    ).toHaveTextContent('Lender');
    const drawdowns = screen.getByRole('region', {
      name: label('detail.operationGroups.drawdowns'),
    });
    expect(drawdowns).toHaveTextContent(label('payments.openingPrincipal'));
    expect(drawdowns).toHaveTextContent('T2');
    expect(drawdowns).toHaveTextContent('#99');
    expect(screen.getByRole('link', { name: 'Current account' })).toHaveAttribute(
      'href',
      '/accounts?highlight=2'
    );
    expect(
      screen.getByRole('link', { name: label('detail.transaction', { id: 3 }) })
    ).toHaveAttribute('href', expect.stringContaining('highlight=3'));
  });

  it('converts exact payment amounts and totals without changing foreign transaction currencies', () => {
    state.breakdown = {
      ...breakdown,
      totalPaid: 9007199254740992,
      exactValues: { totalPaid: '9007199254740993.25' },
    };
    state.transactions = [
      transaction(1, {
        currency: 'USD',
        movementType: 'REPAYMENT',
        amount: 9007199254740992,
        exactValues: { amount: '9007199254740993.25' },
      }),
      transaction(2, { currency: 'GBP', amount: 10, movementType: 'REPAYMENT' }),
    ];
    mount(
      {
        currency: 'USD',
        isConverted: true,
        exchangeRate: 0.5,
        baseCurrency: 'EUR',
        exactValues: { exchangeRate: '0.5' },
      },
      'payments'
    );
    const payments = screen.getByRole('region', { name: label('detail.operationGroups.payments') });
    expect(payments).toHaveTextContent('€4,503,599,627,370,496.63');
    expect(screen.getByText(label('breakdown.totalPaid') + ':').parentElement).toHaveTextContent(
      '€4,503,599,627,370,496.63'
    );
    expect(within(payments).getByText('£10.00')).toBeInTheDocument();
  });
});
