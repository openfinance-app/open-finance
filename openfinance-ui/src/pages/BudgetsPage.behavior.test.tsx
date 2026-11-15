import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { useViewStateStore } from '@/stores/viewState';
import i18n from '@/test/i18n-test';
import type {
  BudgetProgressResponse,
  BudgetRequest,
  BudgetResponse,
  BudgetSummaryResponse,
} from '@/types/budget';
import BudgetsPage from '@/pages/BudgetsPage';

const state = vi.hoisted(() => ({
  summary: undefined as BudgetSummaryResponse | undefined,
  native: undefined as BudgetResponse[] | undefined,
  error: null as unknown,
  nativeLoading: false,
  editLoading: false,
  createPending: false,
  updatePending: false,
  removePending: false,
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  summaryQuery: vi.fn(),
  nativeQuery: vi.fn(),
}));
vi.mock('@/hooks/useBudgets', () => ({
  useBudgetSummary: (...args: unknown[]) => {
    state.summaryQuery(...args);
    return { data: state.summary, error: state.error, isLoading: false };
  },
  useBudgets: (...args: unknown[]) => {
    state.nativeQuery(...args);
    return { data: state.native, isLoading: state.nativeLoading };
  },
  useBudget: (id: number | null) => ({
    data: id ? state.native?.find(b => b.id === id) : undefined,
    isLoading: state.editLoading,
  }),
  useCreateBudget: () => ({ mutateAsync: state.create, isPending: state.createPending }),
  useUpdateBudget: () => ({ mutateAsync: state.update, isPending: state.updatePending }),
  useDeleteBudget: () => ({ mutateAsync: state.remove, isPending: state.removePending }),
}));
vi.mock('@/components/budgets/BudgetCard', () => ({
  BudgetCard: ({
    budget,
    onEdit,
    onDelete,
    onViewDetail,
  }: ComponentProps<typeof import('@/components/budgets/BudgetCard').BudgetCard>) => (
    <article aria-label={budget.categoryName}>
      <h2>{budget.categoryName}</h2>
      <button onClick={() => onEdit(budget.budgetId)}>Edit {budget.categoryName}</button>
      <button onClick={() => onDelete(budget.budgetId)}>Delete {budget.categoryName}</button>
      <button onClick={() => onViewDetail(budget.budgetId)}>View {budget.categoryName}</button>
    </article>
  ),
}));
vi.mock('@/components/budgets/BudgetForm', () => ({
  BudgetForm: ({
    budget,
    onSubmit,
    onCancel,
    serverError,
    serverFieldErrors,
    isLoading,
  }: ComponentProps<typeof import('@/components/budgets/BudgetForm').BudgetForm>) => (
    <section aria-label="Budget editor">
      <output>{budget ? `Editing ${budget.id}` : 'New budget'}</output>
      {serverError && <p role="alert">{serverError}</p>}
      {Object.entries(serverFieldErrors ?? {}).map(([field, error]) => (
        <p key={field}>
          {field}: {error}
        </p>
      ))}
      <button disabled={isLoading} onClick={() => onSubmit(request())}>
        Save budget
      </button>
      <button onClick={onCancel}>Cancel editor</button>
    </section>
  ),
}));
vi.mock('@/components/budgets/BudgetWizard', () => ({
  BudgetWizard: ({
    open,
    onClose,
  }: ComponentProps<typeof import('@/components/budgets/BudgetWizard').BudgetWizard>) =>
    open ? (
      <section aria-label="Budget wizard">
        <button onClick={onClose}>Close wizard</button>
      </section>
    ) : null,
}));
vi.mock('@/components/budgets/BudgetDetailModal', () => ({
  BudgetDetailModal: ({
    budgetId,
    onClose,
  }: ComponentProps<typeof import('@/components/budgets/BudgetDetailModal').BudgetDetailModal>) => (
    <section aria-label="Budget details">
      <output>Budget {budgetId}</output>
      <button onClick={onClose}>Close details</button>
    </section>
  ),
}));
vi.mock('@/components/budgets/BudgetSummaryCard', () => ({ BudgetSummaryCard: () => null }));
vi.mock('@/components/LoadingComponents', () => ({
  LoadingSkeleton: () => <p>Loading budget data</p>,
}));
vi.mock('@/components/ui/Pagination', () => ({
  Pagination: ({
    currentPage,
    totalPages,
    onPageChange,
    onPageSizeChange,
  }: ComponentProps<typeof import('@/components/ui/Pagination').Pagination>) => (
    <nav aria-label="Budget pagination">
      <output>
        Page {currentPage + 1} of {totalPages}
      </output>
      <button onClick={() => onPageChange(currentPage + 1)}>Next page</button>
      <button onClick={() => onPageSizeChange(10)}>Ten per page</button>
    </nav>
  ),
}));

