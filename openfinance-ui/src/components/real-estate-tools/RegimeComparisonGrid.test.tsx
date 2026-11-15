import type { ComponentProps } from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { RegimeComparisonGrid } from '@/components/real-estate-tools/RegimeComparisonGrid';
import { calculateAllRegimes } from '@/utils/taxRegimeCalculations';
import { createInvestmentInputs } from '@/test/simulation-fixtures';
import type { TaxRegime } from '@/types/realEstateTools';
import i18n from '@/test/i18n-test';

vi.mock('@/components/real-estate-tools/RegimeCard', () => ({
  RegimeCard: ({
    regime,
    isOpen,
    onToggle,
  }: ComponentProps<typeof import('@/components/real-estate-tools/RegimeCard').RegimeCard>) => (
    <button aria-expanded={isOpen} onClick={onToggle}>
      {regime}
    </button>
  ),
}));
beforeEach(() => mockAuthentication());
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
const results = calculateAllRegimes(createInvestmentInputs());
const lookup = {
  micro_foncier: results.microFoncier,
  reel_foncier: results.reelFoncier,
  lmnp_reel: results.lmnpReel,
  micro_bic: results.microBic,
};
const props = {
  results,
  recommendedRegime: 'lmnp_reel' as TaxRegime,
  eligibleRegimes: ['lmnp_reel', 'micro_bic'] as TaxRegime[],
  getRegimeResult: (regime: TaxRegime) => lookup[regime],
  isRegimeEligible: (regime: TaxRegime) => lookup[regime].eligible,
};

it('expands paired tax regimes together on wide screens and collapses all after recalculation', async () => {
  vi.stubGlobal('innerWidth', 1400);
  const { rerender } = renderWithProviders(<RegimeComparisonGrid {...props} />);
  const user = userEvent.setup();
  for (const [first, second] of [
    ['micro_foncier', 'reel_foncier'],
    ['lmnp_reel', 'micro_bic'],
  ]) {
    await user.click(screen.getByRole('button', { name: first }));
    expect(screen.getByRole('button', { name: second })).toHaveAttribute('aria-expanded', 'true');
    await user.click(screen.getByRole('button', { name: second }));
    expect(screen.getByRole('button', { name: first })).toHaveAttribute('aria-expanded', 'false');
  }
  await user.click(screen.getByRole('button', { name: 'micro_bic' }));
  rerender(<RegimeComparisonGrid {...props} forceCollapse={1} />);
  expect(screen.queryAllByRole('button', { expanded: true })).toHaveLength(0);
});
it('keeps mobile cards independent and exports the complete calculation results', async () => {
  vi.stubGlobal('innerWidth', 375);
  let blob: Blob | undefined;
  vi.stubGlobal(
    'URL',
    Object.assign(URL, {
      createObjectURL: vi.fn((value: Blob) => {
        blob = value;
        return 'blob:regimes';
      }),
      revokeObjectURL: vi.fn(),
    })
  );
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  renderWithProviders(<RegimeComparisonGrid {...props} />);
  const user = userEvent.setup();
  await user.click(screen.getByRole('button', { name: 'micro_foncier' }));
  expect(screen.getByRole('button', { name: 'reel_foncier' })).toHaveAttribute(
    'aria-expanded',
    'false'
  );
  await user.click(
    screen.getByRole('button', { name: i18n.t('realEstate:regimeGrid.exportResults') })
  );
  expect(click).toHaveBeenCalledOnce();
  expect(blob?.type).toBe('application/json');
  const text = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = reject;
    reader.readAsText(blob!);
  });
  expect(JSON.parse(text).regimes).toEqual(results);
});

