import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ComponentProps } from 'react';
import { AccountForm } from '@/components/accounts/AccountForm';
import type { Account, InterestPeriod } from '@/types/account';
import type { InterestPreview, InterestPreviewRequest } from '@/hooks/useInterestPreview';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import { getToday } from '@/utils/date';

const state = vi.hoisted(() => ({
  baseCurrency: 'EUR',
  rate: undefined as { rate: number; exactValues?: Record<string, string> } | undefined,
  preview: undefined as (InterestPreview & { exactValues?: Record<string, string> }) | undefined,
  previewError: false,
  query: vi.fn(),
  submit: vi.fn(),
}));
vi.mock('@/context/AuthContext', async original => ({
  ...(await original<typeof import('@/context/AuthContext')>()),
  useAuthContext: () => ({ baseCurrency: state.baseCurrency }),
}));
vi.mock('@/hooks/useCurrency', () => ({ useLatestExchangeRate: () => ({ data: state.rate }) }));
vi.mock('@/hooks/useInterestPreview', () => ({
  useInterestPreview: (request: InterestPreviewRequest, enabled: boolean) => {
    state.query(request, enabled);
    return { data: state.preview, isError: state.previewError };
  },
}));
vi.mock('@/components/ui/ExchangeRateDisplay', () => ({ ExchangeRateInline: () => null }));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({
    id,
    value,
    onValueChange,
  }: ComponentProps<typeof import('@/components/ui/CurrencySelector').CurrencySelector>) => (
    <select id={id} value={value} onChange={event => onValueChange(event.target.value)}>
      {['EUR', 'USD'].map(code => (
        <option key={code}>{code}</option>
      ))}
    </select>
  ),
}));
vi.mock('@/components/ui/InstitutionSelector', () => ({
  InstitutionSelector: ({
    id,
    value,
    onValueChange,
  }: ComponentProps<typeof import('@/components/ui/InstitutionSelector').InstitutionSelector>) => (
    <select
      id={id}
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value || undefined)}
    >
      <option value="">None</option>
      <option value="3">Bank</option>
    </select>
  ),
}));
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
      {error && <span role="alert">{error}</span>}
    </>
  ),
}));
const account: Account = {
  id: 1,
  userId: 7,
  name: 'Savings',
  type: 'SAVINGS',
  currency: 'USD',
  balance: 500,
  isActive: true,
  createdAt: '',
  updatedAt: '',
};
const label = (key: string) => i18n.t(key, { ns: 'accounts' });
function field(id: string) {
  const text = document
    .querySelector(`label[for="${id}"]`)
    ?.textContent?.trim()
    .replace(/\s+/g, ' ');
  if (!text) throw new Error(`No label for ${id}`);
  return screen.getByLabelText(text);
}
const change = (id: string, value: string) => fireEvent.change(field(id), { target: { value } });
function mount(props: Partial<ComponentProps<typeof AccountForm>> = {}) {
  return renderWithProviders(<AccountForm onSubmit={state.submit} onCancel={vi.fn()} {...props} />);
}
function submit() {
  fireEvent.submit(field('name').closest('form')!);
}
function enablePreview() {
  fireEvent.click(screen.getByRole('switch'));
  change('initialBalance', '1000');
  change('interestRate', '4');
}

