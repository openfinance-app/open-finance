import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import { PurchaseSection } from '@/components/real-estate-tools/BuyRentForm/PurchaseSection';
import { DEFAULT_BUY_RENT_INPUTS, type PurchaseInputs } from '@/types/realEstateTools';

const derived = {
  totalPrice: 321000,
  borrowedAmount: 261000,
  monthlyPayment: 1400,
  minimumDownPayment: 21000,
};
const fields: [string, string, keyof PurchaseInputs][] = [
  ['Price and Fees', 'Property price', 'propertyPrice'],
  ['Price and Fees', 'Renovation amount', 'renovationAmount'],
  ['Price and Fees', 'Notary fees (%)', 'notaryFeesPercent'],
  ['Price and Fees', 'Agency fees', 'agencyFees'],
  ['Financing', 'Down payment', 'downPayment'],
  ['Financing', 'Loan duration (years)', 'loanDuration'],
  ['Financing', 'Annual rate (%)', 'interestRate'],
  ['Insurance and Fees', 'Total insurance over loan duration', 'totalInsurance'],
  ['Insurance and Fees', 'Application fees', 'applicationFees'],
  ['Insurance and Fees', 'Guarantee fees', 'guaranteeFees'],
  ['Insurance and Fees', 'Total account keeping fees', 'accountFees'],
  ['Recurring charges', 'Annual property tax', 'propertyTax'],
  ['Recurring charges', 'Co-ownership charges/year', 'coOwnershipCharges'],
  ['Recurring charges', 'Maintenance (%/year)', 'maintenancePercent'],
  ['Recurring charges', 'Home insurance/year', 'homeInsurance'],
  ['Recurring charges', 'Additional bank fees/year', 'bankFees'],
  ['Recurring charges', 'Garbage tax/year', 'garbageTax'],
];

beforeEach(() => {
  mockAuthentication();
  Element.prototype.scrollIntoView = vi.fn();
});

describe('purchase inputs', () => {
  it.each(fields)('%s: editing and clearing %s only changes %s', async (section, label, field) => {
    const onUpdate = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <PurchaseSection
        inputs={DEFAULT_BUY_RENT_INPUTS.purchase}
        derivedValues={derived}
        errors={[]}
        onUpdate={onUpdate}
        isOpen
        onToggle={vi.fn()}
      />
    );
    if (section !== 'Price and Fees')
      await user.click(screen.getByRole('button', { name: section }));
    const input = screen.getByRole('textbox', { name: label });
    const edited = field === 'loanDuration' ? '12' : '12.34';
    fireEvent.change(input, { target: { value: edited } });
    expect(onUpdate).toHaveBeenLastCalledWith(field, Number(edited));
    fireEvent.change(input, { target: { value: '' } });
    expect(onUpdate).toHaveBeenLastCalledWith(field, field === 'loanDuration' ? 1 : 0);
    expect(onUpdate).toHaveBeenCalledTimes(2);
  });

  it('reports each validation error next to its own input without showing unrelated errors', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <PurchaseSection
        inputs={DEFAULT_BUY_RENT_INPUTS.purchase}
        derivedValues={derived}
        errors={[
          { field: 'purchase.propertyPrice', message: 'Price must be positive' },
          { field: 'purchase.downPayment', message: 'Cash exceeds purchase cost' },
          { field: 'rental.monthlyRent', message: 'Unrelated rent error' },
        ]}
        onUpdate={vi.fn()}
        isOpen
        onToggle={vi.fn()}
      />
    );
    expect(screen.getByText('Price must be positive')).toBeInTheDocument();
    expect(screen.queryByText('Unrelated rent error')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Financing' }));
    expect(screen.getByText('Cash exceeds purchase cost')).toBeInTheDocument();
  });

  it('updates the property tax exemption and delegates section expansion', async () => {
    const onUpdate = vi.fn();
    const onToggle = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <PurchaseSection
        inputs={DEFAULT_BUY_RENT_INPUTS.purchase}
        derivedValues={derived}
        errors={[]}
        onUpdate={onUpdate}
        isOpen={false}
        onToggle={onToggle}
      />
    );
    await user.click(screen.getByText('Purchase Parameters'));
    expect(onToggle).toHaveBeenCalledOnce();
    await user.click(
      screen.getByRole('switch', { name: 'New property (2-year property tax exemption)' })
    );
    expect(onUpdate).toHaveBeenCalledExactlyOnceWith('isNewProperty', true);
  });
});
