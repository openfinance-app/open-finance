import type { ComponentProps } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { RecurringTransactionForm } from '@/components/transactions/RecurringTransactionForm';
import type { RecurringTransaction } from '@/types/recurringTransaction';
import type { Account } from '@/types/account';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';

const source = vi.hoisted(() => ({
  rate: undefined as { rate: number; exactValues?: Record<string, string> } | undefined,
  payees: [] as Array<{ name: string; categoryId?: number }>,
}));
vi.mock('@/context/AuthContext', async original => ({
  ...(await original<typeof import('@/context/AuthContext')>()),
  useAuthContext: () => ({ baseCurrency: 'EUR' }),
}));
vi.mock('@/hooks/useCurrency', () => ({ useLatestExchangeRate: () => ({ data: source.rate }) }));
vi.mock('@/hooks/usePayees', () => ({ useActivePayees: () => ({ data: source.payees }) }));
vi.mock('@/components/ui/DateInput', () => ({
  DateInput: ({
    id,
    value,
    onChange,
    onBlur,
    error,
  }: ComponentProps<typeof import('@/components/ui/DateInput').DateInput>) => (
    <>
      <input
        id={id}
        value={value ?? ''}
        onChange={event => onChange(event.target.value)}
        onBlur={onBlur}
      />
      {error && <p role="alert">{error}</p>}
    </>
  ),
}));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({
    id,
    value,
    onValueChange,
  }: {
    id: string;
    value: string;
    onValueChange: (value: string) => void;
  }) => (
    <select id={id} value={value} onChange={event => onValueChange(event.target.value)}>
      <option>EUR</option>
      <option>USD</option>
      <option>GBP</option>
      <option>BAD-CODE</option>
    </select>
  ),
}));
vi.mock('@/components/ui/AccountSelector', () => ({
  AccountSelector: ({
    id,
    value,
    onValueChange,
  }: ComponentProps<typeof import('@/components/ui/AccountSelector').AccountSelector>) => (
    <select
      id={id}
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value ? Number(event.target.value) : undefined)}
    >
      <option value="">Choose account</option>
      <option value="1">Euro account</option>
      <option value="2">Dollar account</option>
      <option value="99">Removed account</option>
    </select>
  ),
}));
vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: ({
    id,
    value,
    onValueChange,
  }: ComponentProps<typeof import('@/components/ui/CategorySelect').CategorySelect>) => (
    <select
      id={id}
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value ? Number(event.target.value) : undefined)}
    >
      <option value="">No category</option>
      <option value="3">Rent</option>
      <option value="4">Bills</option>
    </select>
  ),
}));
vi.mock('@/components/ui/PayeeSelector', () => ({
  PayeeSelector: ({
    id,
    value,
    onValueChange,
  }: ComponentProps<typeof import('@/components/ui/PayeeSelector').PayeeSelector>) => (
    <input id={id} value={value ?? ''} onChange={event => onValueChange(event.target.value)} />
  ),
}));

const accounts: Account[] = [
  {
    id: 1,
    name: 'Euro account',
    currency: 'EUR',
    userId: 1,
    balance: 100,
    type: 'CHECKING',
    isActive: true,
    createdAt: '',
    updatedAt: '',
  },
  {
    id: 2,
    name: 'Dollar account',
    currency: 'USD',
    userId: 1,
    balance: 100,
    type: 'CHECKING',
    isActive: true,
    createdAt: '',
    updatedAt: '',
  },
];
const existing: RecurringTransaction = {
  id: 1,
  accountId: 1,
  accountName: 'Euro account',
  toAccountId: null,
  toAccountName: null,
  type: 'EXPENSE',
  amount: 100,
  currency: 'EUR',
  categoryId: null,
  categoryName: null,
  categoryIcon: null,
  categoryColor: null,
  payee: null,
  description: 'Rent',
  notes: null,
  frequency: 'MONTHLY',
  frequencyDisplayName: 'Monthly',
  nextOccurrence: '2024-02-29',
  endDate: '2024-03-31',
  isActive: false,
  createdAt: '',
  updatedAt: '',
  isDue: false,
  daysUntilNext: -100,
  isEnded: true,
};
const submit = vi.fn();
const cancel = vi.fn();
const label = (key: string) => i18n.t(`form.${key}`, { ns: 'recurring' });
function input(id: string) {
  return screen.getByLabelText(document.querySelector(`label[for="${id}"]`)!.textContent!.trim());
}
function change(id: string, value: string) {
  fireEvent.change(input(id), { target: { value } });
}
function renderForm(props: Partial<ComponentProps<typeof RecurringTransactionForm>> = {}) {
  return renderWithProviders(
    <RecurringTransactionForm
      recurringTransaction={existing}
      accounts={accounts}
      categories={[
        { id: 3, name: 'Rent', userId: 1 },
        { id: 4, name: 'Bills', userId: 1 },
      ]}
      onSubmit={submit}
      onCancel={cancel}
      {...props}
    />
  );
}
function save() {
  fireEvent.click(screen.getByRole('button', { name: /^(?:Update|Create)/i }));
}
async function saved() {
  save();
  await waitFor(() => expect(submit).toHaveBeenCalledOnce());
}

