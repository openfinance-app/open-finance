import { useState } from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AmountFilterControls } from '@/components/ui/AmountFilterControls';
import type { AmountFilterValue } from '@/types/amount-filter';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';

function Harness({
  initial = {},
  allowNegative = false,
}: {
  initial?: AmountFilterValue;
  allowNegative?: boolean;
}) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <AmountFilterControls {...value} allowNegative={allowNegative} onChange={setValue} />
      <output aria-label="Selected filters">{JSON.stringify(value)}</output>
    </>
  );
}

describe('Amount comparison controls', () => {
  beforeEach(() => mockAuthentication());

  it('defaults to base currency and preserves bounds when switching to native and back', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness initial={{ min: 2500 }} />);
    const currency = await screen.findByRole('combobox', { name: 'Compare amounts in' });
    expect(currency).toHaveValue('BASE');
    await user.selectOptions(currency, 'NATIVE');
    expect(screen.getByLabelText('Selected filters')).toHaveTextContent(
      '"amountCurrencyMode":"NATIVE"'
    );
    expect(screen.getByLabelText('Min amount')).toHaveValue('2,500');
    await user.selectOptions(currency, 'BASE');
    expect(screen.getByLabelText('Selected filters')).toHaveTextContent('{"min":2500}');
  });

  it('makes exact and range filters mutually exclusive, including zero', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness initial={{ min: 100, max: 500, amountCurrencyMode: 'NATIVE' }} />);
    await user.type(await screen.findByLabelText('Exact amount'), '0');
    expect(screen.getByLabelText('Min amount')).toHaveValue('');
    expect(screen.getByLabelText('Max amount')).toHaveValue('');
    expect(screen.getByLabelText('Selected filters')).toHaveTextContent('"exact":0');
    expect(screen.getByLabelText('Selected filters')).toHaveTextContent(
      '"amountCurrencyMode":"NATIVE"'
    );
    await user.type(screen.getByLabelText('Max amount'), '2500');
    expect(screen.getByLabelText('Exact amount')).toHaveValue('');
    expect(screen.getByLabelText('Selected filters')).toHaveTextContent('"max":2500');
    await user.clear(screen.getByLabelText('Max amount'));
    expect(screen.getByLabelText('Selected filters')).toHaveTextContent(
      '{"amountCurrencyMode":"NATIVE"}'
    );
  });

  it('allows a signed exact balance for overdrawn accounts', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness allowNegative />);
    await user.type(await screen.findByLabelText('Exact amount'), '-60.98');
    expect(screen.getByLabelText('Selected filters')).toHaveTextContent('"exact":-60.98');
  });
});
