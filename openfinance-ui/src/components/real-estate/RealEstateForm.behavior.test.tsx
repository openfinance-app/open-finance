import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { RealEstateForm } from '@/components/real-estate/RealEstateForm';
import type { RealEstateProperty } from '@/types/realEstate';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';

vi.mock('@/components/ui/DateInput', () => ({
  DateInput: ({
    id,
    value,
    onChange,
    onBlur,
    max,
    error,
  }: ComponentProps<typeof import('@/components/ui/DateInput').DateInput>) => (
    <>
      <input
        id={id}
        value={value}
        onChange={event => onChange(event.target.value)}
        onBlur={onBlur}
        max={max}
      />
      {error && <p role="alert">{error}</p>}
    </>
  ),
}));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({
    value,
    onValueChange,
  }: {
    value: string;
    onValueChange: (value: string) => void;
  }) => (
    <select id="currency" value={value} onChange={event => onValueChange(event.target.value)}>
      <option>EUR</option>
      <option>USD</option>
      <option>INVALID-CODE</option>
    </select>
  ),
}));
vi.mock('@/components/ui/LiabilitySelector', () => ({
  LiabilitySelector: ({
    value,
    onValueChange,
    liabilityFilter,
  }: ComponentProps<typeof import('@/components/ui/LiabilitySelector').LiabilitySelector>) => (
    <select
      id="mortgageId"
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value ? Number(event.target.value) : undefined)}
    >
      <option value="">No mortgage</option>
      {[
        { id: 4, name: 'Mortgage', type: 'MORTGAGE' as const },
        { id: 5, name: 'Credit card', type: 'CREDIT_CARD' as const },
      ]
        .filter(
          item =>
            !liabilityFilter ||
            liabilityFilter({
              ...item,
              principal: 100,
              currentBalance: 90,
              currency: 'EUR',
              startDate: '2024-01-01',
              createdAt: '',
              updatedAt: '',
            })
        )
        .map(item => (
          <option key={item.id} value={item.id}>
            {item.name}
          </option>
        ))}
    </select>
  ),
}));
vi.mock('@/components/ui/ExchangeRateDisplay', () => ({
  ExchangeRateInline: ({ from, to }: { from: string; to: string }) => (
    <output aria-label="Conversion pair">
      {from}/{to}
    </output>
  ),
}));

const property: RealEstateProperty = {
  id: 1,
  userId: 1,
  name: 'Home',
  address: '1 Main Street',
  propertyType: 'RESIDENTIAL',
  purchasePrice: 100,
  currentValue: 150,
  acquisitionType: 'PURCHASE',
  purchaseDate: '2024-01-01',
  currency: 'EUR',
  allocatedDebt: 0,
  isActive: true,
  createdAt: '',
  updatedAt: '',
};
const submit = vi.fn();
const cancel = vi.fn();
const label = (key: string) => i18n.t(`form.${key}`, { ns: 'realEstate' });
function input(id: string): HTMLElement {
  // Translated labels include required markers; using the label association keeps these queries accessible.
  const labelElement = document.querySelector(`label[for="${id}"]`);
  return screen.getByLabelText(labelElement?.textContent?.trim() ?? id);
}
function change(id: string, value: string) {
  fireEvent.change(input(id), { target: { value } });
}
function renderForm(props: Partial<ComponentProps<typeof RealEstateForm>> = {}) {
  return renderWithProviders(
    <RealEstateForm
      property={property}
      onSubmit={submit}
      onCancel={cancel}
      isLoading={false}
      {...props}
    />
  );
}
function save() {
  fireEvent.click(screen.getByRole('button', { name: /(?:update|create) property/i }));
}
async function saved() {
  save();
  await waitFor(() => expect(submit).toHaveBeenCalledOnce());
}