describe('Account form preserves balances and conditional interest previews', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    state.rate = undefined;
    state.preview = undefined;
    state.previewError = false;
    state.baseCurrency = 'EUR';
  });

  it('blocks a duplicate name after trimming and ignoring case', async () => {
    mount({ existingAccountNames: ['  Savings '] });
    change('name', 'sAvInGs');
    submit();
    expect(await screen.findByText(label('validation.duplicateName'))).toBeInTheDocument();
    expect(state.submit).not.toHaveBeenCalled();
  });

  it.each([
    ['name', 'a'.repeat(256), 'validation.nameTooLong'],
    ['accountNumber', '1'.repeat(51), 'validation.accountNumberTooLong'],
    ['description', 'a'.repeat(501), 'validation.descriptionTooLong'],
  ])('rejects oversized %s before submitting', async (id, value, key) => {
    mount();
    change('name', 'New account');
    change(id, value);
    submit();
    expect(await screen.findByText(label(key))).toBeInTheDocument();
    expect(state.submit).not.toHaveBeenCalled();
  });

  it.each([false, true])(
    'uses own balance when supplied (%s), excluding related holdings from the edit amount',
    async hasOwnBalance => {
      mount({
        account: {
          ...account,
          balance: 9000,
          ownBalance: hasOwnBalance ? 100 : undefined,
          exactValues: hasOwnBalance ? { ownBalance: '100.01' } : { balance: '9000.01' },
        },
      });
      expect(field('initialBalance')).toHaveValue(hasOwnBalance ? '100.01' : '9,000.01');
      submit();
      await waitFor(() =>
        expect(state.submit).toHaveBeenCalledWith(
          expect.objectContaining({
            initialBalance: hasOwnBalance ? '100.01' : '9000.01',
            adjustBalance: false,
            balanceCurrency: 'USD',
            openingDate: getToday(),
          })
        )
      );
    }
  );

  it.each([
    ['500.00', false],
    ['500.01', true],
  ])(
    'only adjusts the balance for an actual decimal change to %s',
    async (value, adjustBalance) => {
      mount({ account });
      change('initialBalance', String(value));
      submit();
      await waitFor(() =>
        expect(state.submit).toHaveBeenCalledWith(expect.objectContaining({ adjustBalance }))
      );
    }
  );

  it('keeps the entered balance in its original currency when changing the account currency', async () => {
    mount({ account });
    change('currency', 'EUR');
    change('initialBalance', '600.01');
    submit();
    await waitFor(() =>
      expect(state.submit).toHaveBeenCalledWith(
        expect.objectContaining({
          currency: 'EUR',
          balanceCurrency: 'USD',
          initialBalance: '600.01',
          adjustBalance: true,
        })
      )
    );
  });

  it('clears optional metadata explicitly and leaves an omitted opening date undefined', async () => {
    mount({
      account: {
        ...account,
        accountNumber: '1234',
        description: 'Old note',
        institution: { id: 3, name: 'Bank' },
      },
    });
    change('accountNumber', '');
    change('description', '');
    change('institution', '');
    change('openingDate', '');
    submit();
    await waitFor(() =>
      expect(state.submit).toHaveBeenCalledWith(
        expect.objectContaining({
          accountNumber: '',
          description: '',
          institutionId: null,
          openingDate: undefined,
        })
      )
    );
  });

  it.each(['loading', 'error'])('shows %s feedback for an eligible interest preview', mode => {
    state.previewError = mode === 'error';
    mount();
    enablePreview();
    expect(screen.getByRole(mode === 'error' ? 'alert' : 'status')).toHaveTextContent(
      label(mode === 'error' ? 'form.interestPreviewError' : 'form.interestPreviewLoading')
    );
    expect(state.query).toHaveBeenLastCalledWith(
      expect.objectContaining({ balance: '1000', rate: '4', taxRate: '0' }),
      true
    );
  });

  it.each(['0', '-1'])('does not request interest for nonpositive balance %s', balance => {
    mount();
    enablePreview();
    change('initialBalance', balance);
    expect(state.query).toHaveBeenLastCalledWith(expect.objectContaining({ balance }), false);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it.each(['DAILY', 'MONTHLY', 'QUARTERLY', 'HALF_YEARLY', 'ANNUAL'] as InterestPeriod[])(
    'passes the selected %s compounding period and opening date to the preview',
    period => {
      mount();
      enablePreview();
      change('openingDate', '2025-04-15');
      change('interestPeriod', period);
      expect(state.query).toHaveBeenLastCalledWith(
        expect.objectContaining({ openingDate: '2025-04-15', interestPeriod: period }),
        true
      );
    }
  );

  it('clearing rates resets them to zero and disables a zero-interest preview', () => {
    mount();
    enablePreview();
    change('taxRate', '20');
    change('taxRate', '');
    change('interestRate', '');
    expect(state.query).toHaveBeenLastCalledWith(
      expect.objectContaining({ rate: '0', taxRate: '0' }),
      false
    );
  });

  it('displays server preview totals exactly and avoids a double negative tax sign', () => {
    state.preview = {
      year: 2025,
      startDate: '2025-01-01',
      endDate: '2025-12-31',
      grossInterest: 40,
      taxAmount: -10,
      netInterest: 30,
      exactValues: { grossInterest: '40.01', taxAmount: '-10.01', netInterest: '30.00' },
    };
    mount();
    enablePreview();
    expect(screen.getByText(label('form.grossInterest')).parentElement).toHaveTextContent(
      '+€40.01'
    );
    expect(screen.getByText(label('form.taxDeduction')).parentElement).toHaveTextContent('-€10.01');
    expect(screen.getByText(label('form.netEarnings')).parentElement).toHaveTextContent('+€30.00');
  });

  it('preserves input cents in the foreign-currency balance preview and submission', async () => {
    state.rate = { rate: 0.5, exactValues: { rate: '0.5' } };
    mount();
    change('name', 'Foreign savings');
    change('currency', 'USD');
    change('initialBalance', '9007199254740993.25');
    expect(screen.getByText('€4,503,599,627,370,496.63')).toBeInTheDocument();
    submit();
    await waitFor(() =>
      expect(state.submit).toHaveBeenCalledWith(
        expect.objectContaining({ initialBalance: '9007199254740993.25' })
      )
    );
  });
});
