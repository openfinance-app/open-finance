import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { ExpensesSection } from '@/components/real-estate-tools/InvestmentForm/ExpensesSection';
import { RentalSection } from '@/components/real-estate-tools/BuyRentForm/RentalSection';
import type { OwnerExpensesInputs, RentalInputs } from '@/types/realEstateTools';
import i18n from '@/test/i18n-test';
const expenses: OwnerExpensesInputs = {
  propertyTax: 100,
  nonRecoverableCharges: 100,
  annualMaintenance: 100,
  cfe: 100,
  cvae: 100,
  managementFees: 100,
  pnoInsurance: 100,
  accountingFees: 100,
  marginalTaxRate: 30,
};
const rental: RentalInputs = {
  monthlyRent: 1000,
  monthlyCharges: 100,
  securityDeposit: 1000,
  rentalInsurance: 100,
  garbageTax: 100,
  initialSavings: 500,
  monthlySavings: 50,
};
beforeEach(() => mockAuthentication());
describe('Simulation expense entry', () => {
  it.each(Object.keys(expenses) as (keyof OwnerExpensesInputs)[])(
    'clearing %s removes that expense without propagating NaN',
    field => {
      const update = vi.fn();
      renderWithProviders(
        <ExpensesSection
          inputs={expenses}
          errors={[]}
          onUpdate={update}
          isOpen
          onToggle={vi.fn()}
        />
      );
      fireEvent.change(screen.getByLabelText(i18n.t(`realEstate:expensesSection.${field}`)), {
        target: { value: '' },
      });
      expect(update).toHaveBeenCalledExactlyOnceWith(field, 0);
    }
  );
  it.each([
    'monthlyRent',
    'monthlyCharges',
    'securityDeposit',
    'rentalInsurance',
    'garbageTax',
  ] as const)('clearing renter %s does not change computed savings', field => {
    const update = vi.fn();
    renderWithProviders(
      <RentalSection inputs={rental} errors={[]} onUpdate={update} isOpen onToggle={vi.fn()} />
    );
    fireEvent.change(screen.getByLabelText(i18n.t(`realEstate:rentalSection.${field}`)), {
      target: { value: '' },
    });
    expect(update).toHaveBeenCalledExactlyOnceWith(field, 0);
    expect(
      screen.getByLabelText(i18n.t('realEstate:rentalSection.initialSavings'))
    ).toHaveAttribute('readonly');
    expect(
      screen.getByLabelText(i18n.t('realEstate:rentalSection.monthlySavings'))
    ).toHaveAttribute('readonly');
  });
  it('places supplied tax-rate validation next to the editable rate', () => {
    renderWithProviders(
      <ExpensesSection
        inputs={expenses}
        errors={[
          { field: 'expenses.marginalTaxRate', message: 'Tax rate is outside the supported range' },
        ]}
        onUpdate={vi.fn()}
        isOpen
        onToggle={vi.fn()}
      />
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Tax rate is outside the supported range');
  });
  it('keeps rent validation separate from unrelated purchase validation', () => {
    renderWithProviders(
      <RentalSection
        inputs={rental}
        errors={[
          { field: 'rental.monthlyRent', message: 'Monthly rent is required' },
          { field: 'purchase.propertyPrice', message: 'Unrelated error' },
        ]}
        onUpdate={vi.fn()}
        isOpen
        onToggle={vi.fn()}
      />
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Monthly rent is required');
    expect(screen.queryByText('Unrelated error')).not.toBeInTheDocument();
  });
});
