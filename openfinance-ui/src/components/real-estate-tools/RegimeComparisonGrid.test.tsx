import type { ComponentProps } from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
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
