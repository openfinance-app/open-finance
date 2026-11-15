import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { RecurringTransactionCard } from '@/components/transactions/RecurringTransactionCard';
import { RecurringTransactionDetailModal } from '@/components/transactions/RecurringTransactionDetailModal';
import type { RecurringTransaction } from '@/types/recurringTransaction';

const state = vi.hoisted(() => ({
  rate: undefined as { rate: number; exactValues?: Record<string, string> } | undefined,
  close: vi.fn(),
  edit: vi.fn(),
  remove: vi.fn(),
  pause: vi.fn(),
  resume: vi.fn(),
  view: vi.fn(),
}));
vi.mock('@/hooks/useCurrency', () => ({ useLatestExchangeRate: () => ({ data: state.rate }) }));
vi.mock('@/components/attachments', () => ({
  AttachmentList: () => null,
  AttachmentUpload: () => null,
}));
const recurring = (overrides: Partial<RecurringTransaction> = {}): RecurringTransaction => ({
  id: 14,
  accountId: 1,
  accountName: 'Daily account',
  toAccountId: null,
  toAccountName: null,
  type: 'EXPENSE',
  amount: 25,
  currency: 'EUR',
  categoryId: null,
  categoryName: null,
  categoryIcon: null,
  categoryColor: null,
  payee: null,
  description: 'Water payment',
  notes: null,
  frequency: 'MONTHLY',
  frequencyDisplayName: 'Monthly',
  nextOccurrence: '2099-04-03',
  endDate: null,
  isActive: true,
  createdAt: '2024-01-01',
  updatedAt: '2024-01-01',
  isDue: false,
  daysUntilNext: 8,
  isEnded: false,
  ...overrides,
});
function mount(kind: 'card' | 'detail', value: RecurringTransaction) {
  return renderWithProviders(
    kind === 'card' ? (
      <RecurringTransactionCard
        recurringTransaction={value}
        onEdit={state.edit}
        onDelete={state.remove}
        onPause={state.pause}
        onResume={state.resume}
        onViewDetail={state.view}
      />
    ) : (
      <RecurringTransactionDetailModal recurringTransaction={value} onClose={state.close} />
    )
  );
}
beforeEach(() => {
  vi.clearAllMocks();
  mockAuthentication();
  state.rate = undefined;
});

describe.each(['card', 'detail'] as const)('Recurring %s boundary behavior', kind => {
  it.each([
    [{ isEnded: true, isActive: true, isDue: true }, 'Ended'],
    [{ isActive: false, isDue: true }, 'Paused'],
    [{ isDue: true }, 'Due Now'],
    [{ daysUntilNext: 0 }, 'Due Soon'],
    [{ daysUntilNext: 7 }, 'Due Soon'],
    [{ daysUntilNext: -1 }, 'Active'],
    [{ daysUntilNext: 8 }, 'Active'],
  ] as const)('respects status precedence and the seven-day boundary: %j', (changes, label) => {
    mount(kind, recurring(changes));
    expect(screen.getByText(label)).toBeInTheDocument();
  });
  it('retains the original amount when the foreign rate has not loaded', () => {
    mount(kind, recurring({ currency: 'USD', amount: 25.37 }));
    expect(screen.getByText('$25.37')).toBeInTheDocument();
  });
  it.each(['EUR', 'USD'])('preserves exact cents in %s amounts and conversions', currency => {
    state.rate = { rate: 0.5, exactValues: { rate: '0.5' } };
    mount(
      kind,
      recurring({
        currency,
        amount: 9007199254740994,
        exactValues: { amount: '9007199254740993.25' },
      })
    );
    expect(
      screen.getByText(
        currency === 'EUR' ? '€9,007,199,254,740,993.25' : '€4,503,599,627,370,496.63'
      )
    ).toBeInTheDocument();
  });
  it('keeps the raw schedule value visible if an imported date cannot be parsed', () => {
    mount(kind, recurring({ nextOccurrence: 'invalid imported date' }));
    expect(screen.getByText(/invalid imported date/)).toBeInTheDocument();
  });
});

