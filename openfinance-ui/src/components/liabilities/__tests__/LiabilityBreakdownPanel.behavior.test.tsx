import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LiabilityBreakdownPanel } from '@/components/liabilities/LiabilityBreakdownPanel';
import type { Liability, LiabilityBreakdown } from '@/types/liability';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';

const state = vi.hoisted(() => ({ data: undefined as LiabilityBreakdown | undefined }));
vi.mock('@/hooks/useLiabilities', () => ({
  useLiabilityBreakdown: () => ({ data: state.data, isLoading: false, error: null }),
}));
vi.mock('@/context/AuthContext', async original => ({
  ...(await original<typeof import('@/context/AuthContext')>()),
  useAuthContext: () => ({ baseCurrency: 'EUR' }),
}));
const debt: Liability = {
  id: 1,
  name: 'Loan',
  type: 'LOAN',
  currency: 'EUR',
  principal: 1000,
  currentBalance: 500,
  startDate: '2025-01-01',
  createdAt: '',
  updatedAt: '',
};
const breakdown: LiabilityBreakdown = {
  liabilityId: 1,
  name: 'Loan',
  currency: 'EUR',
  principal: 1000,
  currentBalance: 500,
  principalPaid: 500,
  interestPaid: 0,
  insurancePaid: 0,
  feesPaid: 0,
  totalPaid: 500,
  projectedInterest: null,
  projectedInsurance: null,
  projectedFees: null,
  totalProjectedCost: null,
  linkedTransactionCount: 1,
  linkedTransactionsTotalAmount: 500,
};
const text = (key: string) => i18n.t(`breakdown.${key}`, { ns: 'liabilities' });
const mount = (changes: Partial<Liability> = {}) =>
  renderWithProviders(<LiabilityBreakdownPanel liability={{ ...debt, ...changes }} />);

describe('Liability breakdown optional costs and exact currencies', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    state.data = { ...breakdown };
  });

  it.each([
    [0, 0, '0.0%'],
    [100, -1, '0.0%'],
    [100, 101, '100.0%'],
    [100, 50, '50.0%'],
  ])(
    'clamps payoff progress with principal %s and paid %s',
    (principal, principalPaid, expected) => {
      state.data = {
        ...breakdown,
        principal: Number(principal),
        principalPaid: Number(principalPaid),
      };
      mount();
      expect(screen.getByText(String(expected))).toBeInTheDocument();
    }
  );

  it('shows an unavailable forecast and omits missing projected amounts', () => {
    mount();
    expect(screen.getByRole('status')).toHaveTextContent(text('forecastUnavailable'));
    expect(screen.queryByText(text('projectedInterest'))).not.toBeInTheDocument();
    expect(screen.queryByText(text('totalProjectedCost'))).not.toBeInTheDocument();
  });

  it('renders zero interest and zero forecast amounts as known values', () => {
    state.data = { ...breakdown, projectedInterest: 0, totalProjectedCost: 0 };
    mount();
    expect(screen.getByText(text('projectedInterest')).parentElement).toHaveTextContent('€0.00');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it.each(['missing', 'unnamed', 'logo', 'no-logo'])(
    'shows institution details only when named (%s)',
    variant => {
      mount({
        institution:
          variant === 'missing'
            ? undefined
            : {
                id: 2,
                name: variant === 'unnamed' ? '' : 'Bank',
                logo: variant === 'logo' ? '/bank.svg' : undefined,
              },
      });
      if (variant === 'missing' || variant === 'unnamed')
        expect(screen.queryByText(text('institution'))).not.toBeInTheDocument();
      else expect(screen.getByText('Bank')).toBeInTheDocument();
      if (variant === 'logo')
        expect(document.querySelector('img')).toHaveAttribute('src', '/bank.svg');
    }
  );

  it.each([false, true])(
    'only displays a linked property when its name is available (%s)',
    named => {
      mount({ linkedPropertyId: 2, linkedPropertyName: named ? 'Home' : undefined });
      if (named) expect(screen.getByText('Home')).toBeInTheDocument();
      else expect(screen.queryByText(text('linkedAsset'))).not.toBeInTheDocument();
    }
  );

  it('shows paid miscellaneous charges separately from principal and forecast costs', () => {
    state.data = { ...breakdown, otherChargesPaid: 12.34 };
    mount();
    expect(screen.getByText(text('otherChargesPaid')).parentElement).toHaveTextContent('€12.34');
  });

  it('omits an unknown monthly insurance cost while retaining its configured one-time fee', () => {
    mount({ insurancePercentage: 0.5, monthlyInsuranceCost: undefined, additionalFees: 12.34 });
    expect(screen.getByText(text('oneTimeFee')).parentElement).toHaveTextContent('€12.34');
    expect(
      screen.queryByText(
        i18n.t('breakdown.annualInsuranceRate', { ns: 'liabilities', rate: '0.50' })
      )
    ).not.toBeInTheDocument();
  });

  it.each([false, true])(
    'preserves exact native and converted amounts throughout the breakdown (converted=%s)',
    converted => {
      const amount = 9007199254740992;
      const fields = [
        'principalPaid',
        'interestPaid',
        'insurancePaid',
        'feesPaid',
        'otherChargesPaid',
        'totalPaid',
        'currentBalance',
        'projectedInterest',
        'projectedInsurance',
        'totalProjectedCost',
      ] as const;
      state.data = {
        ...breakdown,
        ...Object.fromEntries(fields.map(field => [field, amount])),
        exactValues: Object.fromEntries(fields.map(field => [field, '9007199254740993.25'])),
      };
      mount({
        currency: converted ? 'USD' : 'EUR',
        baseCurrency: 'EUR',
        isConverted: converted,
        exchangeRate: 0.5,
        insurancePercentage: 1,
        monthlyInsuranceCost: amount,
        additionalFees: amount,
        exactValues: {
          exchangeRate: '0.5',
          monthlyInsuranceCost: '9007199254740993.25',
          additionalFees: '9007199254740993.25',
        },
      });
      for (const label of [
        'principalPaid',
        'interestPaid',
        'insurancePaid',
        'feesPaid',
        'otherChargesPaid',
        'totalPaid',
        'remainingBalance',
        'projectedInterest',
        'projectedInsurance',
        'totalProjectedCost',
        'oneTimeFee',
      ]) {
        expect(screen.getByText(text(label)).parentElement).toHaveTextContent(
          converted ? '€4,503,599,627,370,496.63' : '€9,007,199,254,740,993.25'
        );
      }
    }
  );
});
