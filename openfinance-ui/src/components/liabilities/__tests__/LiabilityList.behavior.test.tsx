import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { AxiosError } from 'axios';
import type { ComponentProps } from 'react';
import { LiabilityList } from '@/components/liabilities/LiabilityList';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import type { Liability } from '@/types/liability';
import i18n from '@/test/i18n-test';

vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: ({
    open,
    onOpenChange,
    onConfirm,
    children,
    loading,
  }: ComponentProps<typeof import('@/components/ConfirmationDialog').ConfirmationDialog>) =>
    open ? (
      <div role="dialog">
        {children}
        <button onClick={onConfirm}>Confirm deletion</button>
        <button onClick={() => onOpenChange(false)}>Cancel deletion</button>
        <span>{loading ? 'Deleting' : 'Ready'}</span>
      </div>
    ) : null,
}));
vi.mock('@/context/AuthContext', async original => ({
  ...(await original<typeof import('@/context/AuthContext')>()),
  useAuthContext: () => ({ baseCurrency: 'EUR' }),
}));
const debt: Liability = {
  id: 1,
  name: 'Car loan',
  type: 'AUTO_LOAN',
  currency: 'EUR',
  principal: 1000,
  currentBalance: 500,
  minimumPayment: 30,
  startDate: '2025-01-01',
  createdAt: '',
  updatedAt: '',
};
function amount(label: string) {
  return screen.getByText(i18n.t(label, { ns: 'liabilities' })).parentElement!;
}
const renderDebt = (changes: Partial<Liability> = {}, onDelete = vi.fn()) =>
  renderWithProviders(
    <LiabilityList liabilities={[{ ...debt, ...changes }]} onEdit={vi.fn()} onDelete={onDelete} />
  );

