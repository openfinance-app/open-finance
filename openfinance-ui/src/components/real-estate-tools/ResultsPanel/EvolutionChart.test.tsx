import { render } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import type { BuyRentResults } from '@/types/realEstateTools';

// Capture the options object handed to the chart so we can exercise its formatters.
const mocks = vi.hoisted(() => ({ options: undefined as any }));

vi.mock('react-chartjs-2', () => ({
  Line: (props: { options: unknown }) => {
    mocks.options = props.options;
    return null;
  },
}));

// Force a non-EUR base currency so a hardcoded 'EUR' is detectable.
vi.mock('@/context/AuthContext', () => ({
  useAuthContext: () => ({ baseCurrency: 'USD' }),
}));

import { EvolutionChart } from './EvolutionChart';

const results = {
  years: [
    {
      year: 1,
      buy: { propertyValue: 100000, remainingCapital: 90000 },
      rent: { savings: 5000 },
    },
  ],
} as unknown as BuyRentResults;

describe('EvolutionChart', () => {
  beforeEach(() => {
    mocks.options = undefined;
  });

  it('formats chart amounts with the user baseCurrency instead of a hardcoded EUR', () => {
    render(<EvolutionChart results={results} />);

    const label = mocks.options.plugins.tooltip.callbacks.label({
      dataset: { label: 'Patrimoine' },
      parsed: { y: 1000 },
    } as any);
    const tick = mocks.options.scales.y.ticks.callback(1000 as any);

    // baseCurrency is USD → formatted output must use the USD symbol, not the euro sign.
    expect(label).toContain('$');
    expect(label).not.toContain('€');
    expect(tick).toContain('$');
  });
});
