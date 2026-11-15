import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { LiabilityForm } from '@/components/liabilities/LiabilityForm';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import type { Liability } from '@/types/liability';

const sources = vi.hoisted(() => ({
  accounts: [] as Array<{
    id: number;
    name: string;
    type: string;
    currency: string;
    balance: number;
    ownBalance?: number;
    exactValues?: Record<string, string>;
  }>,
  properties: undefined as
    | undefined
    | Array<{
        id: number;
        name: string;
        isActive?: boolean;
        acquisitionType?: string;
        soldDate?: string;
        mortgageId?: number;
      }>,
  assets: [] as Array<{
    id: number;
    name: string;
    isPhysical: boolean;
    isActive?: boolean;
    acquisitionType?: string;
    soldDate?: string;
  }>,
}));
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => ({ data: sources.accounts }) }));
vi.mock('@/hooks/useAssets', () => ({ useAssets: () => ({ data: sources.assets }) }));
vi.mock('@/hooks/useRealEstate', () => ({ useProperties: () => ({ data: sources.properties }) }));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({
    id,
    value,
    onValueChange,
    disabled,
  }: {
    id: string;
    value: string;
    onValueChange: (value: string) => void;
    disabled?: boolean;
  }) => (
    <select
      id={id}
      value={value}
      onChange={event => onValueChange(event.target.value)}
      disabled={disabled}
    >
      {['EUR', 'USD', 'GBP'].map(currency => (
        <option key={currency}>{currency}</option>
      ))}
    </select>
  ),
}));
vi.mock('@/components/ui/InstitutionSelector', () => ({
  InstitutionSelector: ({
    value,
    onValueChange,
  }: {
    value?: string;
    onValueChange: (value: string) => void;
  }) => (
    <select
      aria-label="Financial Institution"
      value={value}
      onChange={event => onValueChange(event.target.value)}
    >
      <option value="">Unselected</option>
      <option value="__none__">No institution</option>
      <option value="42">Lender</option>
    </select>
  ),
}));
vi.mock('@/components/ui/ExchangeRateDisplay', () => ({ ExchangeRateInline: () => null }));

const existing: Liability = {
  id: 1,
  name: 'Car loan',
  type: 'AUTO_LOAN',
  principal: 10000,
  currentBalance: 5000,
  currency: 'EUR',
  startDate: '2024-01-01',
  createdAt: '2024-01-01',
  updatedAt: '2024-01-01',
};
const submit = vi.fn();
const cancel = vi.fn();

function renderForm(props: Partial<React.ComponentProps<typeof LiabilityForm>> = {}) {
  return renderWithProviders(
    <LiabilityForm onSubmit={submit} onCancel={cancel} isLoading={false} {...props} />
  );
}

function fillRequired() {
  fireEvent.change(screen.getByLabelText(/liability name/i), {
    target: { value: 'New borrowing' },
  });
  fireEvent.change(screen.getByLabelText(/original or approved principal/i), {
    target: { value: '10000' },
  });
}

async function save() {
  fireEvent.click(screen.getByRole('button', { name: /(?:create|update) liability/i }));
  await waitFor(() => expect(submit).toHaveBeenCalledOnce());
}