describe('Liability payment, funding and deletion boundaries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });
  afterEach(() => vi.useRealTimers());

  it.each([
    [undefined, 30, '€30.00'],
    [0, 30, '€0.00'],
    [25, 30, '€25.00'],
    [undefined, undefined, null],
  ] as const)(
    'resolves effective payment %s ahead of minimum %s',
    (effectiveMonthlyPayment, minimumPayment, expected) => {
      renderDebt({ effectiveMonthlyPayment, minimumPayment });
      if (expected) expect(amount('list.monthlyPayment')).toHaveTextContent(expected);
      else
        expect(
          screen.queryByText(i18n.t('list.monthlyPayment', { ns: 'liabilities' }))
        ).not.toBeInTheDocument();
    }
  );

  it.each([0, -1])('settled or credit balance %s has no scheduled amount due', currentBalance => {
    renderDebt({ currentBalance, effectiveMonthlyPayment: 25 });
    expect(amount('list.monthlyPayment')).toHaveTextContent('€0.00');
  });

  it.each([
    { interestRate: undefined, insurancePercentage: undefined, expected: null },
    { interestRate: undefined, insurancePercentage: 1.25, expected: '— / 1.25%' },
    { interestRate: 0, insurancePercentage: 0, expected: '0.00%' },
    { interestRate: 4, insurancePercentage: 1.25, expected: '4.00% / 1.25%' },
  ])('shows optional interest and insurance separately: $expected', ({ expected, ...changes }) => {
    renderDebt(changes);
    if (expected)
      expect(
        amount(changes.insurancePercentage ? 'list.interestInsurance' : 'list.interestRate')
      ).toHaveTextContent(expected);
    else
      expect(
        screen.queryByText(i18n.t('list.interestRate', { ns: 'liabilities' }))
      ).not.toBeInTheDocument();
  });

  it.each([0, 250])(
    'uses funded principal and repaid amount %s for a partially drawn loan',
    principalPaid => {
      renderDebt({
        fundedAmount: 500,
        currentBalance: 250,
        principalPaid: principalPaid || undefined,
        fundingStatus: 'PARTIALLY_FUNDED',
      });
      expect(screen.getByText(/50\.0%/)).toBeInTheDocument();
      expect(
        screen.getByText(i18n.t('fundingStatus.PARTIALLY_FUNDED', { ns: 'liabilities' }))
      ).toBeInTheDocument();
      expect(
        screen.getByText(new RegExp(i18n.t('funding.repaid', { ns: 'liabilities' })))
      ).toHaveTextContent(`€${principalPaid.toFixed(2)}`);
    }
  );

  it('preserves exact cents when displaying and converting principal and monthly payment', () => {
    renderDebt({
      currency: 'USD',
      isConverted: true,
      exchangeRate: 0.5,
      baseCurrency: 'EUR',
      principal: 9007199254740992,
      effectiveMonthlyPayment: 9007199254740992,
      exactValues: {
        principal: '9007199254740993.25',
        effectiveMonthlyPayment: '9007199254740993.25',
        exchangeRate: '0.5',
      },
    });
    expect(amount('list.originalPrincipal')).toHaveTextContent('€4,503,599,627,370,496.63');
    expect(amount('list.monthlyPayment')).toHaveTextContent('€4,503,599,627,370,496.63');
  });

  it('renders an institution logo and name without requiring notes', () => {
    renderDebt({
      institution: {
        id: 2,
        name: 'Example Bank',
        logo: '/bank.svg',
        countryCode: 'FR',
        createdAt: '',
        updatedAt: '',
      },
    });
    expect(screen.getByText('Example Bank')).toBeInTheDocument();
    expect(document.querySelector('img')).toHaveAttribute('src', '/bank.svg');
  });

  it.each(['message', 'network', 'unknown', 'empty-body'])(
    'retains a failed delete for retry with %s error feedback',
    async kind => {
      const failure =
        kind === 'unknown' ? new Error('private detail') : new AxiosError('Request failed');
      if ((kind === 'message' || kind === 'empty-body') && failure instanceof AxiosError)
        failure.response = {
          data: kind === 'message' ? { message: 'Linked payments prevent deletion' } : undefined,
          status: 409,
          statusText: 'Conflict',
          headers: {},
          config: { headers: {} },
        } as NonNullable<AxiosError['response']>;
      const onDelete = vi.fn().mockRejectedValueOnce(failure).mockResolvedValue(undefined);
      renderDebt({}, onDelete);
      fireEvent.click(screen.getByRole('button', { name: /delete liability/i }));
      fireEvent.click(screen.getByRole('button', { name: 'Confirm deletion' }));
      expect(await screen.findByRole('alert')).toHaveTextContent(
        kind === 'message'
          ? 'Linked payments prevent deletion'
          : i18n.t('deleteError', { ns: 'liabilities' })
      );
      fireEvent.click(screen.getByRole('button', { name: 'Confirm deletion' }));
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(onDelete).toHaveBeenNthCalledWith(2, 1);
    }
  );

  it('ignores repeated confirmation while deleting and clears stale errors when reopening', async () => {
    let resolve!: () => void;
    const onDelete = vi.fn().mockImplementation(
      () =>
        new Promise<void>(done => {
          resolve = done;
        })
    );
    renderDebt({}, onDelete);
    fireEvent.click(screen.getByRole('button', { name: /delete liability/i }));
    const confirm = screen.getByRole('button', { name: 'Confirm deletion' });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(onDelete).toHaveBeenCalledTimes(1);
    await act(async () => resolve());
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('cancelling a failed deletion clears the error before a later attempt', async () => {
    renderDebt({}, vi.fn().mockRejectedValue(new Error('failure')));
    fireEvent.click(screen.getByRole('button', { name: /delete liability/i }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm deletion' }));
    await screen.findByRole('alert');
    fireEvent.click(screen.getByRole('button', { name: 'Cancel deletion' }));
    fireEvent.click(screen.getByRole('button', { name: /delete liability/i }));
    expect(within(screen.getByRole('dialog')).queryByRole('alert')).not.toBeInTheDocument();
  });

  it.each([1, 99])('scrolls only when highlighted debt %s exists', async highlightedId => {
    vi.useFakeTimers();
    renderWithProviders(
      <LiabilityList
        liabilities={[debt]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
        highlightedId={highlightedId}
      />
    );
    await act(async () => vi.advanceTimersByTime(1000));
    expect(Element.prototype.scrollIntoView).toHaveBeenCalledTimes(highlightedId === 1 ? 1 : 0);
  });
});
