import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import type { ComponentProps } from 'react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import RecurringTransactionsPage from '@/pages/RecurringTransactionsPage';
import type {
  RecurringTransactionFilters,
  RecurringTransactionRequest,
} from '@/types/recurringTransaction';
import i18n from '@/test/i18n-test';

const state = vi.hoisted(() => ({
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  pause: vi.fn(),
  resume: vi.fn(),
  process: vi.fn(),
  search: vi.fn(),
}));
const request: RecurringTransactionRequest = {
  accountId: 1,
  type: 'EXPENSE',
  amount: '12.34',
  currency: 'EUR',
  description: 'Internet',
  frequency: 'MONTHLY',
  nextOccurrence: '2027-01-01',
};
vi.mock('@/hooks/useRecurringTransactions', () => ({
  useRecurringTransactionsPaged: (filters: RecurringTransactionFilters) => {
    state.search(filters);
    return {
      data: {
        content: [
          {
            id: 7,
            description: 'Internet',
            amount: 12.34,
            currency: 'EUR',
            isActive: true,
            isDue: true,
          },
        ],
        totalElements: 41,
        totalPages: 3,
        number: filters.page ?? 0,
        size: filters.size ?? 20,
      },
    };
  },
  useCreateRecurringTransaction: () => ({ mutateAsync: state.create }),
  useUpdateRecurringTransaction: () => ({ mutateAsync: state.update }),
  useDeleteRecurringTransaction: () => ({ mutateAsync: state.remove }),
  usePauseRecurringTransaction: () => ({ mutateAsync: state.pause }),
  useResumeRecurringTransaction: () => ({ mutateAsync: state.resume }),
  useProcessRecurringTransactions: () => ({ mutate: state.process }),
}));
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => ({ data: [] }) }));
vi.mock('@/hooks/useTransactions', () => ({ useCategories: () => ({ data: [] }) }));
vi.mock('@/components/transactions/RecurringTransactionCard', () => ({
  RecurringTransactionCard: (
    props: ComponentProps<
      typeof import('@/components/transactions/RecurringTransactionCard').RecurringTransactionCard
    >
  ) => (
    <article>
      <span>{props.recurringTransaction.description}</span>
      <button onClick={() => props.onEdit?.(props.recurringTransaction)}>Edit Internet</button>
      <button onClick={() => props.onDelete?.(props.recurringTransaction)}>Delete Internet</button>
      <button onClick={() => props.onPause?.(props.recurringTransaction.id)}>Pause Internet</button>
      <button onClick={() => props.onResume?.(props.recurringTransaction.id)}>
        Resume Internet
      </button>
      <button onClick={() => props.onViewDetail?.(props.recurringTransaction)}>
        View Internet
      </button>
    </article>
  ),
}));
vi.mock('@/components/transactions/RecurringTransactionForm', () => ({
  RecurringTransactionForm: (
    props: ComponentProps<
      typeof import('@/components/transactions/RecurringTransactionForm').RecurringTransactionForm
    >
  ) => (
    <div>
      {props.error && <p role="alert">{props.error}</p>}
      <button onClick={() => props.onSubmit(request)}>Save schedule</button>
      <button onClick={props.onCancel}>Cancel schedule</button>
    </div>
  ),
}));
vi.mock('@/components/transactions/RecurringTransactionDetailModal', () => ({
  RecurringTransactionDetailModal: (
    props: ComponentProps<
      typeof import('@/components/transactions/RecurringTransactionDetailModal').RecurringTransactionDetailModal
    >
  ) => <button onClick={() => props.onEdit?.(props.recurringTransaction)}>Edit details</button>,
}));
beforeEach(async () => {
  mockAuthentication();
  await i18n.changeLanguage('en');
  vi.clearAllMocks();
  for (const mutation of [state.create, state.update, state.remove, state.pause, state.resume])
    mutation.mockReset().mockResolvedValue({});
  Element.prototype.scrollIntoView = vi.fn();
});

describe('recurring transaction actions', () => {
  it('creates and updates using different mutations and closes only after saving', async () => {
    const user = userEvent.setup();
    renderWithProviders(<RecurringTransactionsPage />);
    await user.click(screen.getByRole('button', { name: /Add Recurring/ }));
    await user.click(screen.getByRole('button', { name: 'Save schedule' }));
    await waitFor(() => expect(state.create).toHaveBeenCalledWith(request));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'Edit Internet' }));
    await user.click(screen.getByRole('button', { name: 'Save schedule' }));
    await waitFor(() => expect(state.update).toHaveBeenCalledWith({ id: 7, data: request }));
    expect(state.create).toHaveBeenCalledOnce();
  });

  it('keeps a failed edit open with the server error and permits cancellation', async () => {
    state.update.mockRejectedValue({
      isAxiosError: true,
      response: { data: { message: 'Account has been archived' } },
    });
    const log = vi.spyOn(console, 'error').mockImplementation(() => {});
    const user = userEvent.setup();
    renderWithProviders(<RecurringTransactionsPage />);
    await user.click(screen.getByRole('button', { name: 'Edit Internet' }));
    await user.click(screen.getByRole('button', { name: 'Save schedule' }));
    expect(await screen.findByText('Account has been archived')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Cancel schedule' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    log.mockRestore();
  });

  it('requires confirmation to delete and retains the confirmation when deletion fails', async () => {
    const user = userEvent.setup();
    const log = vi.spyOn(console, 'error').mockImplementation(() => {});
    state.remove.mockRejectedValueOnce(new Error('Offline'));
    renderWithProviders(<RecurringTransactionsPage />);
    await user.click(screen.getByRole('button', { name: 'Delete Internet' }));
    expect(state.remove).not.toHaveBeenCalled();
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /Cancel/ }));
    expect(state.remove).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Delete Internet' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /Delete/ }));
    await waitFor(() => expect(state.remove).toHaveBeenCalledWith(7));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /Delete/ }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    log.mockRestore();
  });

  it('wires pause, resume, processing, and editing from the detail view to the chosen schedule', async () => {
    const user = userEvent.setup();
    renderWithProviders(<RecurringTransactionsPage />);
    await user.click(screen.getByRole('button', { name: 'Pause Internet' }));
    await user.click(screen.getByRole('button', { name: 'Resume Internet' }));
    expect(state.pause).toHaveBeenCalledWith(7);
    expect(state.resume).toHaveBeenCalledWith(7);
    await user.click(screen.getByRole('button', { name: /Process/ }));
    expect(state.process).toHaveBeenCalledOnce();
    await user.click(screen.getByRole('button', { name: 'View Internet' }));
    await user.click(screen.getByRole('button', { name: 'Edit details' }));
    expect(screen.getByRole('button', { name: 'Save schedule' })).toBeInTheDocument();
  });

  it('returns to the first page when a frequency filter changes', async () => {
    const user = userEvent.setup();
    renderWithProviders(<RecurringTransactionsPage />);
    await user.click(screen.getByRole('button', { name: 'Next page' }));
    expect(state.search).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 20 }));
    await user.click(screen.getByRole('button', { name: /Filters/ }));
    const frequency = screen.getByRole('option', { name: 'Monthly' }).closest('select')!;
    fireEvent.change(frequency, { target: { value: 'MONTHLY' } });
    expect(state.search).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20, frequency: 'MONTHLY' })
    );
  });
});