describe('Liability form decision paths', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    sources.accounts = [];
    sources.properties = undefined;
    sources.assets = [];
  });

  it.each([
    ['2023-12-31', '2025-01-01', false],
    ['2024-01-01', '2025-01-01', true],
    ['2025-01-01', '2025-01-01', true],
    ['2025-01-02', '2025-01-01', false],
    ['2027-01-01', '', true],
    ['', '', true],
  ])(
    'checks amortization %s against start and end %s',
    async (amortizationStartDate, endDate, valid) => {
      renderForm({ liability: { ...existing, amortizationStartDate, endDate } });
      fireEvent.click(screen.getByRole('button', { name: /update liability/i }));
      if (valid) {
        await waitFor(() =>
          expect(submit).toHaveBeenCalledWith(
            expect.objectContaining({
              amortizationStartDate: amortizationStartDate || undefined,
              endDate: endDate || undefined,
            })
          )
        );
      } else {
        expect((await screen.findAllByRole('alert')).length).toBeGreaterThan(0);
        expect(
          screen.getByLabelText(/first principal repayment/i, { selector: 'input' })
        ).toHaveAttribute('aria-invalid', 'true');
        expect(submit).not.toHaveBeenCalled();
      }
    }
  );

  it.each(['', '__none__', '42'])(
    'normalizes institution selection %s in the request',
    async institution => {
      renderForm({ liability: existing });
      fireEvent.change(screen.getByRole('combobox', { name: 'Financial Institution' }), {
        target: { value: institution },
      });
      await save();
      expect(submit).toHaveBeenCalledWith(
        expect.objectContaining({ institutionId: institution === '42' ? 42 : undefined })
      );
    }
  );

  it('omits empty optional details when an older loan has no repayment metadata', async () => {
    renderForm({ liability: existing });
    await save();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        interestRate: undefined,
        endDate: undefined,
        notes: undefined,
        minimumPayment: undefined,
        additionalFees: undefined,
        insurancePercentage: undefined,
      })
    );
  });

  it('prevents editing balance or currency already derived from linked movements', async () => {
    renderForm({ liability: { ...existing, balanceLocked: true } });
    expect(screen.getByLabelText(/current balance/i)).toBeDisabled();
    expect(screen.getByRole('combobox', { name: /currency/i })).toBeDisabled();
    await save();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({ currentBalance: '5000', currency: 'EUR' })
    );
  });

  it.each([
    { balance: -300, ownBalance: -125, expected: '125' },
    { balance: -300, ownBalance: 0, expected: '0' },
    { balance: -300, ownBalance: 30, expected: '0' },
    { balance: -300, expected: '300' },
    {
      balance: 9007199254740992,
      ownBalance: -9007199254740992,
      exactValues: { ownBalance: '-9007199254740993.01' },
      expected: '9007199254740993.01',
    },
  ])('uses the credit-card account own debt, not its net assets: $expected', async account => {
    sources.accounts = [
      { id: 7, name: 'Card', type: 'CREDIT_CARD', currency: 'USD', ...account },
      { id: 8, name: 'Checking', type: 'CHECKING', currency: 'EUR', balance: -400 },
    ];
    renderForm({ initialType: 'CREDIT_CARD' });
    fireEvent.change(screen.getByLabelText(/liability name/i), { target: { value: 'Card debt' } });
    const selector = screen.getByLabelText(/debt already tracked/i);
    expect(within(selector).queryByRole('option', { name: 'Checking' })).not.toBeInTheDocument();
    fireEvent.change(selector, { target: { value: '7' } });
    fireEvent.change(screen.getByLabelText(/credit limit/i), { target: { value: '5000.25' } });
    await save();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        principal: '0',
        currentBalance: account.expected,
        currency: 'USD',
        representedByAccountId: 7,
        creditLimit: '5000.25',
      })
    );
  });

  it('detaches a credit-card source without silently replacing the entered balance', async () => {
    sources.accounts = [
      { id: 7, name: 'Card', type: 'CREDIT_CARD', currency: 'USD', balance: -125 },
    ];
    renderForm({ initialType: 'CREDIT_CARD' });
    fireEvent.change(screen.getByLabelText(/liability name/i), {
      target: { value: 'Independent card' },
    });
    const selector = screen.getByLabelText(/debt already tracked/i);
    fireEvent.change(selector, { target: { value: '7' } });
    fireEvent.change(selector, { target: { value: '' } });
    await save();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({ currentBalance: '125', representedByAccountId: undefined })
    );
  });

  it('keeps an existing card representation immutable', () => {
    renderForm({
      liability: { ...existing, type: 'CREDIT_CARD', representedByAccountId: 7, creditLimit: 1000 },
    });
    expect(screen.getByLabelText(/debt already tracked/i)).toBeDisabled();
    expect(screen.getByLabelText(/credit limit/i)).toHaveValue(1000);
  });

  it('offers only active, owned properties without another mortgage', async () => {
    sources.properties = [
      { id: 1, name: 'Eligible' },
      { id: 2, name: 'Inactive', isActive: false },
      { id: 3, name: 'Planned', acquisitionType: 'PLANNED' },
      { id: 4, name: 'Sold', soldDate: '2024-01-01' },
      { id: 5, name: 'Already mortgaged', mortgageId: 10 },
    ];
    renderForm({ initialType: 'MORTGAGE' });
    fillRequired();
    const selector = screen.getByLabelText(/link to property/i);
    expect(
      within(selector)
        .getAllByRole('option')
        .map(option => option.textContent)
    ).toEqual(['No property linked', 'Eligible']);
    fireEvent.change(selector, { target: { value: '1' } });
    await save();
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ realEstateId: 1 }));
  });

  it('allows clearing a property link and handles a still-loading property list', async () => {
    const { rerender } = renderForm({ initialType: 'MORTGAGE' });
    const selector = screen.getByLabelText(/link to property/i);
    expect(within(selector).getAllByRole('option')).toHaveLength(1);
    sources.properties = [{ id: 1, name: 'House' }];
    rerender(
      <LiabilityForm initialType="MORTGAGE" onSubmit={submit} onCancel={cancel} isLoading={false} />
    );
    fillRequired();
    fireEvent.change(selector, { target: { value: '1' } });
    fireEvent.change(selector, { target: { value: '' } });
    await save();
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ realEstateId: null }));
  });

  it.each([true, false])('only submits a physical asset link while selected (%s)', async keep => {
    sources.assets = [
      { id: 1, name: 'Car', isPhysical: true },
      { id: 2, name: 'Shares', isPhysical: false },
      { id: 3, name: 'Planned car', isPhysical: true, acquisitionType: 'PLANNED' },
      { id: 4, name: 'Sold car', isPhysical: true, soldDate: '2024-01-01' },
    ];
    renderForm();
    fillRequired();
    const selector = screen.getByLabelText(/financed possession/i);
    expect(
      within(selector)
        .getAllByRole('option')
        .map(option => option.textContent)
    ).toEqual(['No linked possession', 'Car']);
    fireEvent.change(selector, { target: { value: '1' } });
    if (!keep) fireEvent.change(selector, { target: { value: '' } });
    await save();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({ financedAssetId: keep ? 1 : undefined })
    );
  });

  it.each(['interestRate', 'insurancePercentage'] as const)(
    'clears %s instead of retaining the old rate',
    async field => {
      renderForm({ liability: { ...existing, interestRate: 5, insurancePercentage: 1 } });
      const label = field === 'interestRate' ? /interest rate/i : /insurance rate/i;
      fireEvent.change(screen.getByLabelText(label), { target: { value: '' } });
      await save();
      expect(submit).toHaveBeenCalledWith(expect.objectContaining({ [field]: undefined }));
    }
  );

  it('rejects notes that exceed the storage limit', async () => {
    renderForm({ liability: existing });
    fireEvent.change(screen.getByLabelText(/notes/i), { target: { value: 'x'.repeat(1001) } });
    fireEvent.click(screen.getByRole('button', { name: /update liability/i }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Notes are too long')).toBeInTheDocument();
    expect(submit).not.toHaveBeenCalled();
  });
});
