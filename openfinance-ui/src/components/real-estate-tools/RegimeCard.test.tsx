import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { RegimeCard } from '@/components/real-estate-tools/RegimeCard';
import type { RegimeCalculationResult, TaxRegime } from '@/types/realEstateTools';
import { getRegimeDisplayName } from '@/utils/taxRegimeCalculations';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';

const result: RegimeCalculationResult = {
  regime: 'micro_foncier',
  eligible: true,
  investment: {
    totalPrice: 100000,
    annualCreditCost: 6000,
    monthlyCreditPayment: 500,
    detail: { credit: 5500, assurance: 400, fraisBancaires: 100 },
  },
  revenue: {
    gross: 12000,
    net: 9000,
    deduction: 3000,
    taxable: 9000,
    detail: { loyers: 11000, chargesRecup: 1000 },
  },
  charges: { total: 8000, credit: 6000, other: 2000 },
  taxation: {
    regime: 'micro_foncier',
    incomeTax: 1000,
    socialContributions: 500,
    totalTaxes: 1500,
  },
  performance: { monthlyCashFlow: 200, grossYield: 10, netYield: 6 },
  details: { isEligible: true, warnings: [], depreciation: 0 },
};
const text = (key: string) => i18n.t(key, { ns: 'realEstate' });

describe('Rental regime result states', () => {
  beforeEach(() => mockAuthentication());

  it.each(['micro_foncier', 'reel_foncier', 'lmnp_reel', 'micro_bic'] as TaxRegime[])(
    'identifies and toggles %s',
    regime => {
      const toggle = vi.fn();
      renderWithProviders(<RegimeCard regime={regime} result={result} isOpen onToggle={toggle} />);
      fireEvent.click(screen.getByText(getRegimeDisplayName(regime)));
      expect(toggle).toHaveBeenCalledOnce();
      expect(screen.getByText(text('regimeCard.eligible'))).toBeInTheDocument();
      expect(screen.getByRole('row', { name: /Total price/ })).toHaveTextContent('€100,000.00');
    }
  );

  it.each(['needsHouseholdIncome', 'professionalOutOfScope'] as const)(
    'does not display a completed tax calculation for %s',
    calculationStatus => {
      renderWithProviders(
        <RegimeCard
          regime="micro_foncier"
          result={{
            ...result,
            eligible: false,
            details: {
              ...result.details,
              isEligible: false,
              calculationStatus,
              warnings: ['Missing household context', 'Revenue exceeds limit'],
            },
          }}
          isOpen={false}
          onToggle={vi.fn()}
        />
      );
      expect(screen.getByText(text(`taxContext.${calculationStatus}`))).toBeInTheDocument();
      expect(
        screen.getByText('Missing household context, Revenue exceeds limit')
      ).toBeInTheDocument();
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
      expect(
        screen.getByText(
          text(
            calculationStatus === 'needsHouseholdIncome'
              ? 'taxContext.incomplete'
              : 'regimeCard.notEligible'
          )
        )
      ).toBeInTheDocument();
    }
  );

  it('shows recommendation and preserves exact result values for every amount section', () => {
    const exact = '9007199254740992.01';
    const data = {
      ...result,
      investment: {
        ...result.investment,
        exactValues: { totalPrice: exact, annualCreditCost: exact, monthlyCreditPayment: exact },
      },
      revenue: {
        ...result.revenue,
        exactValues: { gross: exact, deduction: '-' + exact, taxable: exact },
      },
      charges: { ...result.charges, exactValues: { credit: exact, other: exact, total: exact } },
      taxation: {
        ...result.taxation,
        exactValues: { incomeTax: exact, socialContributions: exact, totalTaxes: exact },
      },
      performance: { ...result.performance, exactValues: { monthlyCashFlow: exact } },
    };
    renderWithProviders(
      <RegimeCard regime="micro_foncier" result={data} isRecommended isOpen onToggle={vi.fn()} />
    );
    expect(screen.getByText(text('regimeCard.recommended'))).toBeInTheDocument();
    expect(screen.getAllByText('€9,007,199,254,740,992.01')).toHaveLength(13);
    const deduction = screen.getByRole('row', { name: /Deduction/ });
    expect(deduction).toHaveTextContent('-€9,007,199,254,740,992.01');
    expect(deduction).not.toHaveTextContent('--');
  });

  it('renders zero monetary fallbacks for an older complete result without nullable metrics', () => {
    renderWithProviders(
      <RegimeCard
        regime="micro_foncier"
        result={{
          ...result,
          performance: { ...result.performance, monthlyCashFlow: null, netYield: null },
          taxation: {
            ...result.taxation,
            incomeTax: null,
            socialContributions: null,
            totalTaxes: null,
          },
        }}
        isOpen
        onToggle={vi.fn()}
      />
    );
    expect(screen.getAllByText('€0.00')).toHaveLength(4);
    expect(screen.getByText('0.00%')).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(/NaN|Infinity/);
  });

  it('shows negative cash flow as a loss', () => {
    renderWithProviders(
      <RegimeCard
        regime="micro_foncier"
        result={{ ...result, performance: { ...result.performance, monthlyCashFlow: -200 } }}
        isOpen
        onToggle={vi.fn()}
      />
    );
    expect(screen.getByText('-€200.00').closest('p')).toHaveClass('text-red-600');
  });
});
