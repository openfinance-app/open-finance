import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AccountCard } from '@/components/accounts/AccountCard';
import EstimatedInterestCard from '@/components/dashboard/EstimatedInterestCard';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { setDecimalPlacesOverride } from '@/utils/currency';
import { useDecimalPlaces } from '@/context/DecimalPlacesContext';
import type { Account } from '@/types/account';

const projection = 1.4753632849315068;
const settings = { userId: 1, decimalPlacesOverrideEnabled: true, preferredDecimalPlaces: 4 };
vi.mock('@/hooks/useAccounts', () => ({
  useInterestEstimate: () => ({
    data: { estimate: projection, yearToDate: projection, historicalAccumulated: 0 },
  }),
}));
vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({
    data: settings,
    isLoading: false,
  }),
  useUpdateUserSettings: () => ({ mutateAsync: vi.fn() }),
}));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: () => null, secondaryCurrency: null }),
}));

function PrecisionControls() {
  const { setDecimalPlaces, setOverrideEnabled } = useDecimalPlaces();
  return (
    <>
      <button onClick={() => setDecimalPlaces(2)}>Two decimals</button>
      <button onClick={() => setDecimalPlaces(4)}>Four decimals</button>
      <button onClick={() => setOverrideEnabled(false)}>Currency defaults</button>
    </>
  );
}

describe('interest estimate display precision', () => {
  beforeEach(() => {
    mockAuthentication();
    localStorage.setItem('open_finance_number_format', '1,234.56');
    localStorage.setItem(
      'open_finance_decimal_places',
      JSON.stringify({ enabled: true, places: 4 })
    );
  });

  afterEach(() => {
    setDecimalPlacesOverride(null);
    localStorage.removeItem('open_finance_number_format');
    localStorage.removeItem('open_finance_decimal_places');
  });

  it('rounds the calculated amount to four decimals in account and dashboard cards', async () => {
    const account = {
      id: 1,
      name: 'Savings',
      type: 'SAVINGS',
      currency: 'EUR',
      balance: 0,
      isActive: true,
      isInterestEnabled: true,
    } as Account;
    renderWithProviders(
      <>
        <PrecisionControls />
        <section aria-label="Account">
          <AccountCard account={account} onEdit={vi.fn()} onDelete={vi.fn()} />
        </section>
        <section aria-label="Dashboard">
          <EstimatedInterestCard
            period="YTD"
            summary={{
              currency: 'EUR',
              totalEarned: 0,
              totalProjected: projection,
              accounts: [
                {
                  accountId: 1,
                  accountName: 'Savings',
                  interestEarned: 0,
                  projectedInterest: projection,
                },
              ],
            }}
          />
        </section>
      </>
    );
    expect(
      await within(screen.getByRole('region', { name: 'Account' })).findByText('€1.4754')
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('region', { name: 'Dashboard' })).getAllByText('€1.4754')
    ).toHaveLength(2);
    expect(screen.queryByText('€1.4800')).not.toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Two decimals' }));
    expect(
      within(screen.getByRole('region', { name: 'Account' })).getByText('€1.48')
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Four decimals' }));
    expect(
      within(screen.getByRole('region', { name: 'Account' })).getByText('€1.4754')
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Currency defaults' }));
    expect(
      within(screen.getByRole('region', { name: 'Account' })).getByText('€1.48')
    ).toBeInTheDocument();
  });
});
