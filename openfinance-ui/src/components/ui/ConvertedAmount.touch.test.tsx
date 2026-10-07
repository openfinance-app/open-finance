import { describe, it, expect, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { renderWithProviders } from '@/test/test-utils';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';

vi.mock('@/context/CurrencyDisplayContext', () => ({
  CurrencyDisplayProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
  useCurrencyDisplay: () => ({ displayMode: 'base', secondaryCurrency: 'GBP' }),
}));
const amount = (
  <ConvertedAmount
    amount={0.01234567}
    currency="BTC"
    convertedAmount={617.2835}
    baseCurrency="USD"
    exchangeRate={50000}
    isConverted
    secondaryAmount={411.522333}
    secondaryCurrency="GBP"
  />
);

function touchDown(target: HTMLElement) {
  const event = new Event('pointerdown', { bubbles: true });
  Object.defineProperty(event, 'pointerType', { value: 'touch' });
  fireEvent(target, event);
  fireEvent.pointerUp(target);
}

describe('Currency comparison disclosure', () => {
  it('opens on a touch tap even when focus opens Radix before click, without activating a parent', async () => {
    const navigate = vi.fn();
    renderWithProviders(<div onClick={navigate}>{amount}</div>);
    const target = screen.getByText('$617.28').closest('[tabindex]') as HTMLElement;
    touchDown(target);
    fireEvent.focus(target);
    fireEvent.click(target);
    expect(await screen.findByRole('tooltip')).toHaveTextContent('₿0.01234567');
    expect(screen.getByRole('tooltip')).toHaveTextContent('£411.52');
    expect(navigate).not.toHaveBeenCalled();
    touchDown(target);
    fireEvent.click(target);
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument());
  });
  it('supports keyboard focus and activation', async () => {
    const user = userEvent.setup();
    renderWithProviders(amount);
    await user.tab();
    expect(await screen.findByRole('tooltip')).toHaveTextContent('£411.52');
    await user.keyboard('{Enter}');
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument());
    await user.keyboard(' ');
    expect(await screen.findByRole('tooltip')).toHaveTextContent('£411.52');
  });
});