describe('Recurring card actions', () => {
  it.each([true, false])(
    'pause/resume is isolated from opening the detail: active=%s',
    isActive => {
      const value = recurring({ isActive });
      mount('card', value);
      fireEvent.click(
        screen.getByTitle(isActive ? 'Pause recurring transaction' : 'Resume recurring transaction')
      );
      expect(isActive ? state.pause : state.resume).toHaveBeenCalledWith(14);
      expect(state.view).not.toHaveBeenCalled();
      fireEvent.click(screen.getByRole('heading', { name: value.description }));
      expect(state.view).toHaveBeenCalledWith(value);
    }
  );
  it.each([true, false])('does not repeat a pending pause/resume: active=%s', isActive => {
    renderWithProviders(
      <RecurringTransactionCard
        recurringTransaction={recurring({ isActive })}
        onPause={state.pause}
        onResume={state.resume}
        isPausing={isActive}
        isResuming={!isActive}
      />
    );
    const button = screen.getByTitle(
      isActive ? 'Pause recurring transaction' : 'Resume recurring transaction'
    );
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(state.pause).not.toHaveBeenCalled();
    expect(state.resume).not.toHaveBeenCalled();
  });
  it('ended schedules expose their end date and no pause/resume action', () => {
    mount(
      'card',
      recurring({ isEnded: true, endDate: '2025-02-01', notes: 'Final payment recorded' })
    );
    expect(screen.getByText(/Ended on/)).toHaveTextContent('02/01/2025');
    expect(screen.getByText('Final payment recorded')).toBeInTheDocument();
    expect(screen.queryByTitle('Pause recurring transaction')).not.toBeInTheDocument();
    expect(screen.queryByText(/^Next:/)).not.toBeInTheDocument();
  });
  it('shows upcoming end dates, income signs and optional category information', () => {
    mount('card', recurring({ type: 'INCOME', endDate: '2099-05-01', categoryName: 'Salary' }));
    expect(screen.getByText(/Ends on/)).toHaveTextContent('05/01/2099');
    expect(screen.getByText('Salary')).toBeInTheDocument();
    expect(screen.getByText('€25.00').parentElement?.parentElement).toHaveTextContent('+');
  });
  it('edit/delete send the selected record without opening its detail', () => {
    const value = recurring();
    mount('card', value);
    fireEvent.click(screen.getByTitle('Edit recurring transaction'));
    fireEvent.click(screen.getByTitle('Delete recurring transaction'));
    expect(state.edit).toHaveBeenCalledWith(value);
    expect(state.remove).toHaveBeenCalledWith(value);
    expect(state.view).not.toHaveBeenCalled();
  });
  it('unbound optional actions are safe for a read-only caller', () => {
    renderWithProviders(
      <RecurringTransactionCard recurringTransaction={recurring({ isActive: false })} />
    );
    fireEvent.click(screen.getByTitle('Resume recurring transaction'));
    fireEvent.click(screen.getByTitle('Edit recurring transaction'));
    fireEvent.click(screen.getByTitle('Delete recurring transaction'));
    fireEvent.click(screen.getByRole('heading', { name: 'Water payment' }));
    expect(screen.getByText('Paused')).toBeInTheDocument();
  });
  it('unbound pause action is safe for a read-only caller', () => {
    renderWithProviders(<RecurringTransactionCard recurringTransaction={recurring()} />);
    fireEvent.click(screen.getByTitle('Pause recurring transaction'));
    expect(screen.getByText('Active')).toBeInTheDocument();
  });
});

it('an inner dialog can consume Escape without closing the recurring detail', () => {
  const view = mount('detail', recurring({ description: '' }));
  expect(screen.getByRole('dialog', { name: 'Overview' })).toBeInTheDocument();
  const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
  event.preventDefault();
  fireEvent(window, event);
  fireEvent.keyDown(window, { key: 'Enter' });
  expect(state.close).not.toHaveBeenCalled();
  view.unmount();
  fireEvent.keyDown(window, { key: 'Escape' });
  expect(state.close).not.toHaveBeenCalled();
});