function request(): BudgetRequest {
  return {
    categoryId: 1,
    amount: '100.01',
    currency: 'EUR',
    period: 'MONTHLY',
    startDate: '2025-01-01',
    endDate: '2025-01-31',
    rollover: false,
  };
}
function progress(
  id: number,
  extras: Partial<BudgetProgressResponse> = {}
): BudgetProgressResponse {
  return {
    budgetId: id,
    categoryName: `Category ${id}`,
    budgeted: 100,
    spent: 90,
    remaining: 10,
    percentageSpent: 90,
    currency: 'EUR',
    period: 'MONTHLY',
    startDate: '2020-01-01',
    endDate: '2099-12-31',
    daysRemaining: 10,
    status: 'WARNING',
    ...extras,
  };
}
function summary(budgets: BudgetProgressResponse[]): BudgetSummaryResponse {
  return {
    period: 'MONTHLY',
    totalBudgets: budgets.length,
    activeBudgets: budgets.length,
    totalBudgeted: 100,
    totalSpent: 90,
    totalRemaining: 10,
    averageSpentPercentage: 90,
    currency: 'EUR',
    budgets,
  };
}
function native(id: number): BudgetResponse {
  return { ...request(), id, categoryName: `Category ${id}`, createdAt: '', updatedAt: '' };
}
const mount = () => renderWithProviders(<BudgetsPage />);
const openCreate = () =>
  fireEvent.click(screen.getByRole('button', { name: i18n.t('budgets:addBudget') }));
const save = () => fireEvent.click(screen.getByRole('button', { name: 'Save budget' }));

