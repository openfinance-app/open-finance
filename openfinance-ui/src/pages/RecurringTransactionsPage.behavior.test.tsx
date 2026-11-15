import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import RecurringTransactionsPage from '@/pages/RecurringTransactionsPage';
import type {
  RecurringTransaction,
  RecurringTransactionFilters,
  RecurringTransactionRequest,
} from '@/types/recurringTransaction';
type CardProps = ComponentProps<
  typeof import('@/components/transactions/RecurringTransactionCard').RecurringTransactionCard
>;
type DetailProps = ComponentProps<
  typeof import('@/components/transactions/RecurringTransactionDetailModal').RecurringTransactionDetailModal
>;
type FormProps = ComponentProps<
  typeof import('@/components/transactions/RecurringTransactionForm').RecurringTransactionForm
>;
type ConfirmationProps = ComponentProps<
  typeof import('@/components/ConfirmationDialog').ConfirmationDialog
>;
const state = vi.hoisted(() => ({
  rows: [] as RecurringTransaction[],
  totals: [] as RecurringTransaction[],
  filtered: [] as RecurringTransaction[],
  pages: 3,
  loading: false,
  query: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  pause: vi.fn(),
  resume: vi.fn(),
  process: vi.fn(),
  createPending: false,
  updatePending: false,
  processing: false,
  processError: false,
  processResult: undefined as { processedCount: number; failedCount: number } | undefined,
}));
vi.mock('@/hooks/useRecurringTransactions', () => ({
  useRecurringTransactionsPaged: (filters: RecurringTransactionFilters) => {
    state.query(filters);
    const all = (filters.size ?? 20) > 50;
    const active = !!(
      filters.search ||
      filters.type ||
      filters.frequency ||
      filters.isActive !== undefined ||
      filters.accountId
    );
    return {
      isLoading: state.loading,
      error: null,
      data: {
        content: all ? (active ? state.filtered : state.totals) : state.rows,
        number: filters.page ?? 0,
        size: filters.size ?? 20,
        totalPages: all ? 1 : state.pages,
        totalElements: all ? state.totals.length : 60,
      },
    };
  },
  useCreateRecurringTransaction: () => ({
    mutateAsync: state.create,
    isPending: state.createPending,
  }),
  useUpdateRecurringTransaction: () => ({
    mutateAsync: state.update,
    isPending: state.updatePending,
  }),
  useDeleteRecurringTransaction: () => ({ mutateAsync: state.remove, isPending: false }),
  usePauseRecurringTransaction: () => ({ mutateAsync: state.pause, isPending: false }),
  useResumeRecurringTransaction: () => ({ mutateAsync: state.resume, isPending: false }),
  useProcessRecurringTransactions: () => ({
    mutate: state.process,
    isPending: state.processing,
    isError: state.processError,
    data: state.processResult,
  }),
}));
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => ({ data: [] }) }));
vi.mock('@/hooks/useTransactions', () => ({ useCategories: () => ({ data: [] }) }));
vi.mock('@/components/transactions/RecurringTransactionCard', () => ({
  RecurringTransactionCard: (props: CardProps) => (
    <article aria-label={props.recurringTransaction.description}>
      <button onClick={() => props.onViewDetail?.(props.recurringTransaction)}>
        View schedule
      </button>
      <button onClick={() => props.onEdit?.(props.recurringTransaction)}>Edit schedule</button>
      <button onClick={() => props.onDelete?.(props.recurringTransaction)}>Delete schedule</button>
      <button onClick={() => props.onPause?.(props.recurringTransaction.id)}>Pause schedule</button>
      <button onClick={() => props.onResume?.(props.recurringTransaction.id)}>
        Resume schedule
      </button>
    </article>
  ),
}));
vi.mock('@/components/transactions/RecurringTransactionDetailModal', () => ({
  RecurringTransactionDetailModal: (props: DetailProps) => (
    <section aria-label="Schedule detail">
      <button onClick={props.onClose}>Close detail</button>
      <button onClick={() => props.onEdit?.(props.recurringTransaction)}>Edit detail</button>
    </section>
  ),
}));
const request: RecurringTransactionRequest = {
  accountId: 1,
  type: 'EXPENSE',
  amount: '12.50',
  currency: 'EUR',
  description: 'Internet',
  frequency: 'MONTHLY',
  nextOccurrence: '2099-01-01',
};
vi.mock('@/components/transactions/RecurringTransactionForm', () => ({
  RecurringTransactionForm: (props: FormProps) => (
    <div>
      {props.error && <p role="alert">{props.error}</p>}
      <button disabled={props.isSubmitting} onClick={() => props.onSubmit(request)}>
        Save schedule
      </button>
      <button onClick={props.onCancel}>Cancel schedule</button>
    </div>
  ),
}));
vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: (props: ConfirmationProps) =>
    props.open ? (
      <section aria-label="Delete schedule confirmation">
        <p>{props.description}</p>
        <button onClick={props.onConfirm}>Confirm deletion</button>
        <button onClick={() => props.onOpenChange(false)}>Cancel deletion</button>
      </section>
    ) : null,
}));
vi.mock('@/components/ui/AccountSelector', () => ({
  AccountSelector: ({
    id,
    value,
    onValueChange,
  }: {
    id?: string;
    value?: number;
    onValueChange: (value: number | undefined) => void;
  }) => (
    <select
      id={id}
      aria-label="Account filter"
      value={value ?? ''}
      onChange={e => onValueChange(e.target.value ? Number(e.target.value) : undefined)}
    >
      <option value="">All</option>
      <option value="1">Daily</option>
    </select>
  ),
}));
vi.mock('@/components/ui/Pagination', () => ({
  Pagination: ({
    currentPage,
    onPageChange,
    onPageSizeChange,
  }: {
    currentPage: number;
    onPageChange: (page: number) => void;
    onPageSizeChange: (size: number) => void;
  }) => (
    <nav aria-label="Pages">
      <output aria-label="Current page">{currentPage}</output>
      <button onClick={() => onPageChange(currentPage + 1)}>Next page</button>
      <button onClick={() => onPageSizeChange(50)}>Fifty per page</button>
    </nav>
  ),
}));
const recurring = (changes: Partial<RecurringTransaction> = {}): RecurringTransaction => ({
  id: 1,
  accountId: 1,
  accountName: 'Daily',
  toAccountId: null,
  toAccountName: null,
  type: 'EXPENSE',
  amount: 12.5,
  currency: 'EUR',
  categoryId: null,
  categoryName: null,
  categoryIcon: null,
  categoryColor: null,
  payee: null,
  description: 'Internet',
  notes: null,
  frequency: 'MONTHLY',
  frequencyDisplayName: 'Monthly',
  nextOccurrence: '2099-01-01',
  endDate: null,
  isActive: true,
  createdAt: '2024-01-01',
  updatedAt: '2024-01-01',
  isDue: true,
  daysUntilNext: 0,
  isEnded: false,
  ...changes,
});
function mount() {
  window.history.replaceState({}, '', '/recurring-transactions');
  return renderWithProviders(<RecurringTransactionsPage />);
}
function showFilters() {
  fireEvent.click(screen.getByRole('button', { name: 'Filters' }));
}
beforeEach(() => {
  vi.clearAllMocks();
  mockAuthentication();
  sessionStorage.clear();
  Element.prototype.scrollIntoView = vi.fn();
  state.rows = [recurring()];
  state.totals = [
    recurring(),
    recurring({ id: 2, isActive: false, isDue: false }),
    recurring({ id: 3, isActive: false, isEnded: true, isDue: false }),
  ];
  state.filtered = [state.totals[1]];
  state.pages = 3;
  state.loading = false;
  state.processing = false;
  state.processError = false;
  state.processResult = undefined;
  state.createPending = false;
  state.updatePending = false;
  [state.create, state.update, state.remove, state.pause, state.resume].forEach(mock =>
    mock.mockResolvedValue(undefined)
  );
});
describe('Recurring schedule workflows', () => {
  it('creates a schedule and closes the dialog only after saving', async () => {
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Add Recurring Transaction' }));
    fireEvent.click(screen.getByRole('button', { name: 'Save schedule' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(state.create).toHaveBeenCalledWith(request);
    expect(state.update).not.toHaveBeenCalled();
  });
  it.each([true, false])('edits a schedule from %s detail state', fromDetail => {
    mount();
    if (fromDetail) {
      fireEvent.click(screen.getByText('View schedule'));
      fireEvent.click(screen.getByText('Edit detail'));
    } else fireEvent.click(screen.getByText('Edit schedule'));
    expect(screen.queryByRole('region', { name: 'Schedule detail' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Save schedule'));
    return waitFor(() => expect(state.update).toHaveBeenCalledWith({ id: 1, data: request }));
  });
  it.each([true, false])('failed saves show %s server details and can be retried', explicit => {
    state.create.mockRejectedValueOnce(
      explicit
        ? { isAxiosError: true, response: { data: { message: 'Schedule overlaps' } } }
        : new Error('offline')
    );
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Add Recurring Transaction' }));
    fireEvent.click(screen.getByText('Save schedule'));
    return waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        explicit ? 'Schedule overlaps' : 'Failed to save recurring'
      );
    }).then(async () => {
      fireEvent.click(screen.getByText('Save schedule'));
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(state.create).toHaveBeenCalledTimes(2);
    });
  });
  it('cancelled edits do not become the next create operation', async () => {
    mount();
    fireEvent.click(screen.getByText('Edit schedule'));
    fireEvent.click(screen.getByText('Cancel schedule'));
    fireEvent.click(screen.getByRole('button', { name: 'Add Recurring Transaction' }));
    fireEvent.click(screen.getByText('Save schedule'));
    await waitFor(() => expect(state.create).toHaveBeenCalled());
    expect(state.update).not.toHaveBeenCalled();
  });
  it('closing detail returns to the schedule list', () => {
    mount();
    fireEvent.click(screen.getByText('View schedule'));
    fireEvent.click(screen.getByText('Close detail'));
    expect(screen.queryByRole('region', { name: 'Schedule detail' })).not.toBeInTheDocument();
  });
  it.each([true, false])('pending create/update prevents repeated save: editing=%s', editing => {
    state.createPending = !editing;
    state.updatePending = editing;
    mount();
    fireEvent.click(
      editing
        ? screen.getByText('Edit schedule')
        : screen.getByRole('button', { name: 'Add Recurring Transaction' })
    );
    expect(screen.getByText('Save schedule')).toBeDisabled();
  });
  it('pause and resume target the selected schedule', async () => {
    mount();
    fireEvent.click(screen.getByText('Pause schedule'));
    fireEvent.click(screen.getByText('Resume schedule'));
    await waitFor(() => expect(state.resume).toHaveBeenCalledWith(1));
    expect(state.pause).toHaveBeenCalledWith(1);
  });
  it('delete cancellation preserves the schedule and confirmation sends its id', async () => {
    mount();
    fireEvent.click(screen.getByText('Delete schedule'));
    fireEvent.click(screen.getByText('Cancel deletion'));
    expect(state.remove).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText('Delete schedule'));
    fireEvent.click(screen.getByText('Confirm deletion'));
    await waitFor(() =>
      expect(
        screen.queryByRole('region', { name: 'Delete schedule confirmation' })
      ).not.toBeInTheDocument()
    );
    expect(state.remove).toHaveBeenCalledWith(1);
  });
  it('failed deletion keeps confirmation open for retry', async () => {
    state.remove.mockRejectedValueOnce(new Error('offline'));
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    mount();
    fireEvent.click(screen.getByText('Delete schedule'));
    fireEvent.click(screen.getByText('Confirm deletion'));
    await waitFor(() => expect(state.remove).toHaveBeenCalledTimes(1));
    expect(screen.getByText('Confirm deletion')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Confirm deletion'));
    await waitFor(() => expect(screen.queryByText('Confirm deletion')).not.toBeInTheDocument());
  });
  it('filtered badges use all matching schedules and preserve global totals', () => {
    mount();
    showFilters();
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'paused' } });
    expect(screen.getByText('0 / 1 Active')).toBeInTheDocument();
    expect(screen.getByText('1 / 1 Paused')).toBeInTheDocument();
    expect(screen.getByText('0 / 1 Ended')).toBeInTheDocument();
  });
  it('page and page-size changes preserve filters, and filtering resets the page', () => {
    mount();
    fireEvent.click(screen.getByText('Next page'));
    expect(screen.getByLabelText('Current page')).toHaveTextContent('1');
    showFilters();
    fireEvent.change(screen.getByLabelText('Search'), { target: { value: 'Internet' } });
    expect(screen.getByLabelText('Current page')).toHaveTextContent('0');
    fireEvent.click(screen.getByText('Fifty per page'));
    expect(state.query).toHaveBeenCalledWith(
      expect.objectContaining({ search: 'Internet', size: 50, page: 0 })
    );
  });
  it('removing the final page returns to the nearest existing page', () => {
    const view = mount();
    fireEvent.click(screen.getByText('Next page'));
    fireEvent.click(screen.getByText('Next page'));
    state.pages = 2;
    view.rerender(<RecurringTransactionsPage />);
    expect(screen.getByLabelText('Current page')).toHaveTextContent('1');
  });
  it('empty filtered results can clear all filters while retaining page size', () => {
    state.rows = [];
    mount();
    showFilters();
    fireEvent.change(screen.getByLabelText('Search'), { target: { value: 'missing' } });
    expect(screen.getByText('No results match your filters')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'Clear Filters' })[0]);
    expect(screen.getByText('No recurring transactions')).toBeInTheDocument();
    expect(screen.getByLabelText('Search')).toHaveValue('');
  });
  it('processing reports both successful and failed schedule counts', () => {
    state.processResult = { processedCount: 3, failedCount: 1 };
    mount();
    fireEvent.click(screen.getByText('Process due now'));
    expect(state.process).toHaveBeenCalled();
    expect(screen.getByText('Processed: 3. Failed: 1.')).toHaveAttribute('role', 'status');
  });
  it('processing disables duplicate work and displays its failure', () => {
    state.processing = true;
    state.processError = true;
    mount();
    expect(screen.getByText('Processing…')).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent('Processing failed');
  });
  it.each([
    ['Type', 'INCOME', 'type'],
    ['Frequency', 'YEARLY', 'frequency'],
    ['Status', 'active', 'isActive'],
  ])('changing and clearing %s updates the query', (label, value, key) => {
    mount();
    showFilters();
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
    expect(state.query).toHaveBeenCalledWith(
      expect.objectContaining({ [key]: key === 'isActive' ? true : value, page: 0 })
    );
    fireEvent.change(screen.getByLabelText(label), { target: { value: 'all' } });
    expect(state.query).toHaveBeenLastCalledWith(expect.objectContaining({ [key]: undefined }));
  });
  it('account filtering passes the selected id and resets pagination', () => {
    mount();
    showFilters();
    fireEvent.change(screen.getByLabelText('Account filter'), { target: { value: '1' } });
    expect(state.query).toHaveBeenCalledWith(expect.objectContaining({ accountId: 1, page: 0 }));
  });
});
