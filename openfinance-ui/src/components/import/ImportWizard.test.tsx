import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { Link, Route, Routes } from 'react-router';
import { useImportDraftStore } from '@/stores/importDraft';
import type { ImportTransactionDTO } from '@/types/import';
import { ImportWizard } from '@/components/import/ImportWizard';

const cancel = vi.fn();
const confirm = vi.fn();
const saveReview = vi.fn();
let sessionStatus = 'PARSED';
const transactions = [
  { date: '2026-09-10', amount: -10.99, payee: 'Groceries', validationErrors: [] },
];
vi.mock('@/hooks/useImport', () => ({
  useStartImport: () => ({ mutateAsync: async () => ({ id: 42 }) }),
  useImportSession: (id: number | null) => ({
    data: id ? { id, status: sessionStatus, cancellable: true, readyForReview: true } : undefined,
  }),
  useImportTransactions: (id: number | null) => ({ data: id ? transactions : undefined }),
  useConfirmImport: () => ({ mutateAsync: confirm }),
  useCancelImport: () => ({ mutateAsync: cancel, isPending: false }),
  useUpdateAccount: () => ({ mutateAsync: vi.fn() }),
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
    useImportDraftStore.getState().clear();
    mockAuthentication();
    confirm.mockReset().mockResolvedValue({ id: 42, status: 'IMPORTING' });
    saveReview.mockReset().mockResolvedValue({ id: 42 });
    cancel.mockReset().mockResolvedValue({ id: 42, status: 'CANCELLED' });
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
