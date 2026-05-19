import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/test-utils';
import { PeriodSelector } from '@/components/ui/PeriodSelector';

describe('PeriodSelector', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-05-19T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows years in the custom period label', () => {
    renderWithProviders(
      <PeriodSelector selectedPeriod="CUSTOM" onPeriodChange={vi.fn()} />
    );

    expect(
      screen.getByRole('button', {
        name: /2026.*2026/,
      })
    ).toBeInTheDocument();
  });
});