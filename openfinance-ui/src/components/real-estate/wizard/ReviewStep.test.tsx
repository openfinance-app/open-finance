import { beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { ReviewStep } from '@/components/real-estate/wizard/ReviewStep';
import type { Account } from '@/types/account';
import type { FundingStepState, PropertyStepState } from '@/components/real-estate/wizard/types';

const property: PropertyStepState = {
  name: 'Home',
  address: 'Example address',
  propertyType: 'RESIDENTIAL',
  purchasePrice: '100000',
  currentValue: '100000',
  purchaseDate: '2026-01-01',
  currency: 'EUR',
};
const funding: FundingStepState = {
  source: 'new',
  mortgageName: 'Mortgage',
  loanAmount: '80000',
  interestRate: '3',
  route: 'direct',
  downPaymentAmount: '20000',
  downPaymentAccountId: 1,
};
const account = {
  id: 1,
  name: 'Checking',
  currency: 'EUR',
  balance: 64000,
  ownBalance: 64000,
} as Account;
function value(label: string) {
  return screen.getByText(label, { selector: 'dt' }).nextElementSibling;
}

describe('Purchase effects review', () => {
  beforeEach(() => mockAuthentication());
  it.each(['direct', 'account'] as const)(
    'shows the same net effects for the %s funding route',
    route => {
      renderWithProviders(
        <ReviewStep property={property} funding={{ ...funding, route }} account={account} />
      );
      expect(value('Paid from')).toHaveTextContent('Checking');
      expect(value('Cash change')).toHaveTextContent('-€20,000.00');
      expect(value('Account cash after purchase')).toHaveTextContent('€44,000.00');
      expect(value('Loan balance after purchase')).toHaveTextContent('€80,000.00');
      expect(value('Net worth change')).toHaveTextContent('€0.00');
      expect(screen.queryByRole('status')).not.toBeInTheDocument();
    }
  );
  it('explicitly reviews a deliberately zero valuation and its net-worth decrease', () => {
    renderWithProviders(
      <ReviewStep
        property={{ ...property, currentValue: '0' }}
        funding={funding}
        account={account}
      />
    );
    expect(screen.getByRole('status')).toHaveTextContent('initial value is zero');
    expect(value('Net worth change')).toHaveTextContent('-€100,000.00');
  });

  it.each(['direct', 'account'] as const)(
    'does not preview a second debit for a linked payment on the %s route',
    route => {
      renderWithProviders(
        <ReviewStep
          property={property}
          funding={{ ...funding, route, paymentMode: 'existing', existingPaymentId: 9 }}
          account={account}
        />
      );
      expect(value('Payment from account')).toHaveTextContent('€0.00');
      expect(value('Cash change')).toHaveTextContent(route === 'direct' ? '€0.00' : '€80,000.00');
      expect(value('Net worth change')).toHaveTextContent(
        route === 'direct' ? '€20,000.00' : '€100,000.00'
      );
    }
  );
  it('reuses a receipt without previewing another credit or debt increase', () => {
    renderWithProviders(
      <ReviewStep
        property={property}
        funding={{
          ...funding,
          source: 'existing',
          route: 'account',
          loanAlreadyReceived: true,
          existingLoanReceiptId: 12,
        }}
        account={account}
        mortgage={
          { currentBalance: 80000, currency: 'EUR' } as import('@/types/liability').Liability
        }
      />
    );
    expect(value('Cash change')).toHaveTextContent('-€100,000.00');
    expect(value('Loan balance after purchase')).toHaveTextContent('€80,000.00');
    expect(value('Net worth change')).toHaveTextContent('€0.00');
  });
});