describe('Recurring form ownership of user edits and schedule dates', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    source.rate = undefined;
    source.payees = [];
  });

  it('allows a metadata edit to an ended schedule without silently rescheduling it', async () => {
    renderForm();
    change('description', 'Updated description');
    await saved();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        description: 'Updated description',
        nextOccurrence: '2024-02-29',
        endDate: '2024-03-31',
      })
    );
  });

  it.each([
    ['nextOccurrence', '2024-03-01'],
    ['endDate', '2024-04-01'],
    ['frequency', 'WEEKLY'],
  ])('validates past end date when %s changes', async (field, value) => {
    renderForm();
    change(field, value);
    save();
    expect(await screen.findByText(label('validation.endDatePast'))).toBeInTheDocument();
    expect(submit).not.toHaveBeenCalled();
  });

  it.each([
    ['2099-01-01', '2099-01-01', true],
    ['2099-01-02', '2099-01-01', false],
    ['2099-01-02', '', true],
  ])('checks next occurrence %s against end %s', async (next, end, valid) => {
    renderForm();
    change('nextOccurrence', next as string);
    change('endDate', end as string);
    save();
    if (valid)
      await waitFor(() =>
        expect(submit).toHaveBeenCalledWith(
          expect.objectContaining({ nextOccurrence: next, endDate: end || null })
        )
      );
    else {
      expect(await screen.findByText(label('validation.endDateAfterNext'))).toBeInTheDocument();
      expect(submit).not.toHaveBeenCalled();
    }
  });

  it('turns an expense into a transfer without leaking category or payee fields', async () => {
    renderForm({
      recurringTransaction: { ...existing, categoryId: 3, payee: 'Landlord', notes: 'Keep note' },
    });
    change('type', 'TRANSFER');
    expect(screen.queryByLabelText(label('category'))).not.toBeInTheDocument();
    change('toAccountId', '2');
    await saved();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({ toAccountId: 2, categoryId: null, payee: null, notes: 'Keep note' })
    );
  });

  it('requires a transfer destination and clears it when switching back to an expense', async () => {
    renderForm({ recurringTransaction: { ...existing, type: 'TRANSFER' } });
    save();
    expect(await screen.findByText(label('validation.toAccountRequired'))).toBeInTheDocument();
    change('toAccountId', '2');
    change('type', 'EXPENSE');
    await saved();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'EXPENSE', toAccountId: null })
    );
  });

  it('explicitly clears optional category, payee, notes and end date', async () => {
    renderForm({
      recurringTransaction: { ...existing, categoryId: 3, payee: 'Landlord', notes: 'Old note' },
    });
    for (const id of ['categoryId', 'payee', 'notes', 'endDate']) change(id, '');
    await saved();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({ categoryId: null, payee: null, notes: null, endDate: null })
    );
  });

  it.each(['pristine', 'amount-edited', 'currency-edited', 'unknown-account'])(
    'changes account currency only for untouched new input (%s)',
    state => {
      renderForm({ recurringTransaction: undefined });
      if (state === 'amount-edited') change('amount', '100.01');
      if (state === 'currency-edited') change('currency', 'GBP');
      change('accountId', state === 'unknown-account' ? '99' : '2');
      expect(input('currency')).toHaveValue(
        state === 'pristine' ? 'USD' : state === 'currency-edited' ? 'GBP' : 'EUR'
      );
    }
  );

  it.each(['eligible', 'no-category', 'missing-category', 'already-chosen', 'editing'])(
    'autofills the payee category only when appropriate (%s)',
    state => {
      source.payees = [
        {
          name: 'Landlord',
          categoryId: state === 'no-category' ? undefined : state === 'missing-category' ? 99 : 3,
        },
      ];
      renderForm({ recurringTransaction: state === 'editing' ? existing : undefined });
      if (state === 'already-chosen') change('categoryId', '4');
      change('payee', 'Landlord');
      expect(input('categoryId')).toHaveValue(
        state === 'eligible' ? '3' : state === 'already-chosen' ? '4' : ''
      );
    }
  );

  it('retains exact amounts in the conversion preview and the saved request', async () => {
    source.rate = { rate: 1, exactValues: { rate: '1.000000000000000001' } };
    renderForm({
      recurringTransaction: {
        ...existing,
        currency: 'USD',
        amount: 9007199254740992,
        exactValues: { amount: '9007199254740992.01' },
      },
    });
    expect(screen.getByText(/9,007,199,254,740,992\.02/)).toBeInTheDocument();
    await saved();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({ amount: '9007199254740992.01', currency: 'USD' })
    );
  });

  it.each([
    ['description', '', 'validation.descriptionRequired'],
    ['description', 'x'.repeat(1001), 'validation.descriptionTooLong'],
    ['notes', 'x'.repeat(2001), 'validation.notesTooLong'],
    ['nextOccurrence', '', 'validation.nextOccurrenceRequired'],
    ['currency', 'BAD-CODE', 'validation.currencyLength'],
    ['amount', '', 'validation.amountInvalid'],
    ['amount', '0', 'validation.amountPositive'],
  ])('rejects invalid %s before submitting', async (field, value, message) => {
    renderForm();
    change(field, value);
    save();
    expect((await screen.findAllByText(label(message))).length).toBeGreaterThan(0);
    expect(submit).not.toHaveBeenCalled();
  });

  it('shows a server error and prevents submission and cancellation while saving', () => {
    renderForm({ isSubmitting: true, error: 'Account was closed' });
    expect(screen.getByRole('alert')).toHaveTextContent('Account was closed');
    expect(screen.getByRole('button', { name: label('saving') })).toBeDisabled();
    expect(screen.getByRole('button', { name: label('cancel') })).toBeDisabled();
    expect(submit).not.toHaveBeenCalled();
    expect(cancel).not.toHaveBeenCalled();
  });
});
