import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { Link, Route, Routes } from 'react-router';
import { useImportDraftStore } from '@/stores/importDraft';
import type { ImportSessionStatus, ImportTransactionDTO } from '@/types/import';
import { ProtectedRoute } from '@/components/ProtectedRoute';
import { ImportWizard } from '@/components/import/ImportWizard';

const cancel = vi.fn();
const confirm = vi.fn();
const saveReview = vi.fn();
const applyAccount = vi.fn();
let sessionStatus = 'PARSED';
let sessionMetadata = '';
let sessionError: Error | null = null;
let reviewing = false;
let liveProgress = { phase: 'IDLE', processed: 0, total: 0 };
const transactions = [
  { date: '2026-09-10', amount: -10.99, payee: 'Groceries', validationErrors: [] },
];
vi.mock('@/hooks/useImport', () => ({
  useStartImport: () => ({ mutateAsync: async () => ({ id: 42 }) }),
  useImportSession: (id: number | null) => ({
    data:
      id && !sessionError
        ? {
            id,
            status: sessionStatus,
            cancellable: true,
            readyForReview: true,
            confirmable: true,
            fileName: 'statement.csv',
            accountId: 7,
            metadata: sessionMetadata,
          }
        : undefined,
    error: id ? sessionError : null,
  }),
  useImportTransactions: (
    id: number | null,
    _status: ImportSessionStatus | undefined,
    enabled = true
  ) => ({
    data: id && enabled ? transactions : undefined,
    isLoading: enabled && reviewing,
    isFetching: enabled && reviewing,
  }),
  useImportProgress: () => ({ data: liveProgress }),
  useConfirmImport: () => ({ mutateAsync: confirm }),
  useCancelImport: () => ({ mutateAsync: cancel, isPending: false }),
  useUpdateAccount: () => ({ mutateAsync: applyAccount }),
  useUpdateTransactions: () => ({ mutateAsync: saveReview }),
}));
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => ({ data: [] }) }));
vi.mock('@/hooks/useTransactions', () => ({
  useCategories: () => ({ data: [] }),
  useCreateCategory: () => ({ mutateAsync: vi.fn() }),
}));
vi.mock('@/components/import/FileUpload', () => ({
  FileUpload: ({
    onUploadSuccess,
  }: {
    onUploadSuccess: (file: { uploadId: string; fileName: string }) => void;
  }) => (
    <button onClick={() => onUploadSuccess({ uploadId: 'upload-1', fileName: 'statement.csv' })}>
      Upload statement
    </button>
  ),
}));
vi.mock('@/components/import/ImportReview', () => ({
  ImportReview: ({
    transactions,
    onTransactionsChange,
  }: {
    transactions: ImportTransactionDTO[];
    onTransactionsChange: (transactions: ImportTransactionDTO[]) => void;
  }) => (
    <div>
      <p>Review transactions</p>
      <p>{transactions[0]?.memo}</p>
      <button
        onClick={() =>
          onTransactionsChange(
            transactions.map(row => ({ ...row, memo: 'Saved draft correction' }))
          )
        }
      >
        Correct memo
      </button>
    </div>
  ),
}));

