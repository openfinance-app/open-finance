import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import { PeriodSelector } from '@/components/ui/PeriodSelector';
import type { PeriodSelection } from '@/utils/dashboard-period';

function Selector(): React.JSX.Element {
  const [selection, setSelection] = useState<PeriodSelection>({
    period: 'MTD',
  });
  return <PeriodSelector selection={selection} onPeriodChange={setSelection} today="2026-10-17" />;
}

describe('PeriodSelector', () => {
  beforeEach(() => {
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('has eight options with month-to-date selected and no Month / Year picker', () => {
    renderWithProviders(<Selector />);
    const selector = screen.getByRole('group', { name: 'Time period' });
    expect(within(selector).getAllByRole('button')).toHaveLength(8);
    expect(
      screen.getByRole('button', { name: i18n.t('common:periodSelector.presets.MTD') })
    ).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '30D' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '365D' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Month / Year' })).not.toBeInTheDocument();
  });

  it('accepts a future custom end date and preserves it when reopening', () => {
    renderWithProviders(<Selector />);
    fireEvent.click(screen.getByRole('button', { name: 'Custom range' }));
    fireEvent.change(screen.getByLabelText('To', { exact: true }), {
      target: { value: '10/31/2026' },
    });
    fireEvent.change(screen.getByLabelText('From', { exact: true }), {
      target: { value: '10/01/2026' },
    });
    const selected = screen.getByRole('button', { name: '10/01/2026 → 10/31/2026' });
    expect(selected).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(selected);
    fireEvent.click(selected);
    expect(screen.getByLabelText('To', { exact: true })).toHaveValue('10/31/2026');
  });

  it('accepts a wholly future range without accepting reversed or invalid dates', () => {
    renderWithProviders(<Selector />);
    fireEvent.click(screen.getByRole('button', { name: 'Custom range' }));
    fireEvent.change(screen.getByLabelText('To', { exact: true }), {
      target: { value: '12/31/2027' },
    });
    fireEvent.change(screen.getByLabelText('From', { exact: true }), {
      target: { value: '01/01/2027' },
    });
    expect(screen.getByRole('button', { name: '01/01/2027 → 12/31/2027' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
    for (const value of ['12/31/2026', '02/30/2027']) {
      fireEvent.change(screen.getByLabelText('To', { exact: true }), { target: { value } });
      fireEvent.blur(screen.getByLabelText('To', { exact: true }));
      expect(screen.getByLabelText('To', { exact: true })).toHaveValue('12/31/2027');
    }
  });

  it('keeps rolling and month-to-date selections distinct', () => {
    renderWithProviders(<Selector />);
    fireEvent.click(screen.getByRole('button', { name: '30D' }));
    expect(screen.getByRole('button', { name: '30D' })).toHaveAttribute('aria-pressed', 'true');
    expect(
      screen.getByRole('button', { name: i18n.t('common:periodSelector.presets.MTD') })
    ).toHaveAttribute('aria-pressed', 'false');
  });

  it('shows years in the custom period label', () => {
    renderWithProviders(
      <PeriodSelector
        selection={{ period: 'CUSTOM', range: { from: '2025-07-01', to: '2026-07-31' } }}
        onPeriodChange={vi.fn()}
        today="2026-10-17"
      />
    );
    expect(screen.getByRole('button', { name: /2025.*2026/ })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
  });
});