describe('Budget page mutations and recovery boundaries', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
    useViewStateStore.getState().clear();
    window.history.replaceState({}, '', '/budgets');
    localStorage.removeItem('dismissed_budget_alerts');
    state.summary = summary([progress(1), progress(2)]);
    state.native = [native(1), native(2)];
    state.error = null;
    state.nativeLoading = false;
    state.editLoading = false;
    state.createPending = false;
    state.updatePending = false;
    state.removePending = false;
    state.create.mockResolvedValue(undefined);
    state.update.mockResolvedValue(undefined);
    state.remove.mockResolvedValue(undefined);
  });
  afterEach(() => vi.restoreAllMocks());

  it('updates the selected budget and clears edit state before the next creation', async () => {
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Edit Category 2' }));
    expect(screen.getByText('Editing 2')).toBeInTheDocument();
    save();
    await waitFor(() => expect(screen.queryByLabelText('Budget editor')).not.toBeInTheDocument());
    expect(state.update).toHaveBeenCalledWith({ id: 2, data: request() });
    openCreate();
    expect(screen.getByText('New budget')).toBeInTheDocument();
    save();
    await waitFor(() => expect(state.create).toHaveBeenCalledWith(request()));
  });

  it.each(['validation', 'message', 'exception', 'unknown', 'network'])(
    'retains unsaved values and permits retry after a %s failure',
    async kind => {
      const errors: Record<string, unknown> = {
        validation: {
          isAxiosError: true,
          response: {
            data: { validationErrors: { amount: 'Too high' }, message: 'Do not duplicate this' },
          },
        },
        message: {
          isAxiosError: true,
          response: { data: { message: 'Category already budgeted' } },
        },
        exception: new Error('Disconnected'),
        unknown: 'unexpected',
        network: { isAxiosError: true },
      };
      state.create.mockRejectedValueOnce(errors[kind]);
      mount();
      openCreate();
      save();
      const expected = {
        validation: 'amount: Too high',
        message: 'Category already budgeted',
        exception: 'Disconnected',
        unknown: i18n.t('budgets:saveError'),
        network: i18n.t('budgets:saveError'),
      }[kind]!;
      await screen.findByText(expected);
      expect(screen.queryByText('Do not duplicate this')).not.toBeInTheDocument();
      save();
      await waitFor(() => expect(screen.queryByLabelText('Budget editor')).not.toBeInTheDocument());
      expect(state.create).toHaveBeenCalledTimes(2);
    }
  );

  it('clears server validation when cancelling and reopening an editor', async () => {
    state.create.mockRejectedValueOnce({
      isAxiosError: true,
      response: { data: { validationErrors: { amount: 'Too high' } } },
    });
    mount();
    openCreate();
    save();
    await screen.findByText('amount: Too high');
    fireEvent.click(screen.getByRole('button', { name: 'Cancel editor' }));
    fireEvent.click(screen.getByRole('button', { name: 'Edit Category 1' }));
    expect(screen.queryByText('amount: Too high')).not.toBeInTheDocument();
  });

  it('shows a loading state instead of a blank edit form while fetching the selected budget', () => {
    state.editLoading = true;
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Edit Category 1' }));
    expect(screen.getByText('Loading budget data')).toBeInTheDocument();
    expect(screen.queryByLabelText('Budget editor')).not.toBeInTheDocument();
  });

  it.each(['create', 'update'])('prevents Escape from closing a pending %s', async kind => {
    if (kind === 'create') state.createPending = true;
    else state.updatePending = true;
    mount();
    if (kind === 'create') openCreate();
    else fireEvent.click(screen.getByRole('button', { name: 'Edit Category 1' }));
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    expect(screen.getByLabelText('Budget editor')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save budget' })).toBeDisabled();
  });

  it('dismisses a nonpending editor with Escape', async () => {
    mount();
    openCreate();
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    await waitFor(() => expect(screen.queryByLabelText('Budget editor')).not.toBeInTheDocument());
  });

  it('retains the deletion confirmation after failure and retries the same budget', async () => {
    state.remove.mockRejectedValueOnce(new Error('Still referenced'));
    vi.spyOn(console, 'error').mockImplementation(() => {});
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Delete Category 2' }));
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('Category 2');
    fireEvent.click(
      within(dialog).getByRole('button', { name: i18n.t('budgets:dialogs.delete.confirmText') })
    );
    await waitFor(() => expect(state.remove).toHaveBeenCalledTimes(1));
    expect(dialog).toBeInTheDocument();
    fireEvent.click(
      within(dialog).getByRole('button', { name: i18n.t('budgets:dialogs.delete.confirmText') })
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(state.remove.mock.calls).toEqual([[2], [2]]);
  });

  it.each(['loading', 'unavailable', 'empty'])(
    'keeps summary failure visible while native budgets are %s',
    mode => {
      state.error = new Error('Missing FX');
      state.nativeLoading = mode === 'loading';
      state.native = mode === 'empty' ? [] : undefined;
      mount();
      expect(screen.getByRole('alert')).toHaveTextContent(i18n.t('budgets:summaryUnavailable'));
      if (mode === 'loading') expect(screen.getByText('Loading budget data')).toBeInTheDocument();
      if (mode === 'unavailable')
        expect(screen.getByText(i18n.t('budgets:loadError'))).toBeInTheDocument();
      expect(state.nativeQuery).toHaveBeenLastCalledWith(undefined, true);
    }
  );

  it('filters native fallback budgets and identifies the selected deletion by name', () => {
    state.error = new Error('Missing FX');
    window.history.replaceState({}, '', '/budgets?alertKeyword=Category%202');
    mount();
    expect(screen.queryByRole('heading', { name: 'Category 1' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Category 2' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: i18n.t('common:aria.deleteBudget') }));
    expect(screen.getByRole('dialog')).toHaveTextContent('Category 2');
  });

  it.each(['not-json', '{}', 'null'])(
    'recovers from corrupt dismissed-alert storage %s',
    stored => {
      localStorage.setItem('dismissed_budget_alerts', stored);
      mount();
      expect(screen.getAllByRole('alert')).toHaveLength(2);
    }
  );

  it('honors stored dismissals and limits visible alerts to three', () => {
    state.summary = summary(Array.from({ length: 6 }, (_, i) => progress(i + 1)));
    localStorage.setItem('dismissed_budget_alerts', '[1,2]');
    mount();
    const alerts = screen.getAllByRole('alert');
    expect(alerts).toHaveLength(3);
    expect(alerts[0]).toHaveTextContent('Category 3');
    expect(alerts[2]).toHaveTextContent('Category 5');
  });

  it('can close and reopen the wizard and budget details independently', () => {
    mount();
    fireEvent.click(screen.getByRole('button', { name: i18n.t('budgets:autoCreate') }));
    fireEvent.click(screen.getByRole('button', { name: 'Close wizard' }));
    expect(screen.queryByLabelText('Budget wizard')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'View Category 1' }));
    fireEvent.click(screen.getByRole('button', { name: 'Close details' }));
    expect(screen.queryByLabelText('Budget details')).not.toBeInTheDocument();
  });

  it('resets pagination on a page-size change and clamps a page after deletions', () => {
    state.summary = summary(
      Array.from({ length: 41 }, (_, i) => progress(i + 1, { status: 'ON_TRACK' }))
    );
    const view = mount();
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(screen.getByRole('article', { name: 'Category 21' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Ten per page' }));
    expect(screen.getByText('Page 1 of 5')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    state.summary = summary([progress(1)]);
    view.rerender(<BudgetsPage />);
    expect(screen.getByRole('article', { name: 'Category 1' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Budget pagination')).not.toBeInTheDocument();
  });
});