describe('Property form ownership and optional-data decisions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    submit.mockResolvedValue(undefined);
  });

  it.each(['PURCHASE', 'GIFT', 'PLANNED'] as const)(
    'creates %s with the corresponding financial meaning',
    async acquisitionType => {
      renderForm({ property: undefined, initialAcquisitionType: acquisitionType });
      change('name', 'New home');
      change('address', '2 Main Street');
      change('purchaseDate', '2024-01-01');
      change('purchasePrice', '100.001');
      if (acquisitionType === 'GIFT') {
        change('receivedValue', '500.001');
        change('currentValue', '600.001');
      }
      await saved();
      expect(submit).toHaveBeenCalledWith(
        expect.objectContaining({
          acquisitionType,
          purchasePrice: '100.001',
          receivedValue: acquisitionType === 'GIFT' ? '500.001' : null,
          currentValue:
            acquisitionType === 'PLANNED'
              ? '0'
              : acquisitionType === 'GIFT'
                ? '600.001'
                : '100.001',
          mortgageId: null,
          rentalIncome: null,
          notes: null,
          documents: null,
          latitude: null,
          longitude: null,
          isActive: true,
        })
      );
    }
  );

  it('updates the initial valuation from purchase cost until the user chooses a different value', async () => {
    renderForm({ property: undefined });
    change('name', 'Home');
    change('address', 'Street');
    change('purchaseDate', '2024-01-01');
    change('purchasePrice', '200');
    expect(input('currentValue')).toHaveValue('200');
    change('currentValue', '250');
    change('purchasePrice', '300');
    expect(input('currentValue')).toHaveValue('250');
    await saved();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({ purchasePrice: '300', currentValue: '250' })
    );
  });

  it('does not overwrite an existing valuation when editing acquisition cost', async () => {
    renderForm();
    change('purchasePrice', '200');
    await saved();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({ purchasePrice: '200', currentValue: '150' })
    );
  });

  it('clears current value on switching to planned ownership and accepts a future target date', async () => {
    renderForm();
    change('acquisitionType', 'PLANNED');
    change('purchaseDate', '2099-12-31');
    expect(input('currentValue')).toBeDisabled();
    expect(input('currentValue')).toHaveValue('0');
    expect(input('purchaseDate')).not.toHaveAttribute('max');
    await saved();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        currentValue: '0',
        acquisitionType: 'PLANNED',
        purchaseDate: '2099-12-31',
      })
    );
  });

  it('keeps an existing plan in planned status so ownership must go through purchase review', () => {
    renderForm({ property: { ...property, acquisitionType: 'PLANNED', currentValue: 0 } });
    expect(within(input('acquisitionType')).getAllByRole('option')).toHaveLength(1);
    expect(input('acquisitionType')).toHaveValue('PLANNED');
  });

  it.each([undefined, null, 0, 20])(
    'preserves unknown, zero, and positive gift values (%s) distinctly',
    async receivedValue => {
      renderForm({
        property: { ...property, acquisitionType: 'GIFT', receivedValue, purchasePrice: 0 },
      });
      await saved();
      expect(submit).toHaveBeenCalledWith(
        expect.objectContaining({
          receivedValue: receivedValue == null ? null : String(receivedValue),
          purchasePrice: '0',
        })
      );
    }
  );

  it('preserves exact gift values, documents, zero coordinates and an inactive property', async () => {
    renderForm({
      property: {
        ...property,
        acquisitionType: 'GIFT',
        receivedValue: 9007199254740992,
        exactValues: { receivedValue: '9007199254740992.01' },
        documents: '["deed.pdf"]',
        notes: 'Keep the deed',
        latitude: 0,
        longitude: 0,
        isActive: false,
        mortgageId: 4,
        rentalIncome: 25,
      },
    });
    await saved();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        receivedValue: '9007199254740992.01',
        documents: '["deed.pdf"]',
        notes: 'Keep the deed',
        latitude: 0,
        longitude: 0,
        isActive: false,
        mortgageId: 4,
        rentalIncome: '25',
      })
    );
  });

  it('removes optional details explicitly while retaining attached documents', async () => {
    renderForm({
      property: {
        ...property,
        mortgageId: 4,
        rentalIncome: 100,
        notes: 'Notes',
        latitude: 10,
        longitude: 20,
        documents: '[]',
      },
    });
    for (const id of ['mortgageId', 'rentalIncome', 'notes', 'latitude', 'longitude'])
      change(id, '');
    await saved();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        mortgageId: null,
        rentalIncome: null,
        notes: null,
        latitude: null,
        longitude: null,
        documents: '[]',
      })
    );
  });

  it.each([undefined, null, 0, 10])(
    'normalizes rental income %s without inventing rent',
    async rentalIncome => {
      renderForm({ property: { ...property, rentalIncome, acquisitionType: undefined } });
      await saved();
      expect(submit).toHaveBeenCalledWith(
        expect.objectContaining({
          acquisitionType: 'PURCHASE',
          rentalIncome: rentalIncome ? String(rentalIncome) : null,
        })
      );
    }
  );

  it.each([
    ['name', '', 'validation.nameRequired'],
    ['name', 'x'.repeat(501), 'validation.nameTooLong'],
    ['address', '', 'validation.addressRequired'],
    ['address', 'x'.repeat(1001), 'validation.addressTooLong'],
    ['purchasePrice', '0', 'validation.priceTooSmall'],
    ['currentValue', '-1', 'validation.valueTooSmall'],
    ['receivedValue', '-1', 'validation.priceInvalid'],
    ['rentalIncome', '-1', 'validation.rentalIncomeNonNegative'],
    ['notes', 'x'.repeat(2049), 'validation.notesTooLong'],
    ['latitude', '-91', 'validation.latitudeRange'],
    ['latitude', '91', 'validation.latitudeRange'],
    ['longitude', '-181', 'validation.longitudeRange'],
    ['longitude', '181', 'validation.longitudeRange'],
    ['currency', 'INVALID-CODE', 'validation.currencyCode'],
    ['purchaseDate', '', 'validation.purchaseDateRequired'],
    ['purchaseDate', '01/01/2024', 'validation.invalidDateFormat'],
    ['purchaseDate', '2099-12-31', 'validation.purchaseDateFuture'],
  ])('rejects invalid %s (%s) with a visible error', async (field, value, messageKey) => {
    renderForm({
      property: { ...property, acquisitionType: field === 'receivedValue' ? 'GIFT' : 'PURCHASE' },
    });
    change(field, value);
    save();
    const limit = field === 'latitude' ? 90 : field === 'longitude' ? 180 : 0;
    const isBoundedNumber = [
      'currentValue',
      'receivedValue',
      'rentalIncome',
      'latitude',
      'longitude',
    ].includes(field);
    const message = isBoundedNumber
      ? i18n.t(Number(value) < 0 ? 'input.numberMin' : 'input.numberMax', {
          ns: 'common',
          value: Number(value) < 0 ? -limit : limit,
        })
      : label(messageKey);
    expect((await screen.findAllByText(message)).length).toBeGreaterThan(0);
    expect(submit).not.toHaveBeenCalled();
    const details = input(field).closest('details');
    if (details) expect(details.open).toBe(true);
  });

  it.each([
    [-90, -180],
    [90, 180],
  ])('accepts boundary coordinates %s, %s', async (latitude, longitude) => {
    renderForm();
    change('latitude', String(latitude));
    change('longitude', String(longitude));
    await saved();
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ latitude, longitude }));
  });

  it('offers mortgage liabilities only and lets the user attach one', async () => {
    renderForm();
    expect(
      screen.queryByRole('option', { name: 'Credit card', hidden: true })
    ).not.toBeInTheDocument();
    change('mortgageId', '4');
    await saved();
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ mortgageId: 4 }));
  });

  it('keeps user input after a failed save and supports retry', async () => {
    submit.mockRejectedValueOnce(new Error('Save failed'));
    const error = vi.spyOn(console, 'error').mockImplementation(() => {});
    try {
      renderForm();
      change('name', 'Edited home');
      save();
      await waitFor(() =>
        expect(error).toHaveBeenCalledWith('Failed to save property:', expect.any(Error))
      );
      expect(input('name')).toHaveValue('Edited home');
      save();
      await waitFor(() => expect(submit).toHaveBeenCalledTimes(2));
      expect(submit.mock.calls[1][0]).toMatchObject({ name: 'Edited home' });
    } finally {
      error.mockRestore();
    }
  });

  it('cancels without submitting and disables submission while saving', () => {
    const { rerender } = renderForm();
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(cancel).toHaveBeenCalledOnce();
    expect(submit).not.toHaveBeenCalled();
    rerender(<RealEstateForm property={property} onSubmit={submit} onCancel={cancel} isLoading />);
    expect(screen.getByRole('button', { name: /update property/i })).toBeDisabled();
  });
});