it.each(['micro_foncier', 'reel_foncier', 'lmnp_reel', 'micro_bic', null] as const)(
  'shows the recommended regime without changing eligibility: %s',
  recommended => {
    renderWithProviders(<RegimeComparisonGrid {...props} recommendedRegime={recommended} />);
    const labels = {
      micro_foncier: 'regimeNameMicroFoncier',
      reel_foncier: 'regimeNameReelFoncier',
      lmnp_reel: 'regimeNameLmnpReel',
      micro_bic: 'regimeNameMicroBic',
    };
    const summary = screen.getByText(
      i18n.t('realEstate:regimeGrid.recommendedRegime')
    ).parentElement!;
    expect(summary).toHaveTextContent(
      recommended ? i18n.t(`realEstate:regimeGrid.${labels[recommended]}`) : '-'
    );
    expect(screen.getByText('2/4')).toBeVisible();
  }
);

it.each(['micro_foncier', 'reel_foncier', 'lmnp_reel', 'micro_bic'] as const)(
  'keeps the mobile %s panel independent when opening and closing it',
  regime => {
    vi.stubGlobal('innerWidth', 375);
    renderWithProviders(<RegimeComparisonGrid {...props} />);
    const button = screen.getByRole('button', { name: regime });
    fireEvent.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getAllByRole('button', { expanded: true })).toHaveLength(1);
    fireEvent.click(button);
    expect(screen.queryAllByRole('button', { expanded: true })).toHaveLength(0);
  }
);

it('does not invent returns when no regime is eligible', () => {
  renderWithProviders(
    <RegimeComparisonGrid
      {...props}
      recommendedRegime={null}
      eligibleRegimes={[]}
      isRegimeEligible={() => false}
    />
  );
  expect(screen.getByText('0/4')).toBeVisible();
  for (const key of ['bestCashFlow', 'bestNetYield']) {
    expect(
      within(screen.getByText(i18n.t(`realEstate:regimeGrid.${key}`)).parentElement!).getByText('-')
    ).toBeVisible();
  }
});

it('handles missing calculations without displaying infinite returns', () => {
  renderWithProviders(<RegimeComparisonGrid {...props} getRegimeResult={() => null} />);
  expect(screen.queryAllByRole('button', { expanded: false })).toHaveLength(0);
  for (const key of ['bestCashFlow', 'bestNetYield']) {
    expect(
      within(screen.getByText(i18n.t(`realEstate:regimeGrid.${key}`)).parentElement!).getByText('-')
    ).toBeVisible();
  }
  expect(screen.getByRole('table')).not.toHaveTextContent('Infinity');
});

it('selects and displays the best exact cash flow without rounding the comparison through a number', () => {
  const exactLookup = {
    ...lookup,
    lmnp_reel: {
      ...lookup.lmnp_reel,
      performance: {
        ...lookup.lmnp_reel.performance,
        monthlyCashFlow: 9007199254740992,
        exactValues: { monthlyCashFlow: '9007199254740993.01' },
      },
    },
    micro_bic: {
      ...lookup.micro_bic,
      performance: {
        ...lookup.micro_bic.performance,
        monthlyCashFlow: 9007199254740992,
        exactValues: { monthlyCashFlow: '9007199254740993.02' },
      },
    },
  };
  renderWithProviders(
    <RegimeComparisonGrid
      {...props}
      isRegimeEligible={regime => regime === 'lmnp_reel' || regime === 'micro_bic'}
      getRegimeResult={regime => exactLookup[regime]}
    />
  );
  const summary = screen.getByText(i18n.t('realEstate:regimeGrid.bestCashFlow')).parentElement!;
  expect(within(summary).getByText('€9,007,199,254,740,993.02')).toBeVisible();
  expect(within(screen.getByRole('table')).getByText('€9,007,199,254,740,993.01')).toBeVisible();
});

it('keeps unavailable cash-flow and yield metrics blank instead of presenting infinite returns', () => {
  const unavailable = {
    ...lookup.micro_foncier,
    performance: { ...lookup.micro_foncier.performance, monthlyCashFlow: null, netYield: null },
  };
  renderWithProviders(<RegimeComparisonGrid {...props} getRegimeResult={() => unavailable} />);
  for (const key of ['bestCashFlow', 'bestNetYield']) {
    expect(
      within(screen.getByText(i18n.t(`realEstate:regimeGrid.${key}`)).parentElement!).getByText('-')
    ).toBeVisible();
  }
});