describe('ImportWizard cancellation', () => {
  beforeEach(() => {
    sessionStatus = 'PARSED';
    sessionMetadata = '';
    sessionError = null;
    reviewing = false;
    liveProgress = { phase: 'IDLE', processed: 0, total: 0 };
    useImportDraftStore.getState().clear();
    mockAuthentication();
    confirm.mockReset().mockResolvedValue({ id: 42, status: 'IMPORTING' });
    saveReview.mockReset().mockResolvedValue({ id: 42 });
    applyAccount.mockReset().mockResolvedValue({ id: 42 });
    cancel.mockReset().mockResolvedValue({ id: 42, status: 'CANCELLED' });
  });

  it('recovers a confirmation from the server using only a stored session reference', async () => {
    sessionStorage.setItem(
      'import_recovery',
      JSON.stringify({ userId: 1, sessionId: 42, selectedStep: 'confirm' })
    );
    sessionStatus = 'REVIEWING';
    sessionMetadata = JSON.stringify({
      reviewOptions: { categoryMappings: { Food: 308 }, skipDuplicates: false },
    });
    renderWithProviders(
      <ProtectedRoute>
        <ImportWizard />
      </ProtectedRoute>
    );
    expect(await screen.findByRole('heading', { name: 'Confirm Import' })).toBeInTheDocument();
    expect(screen.getByText('statement.csv')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Skip potential duplicates' })).not.toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Import' }));
    await waitFor(() =>
      expect(confirm).toHaveBeenCalledWith({
        sessionId: 42,
        accountId: 7,
        categoryMappings: { Food: 308 },
        skipDuplicates: false,
      })
    );
    expect(JSON.parse(sessionStorage.getItem('import_recovery')!)).toEqual({
      userId: 1,
      sessionId: 42,
      selectedStep: 'progress',
    });
  });

  it('starts AI review only after Next applies the selected account', async () => {
    let finishApplying!: () => void;
    applyAccount.mockReturnValue(
      new Promise<void>(resolve => {
        finishApplying = resolve;
      })
    );
    reviewing = true;
    liveProgress = { phase: 'AI_CATEGORIZING', processed: 15, total: 45 };
    renderWithProviders(<ImportWizard />);
    fireEvent.click(screen.getByRole('button', { name: 'Upload statement' }));
    await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).toBeEnabled());
    expect(screen.queryByText(/AI categorization in progress/)).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole('combobox'), { target: { value: '' } });
    expect(screen.queryByText(/AI categorization in progress/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /next/i }));
    expect(applyAccount).toHaveBeenCalledWith({ sessionId: 42, accountId: null });
    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.queryByText(/AI categorization in progress/)).not.toBeInTheDocument();

    await act(async () => finishApplying());
    expect(await screen.findByRole('status')).toHaveTextContent(
      'AI categorization in progress (15/45)'
    );
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });

  it('shows AI batch progress while review is loading and blocks continuing', async () => {
    sessionStorage.setItem(
      'import_recovery',
      JSON.stringify({ userId: 1, sessionId: 42, selectedStep: 'review' })
    );
    reviewing = true;
    liveProgress = { phase: 'AI_CATEGORIZING', processed: 15, total: 45 };
    renderWithProviders(
      <ProtectedRoute>
        <ImportWizard />
      </ProtectedRoute>
    );
    expect(await screen.findByRole('heading', { name: 'Review Transactions' })).toBeInTheDocument();
    expect(await screen.findByRole('status')).toHaveTextContent(
      'AI categorization in progress (15/45)'
    );
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
  });

  it('waits for restored review data before enabling confirmation', async () => {
    sessionStorage.setItem(
      'import_recovery',
      JSON.stringify({ userId: 1, sessionId: 42, selectedStep: 'confirm' })
    );
    reviewing = true;
    const view = renderWithProviders(
      <ProtectedRoute>
        <ImportWizard />
      </ProtectedRoute>
    );
    expect(await screen.findByRole('button', { name: 'Confirm Import' })).toBeDisabled();
    reviewing = false;
    view.rerender(
      <ProtectedRoute>
        <ImportWizard />
      </ProtectedRoute>
    );
    expect(await screen.findByRole('button', { name: 'Confirm Import' })).toBeEnabled();
  });

  it('lets the user start over when a recovery session no longer exists', async () => {
    sessionStorage.setItem(
      'import_recovery',
      JSON.stringify({ userId: 1, sessionId: 42, selectedStep: 'confirm' })
    );
    sessionError = new Error('Not found');
    renderWithProviders(
      <ProtectedRoute>
        <ImportWizard />
      </ProtectedRoute>
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load this import');
    fireEvent.click(screen.getByRole('button', { name: 'Start Over' }));
    expect(await screen.findByRole('button', { name: 'Upload statement' })).toBeInTheDocument();
    expect(sessionStorage.getItem('import_recovery')).toBeNull();
  });

  async function enterReview() {
    renderWithProviders(<ImportWizard />);
    fireEvent.click(screen.getByRole('button', { name: 'Upload statement' }));
    await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: /next/i }));
    await screen.findByText('Review transactions');
    fireEvent.click(screen.getByRole('button', { name: /^cancel$/i }));
    fireEvent.click(screen.getByRole('button', { name: /leave anyway/i }));
  }

  it('cancels the server session and returns to an empty upload step on the same route', async () => {
    await enterReview();
    await screen.findByRole('button', { name: 'Upload statement' });
    expect(cancel).toHaveBeenCalledWith(42);
    expect(screen.queryByText('Review transactions')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
  });

  it('keeps the review and displays an error when server cancellation fails', async () => {
    cancel.mockRejectedValue(new Error('Offline'));
    await enterReview();
    expect(await screen.findByRole('alert')).toHaveTextContent('Cancellation failed');
    expect(screen.getByText('Review transactions')).toBeInTheDocument();
  });
  it('shows a confirmation error and permits retry with the same review', async () => {
    confirm.mockRejectedValueOnce(new Error('Network unavailable'));
    renderWithProviders(<ImportWizard />);
    fireEvent.click(screen.getByRole('button', { name: 'Upload statement' }));
    await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: /next/i }));
    await screen.findByText('Review transactions');
    fireEvent.click(screen.getByRole('button', { name: /next/i }));
    fireEvent.click(await screen.findByRole('button', { name: /confirm import/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not confirm the import');
    fireEvent.click(screen.getByRole('button', { name: /confirm import/i }));
    await waitFor(() => expect(confirm).toHaveBeenCalledTimes(2));
    expect(confirm.mock.calls[1][0]).toEqual(confirm.mock.calls[0][0]);
  });

  it('resumes the edited review after navigating away and back', async () => {
    window.history.replaceState({}, '', '/import');
    renderWithProviders(
      <>
        <Link to="/accounts">Accounts</Link>
        <Link to="/import">Import</Link>
        <Routes>
          <Route path="/import" element={<ImportWizard />} />
          <Route path="/accounts" element={<p>Accounts screen</p>} />
        </Routes>
      </>
    );
    fireEvent.click(screen.getByRole('button', { name: 'Upload statement' }));
    await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: /next/i }));
    await screen.findByText('Review transactions');
    fireEvent.click(screen.getByRole('button', { name: 'Correct memo' }));
    expect(screen.getByText('Saved draft correction')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('link', { name: 'Accounts' }));
    expect(await screen.findByText('Accounts screen')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('link', { name: 'Import' }));
    expect(await screen.findByText('Saved draft correction')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Upload statement' })).not.toBeInTheDocument();
  });

  it('clears the failed-request alert when refreshed status confirms completion', async () => {
    confirm.mockRejectedValueOnce(new Error('Response lost'));
    const view = renderWithProviders(<ImportWizard />);
    fireEvent.click(screen.getByRole('button', { name: 'Upload statement' }));
    await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: /next/i }));
    await screen.findByText('Review transactions');
    fireEvent.click(screen.getByRole('button', { name: /next/i }));
    fireEvent.click(await screen.findByRole('button', { name: /confirm import/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not confirm the import');
    sessionStatus = 'COMPLETED';
    view.rerender(<ImportWizard />);
    expect(await screen.findByText('Import completed successfully!')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(confirm).toHaveBeenCalledTimes(1);
  });
});
