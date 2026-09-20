import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { ImportWizard } from '@/components/import/ImportWizard';

const cancel = vi.fn();
const transactions = [
  { date: '2026-09-10', amount: -10.99, payee: 'Groceries', validationErrors: [] },
];
vi.mock('@/hooks/useImport', () => ({
  useStartImport: () => ({ mutateAsync: async () => ({ id: 42 }) }),
  useImportSession: (id: number | null) => ({
    data: id ? { id, status: 'PARSED', cancellable: true, readyForReview: true } : undefined,
  }),
  useImportTransactions: (id: number | null) => ({ data: id ? transactions : undefined }),
  useConfirmImport: () => ({ mutateAsync: vi.fn() }),
  useCancelImport: () => ({ mutateAsync: cancel, isPending: false }),
  useUpdateAccount: () => ({ mutateAsync: vi.fn() }),
  useUpdateTransactions: () => ({ mutateAsync: vi.fn() }),
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
  ImportReview: () => <p>Review transactions</p>,
}));

describe('ImportWizard cancellation', () => {
  beforeEach(() => {
    mockAuthentication();
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
});
