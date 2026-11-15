import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { useImportDraftStore } from '@/stores/importDraft';
import type {
  ImportSessionResponse,
  ImportTransactionDTO,
  FileUploadResponse,
} from '@/types/import';
import { ImportWizard } from '@/components/import/ImportWizard';
import i18n from '@/test/i18n-test';
const state = vi.hoisted(() => ({
  session: {} as Partial<ImportSessionResponse>,
  start: vi.fn(),
  apply: vi.fn(),
  save: vi.fn(),
  confirm: vi.fn(),
  cancel: vi.fn(),
  startError: null as Error | null,
  starting: false,
  applying: false,
  saving: false,
  confirming: false,
  loadingSession: false,
  loadingReview: false,
  fetchingReview: false,
  fileName: 'statement.csv',
  rows: [] as ImportTransactionDTO[],
  uploadId: 'upload-1',
  extraUploads: [] as FileUploadResponse[],
  config: true,
}));
vi.mock('@/hooks/useImport', () => ({
  useStartImport: () => ({
    mutateAsync: state.start,
    isError: !!state.startError,
    error: state.startError,
    isPending: state.starting,
  }),
  useImportSession: (id: number | null) => ({
    data: id ? { id, fileName: state.fileName, ...state.session } : undefined,
    isLoading: state.loadingSession,
  }),
  useImportTransactions: (_id: number | null, _status: string, enabled: boolean) => ({
    data: enabled ? state.rows : [],
    isLoading: enabled && state.loadingReview,
    isFetching: enabled && state.fetchingReview,
  }),
  useImportProgress: () => ({ data: { phase: 'IDLE', processed: 0, total: 0 } }),
  useConfirmImport: () => ({ mutateAsync: state.confirm, isPending: state.confirming }),
  useCancelImport: () => ({ mutateAsync: state.cancel, isPending: false }),
  useUpdateAccount: () => ({ mutateAsync: state.apply, isPending: state.applying }),
  useUpdateTransactions: () => ({ mutateAsync: state.save, isPending: state.saving }),
}));
vi.mock('@/hooks/useImportConfig', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useImportConfig')>()),
  useImportConfig: () => ({ data: { skroogeJsonEnabled: state.config }, isError: false }),
}));
vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: () => ({
    data: [
      { id: 7, name: 'Checking', type: 'CHECKING' },
      { id: 8, name: 'Savings', type: 'SAVINGS' },
    ],
  }),
}));
vi.mock('@/hooks/useTransactions', () => ({
  useCategories: () => ({ data: [] }),
  useCreateCategory: () => ({ mutateAsync: vi.fn() }),
}));
vi.mock('@/components/import/FileUpload', () => ({
  FileUpload: ({
    onUploadSuccess,
  }: ComponentProps<typeof import('@/components/import/FileUpload').FileUpload>) => (
    <button
      onClick={() =>
        onUploadSuccess(
          {
            uploadId: state.uploadId,
            fileName: state.fileName,
            fileSize: 100,
            fileType: 'text/csv',
            status: 'VALIDATED',
            message: '',
            uploadedAt: '',
            recordCount: 1,
          },
          state.extraUploads
        )
      }
    >
      Upload statement
    </button>
  ),
}));
vi.mock('@/components/import/ImportReview', () => ({
  ImportReview: ({
    transactions,
    onTransactionsChange,
  }: ComponentProps<typeof import('@/components/import/ImportReview').ImportReview>) => (
    <section aria-label="Review rows">
      <output>{transactions.length} review rows</output>
      <button onClick={() => onTransactionsChange([])}>Remove all rows</button>
    </section>
  ),
}));
const mount = () => renderWithProviders(<ImportWizard />);
async function upload() {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Upload statement' }));
  });
  expect(state.start).toHaveBeenCalledOnce();
}
async function review() {
  await upload();
  fireEvent.click(screen.getByRole('button', { name: /next/i }));
  await screen.findByLabelText('Review rows');
}
const next = () => fireEvent.click(screen.getByRole('button', { name: /next/i }));

describe('Import wizard failures and destination choices', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
    useImportDraftStore.getState().clear();
    state.session = {
      status: 'PARSED',
      accountId: 7,
      cancellable: true,
      readyForReview: true,
      confirmable: true,
    };
    state.start.mockResolvedValue({ id: 42 });
    state.apply.mockResolvedValue({ id: 42 });
    state.save.mockResolvedValue({ id: 42 });
    state.confirm.mockResolvedValue({ id: 42 });
    state.cancel.mockResolvedValue({ id: 42 });
    state.startError = null;
    state.starting = false;
    state.applying = false;
    state.saving = false;
    state.confirming = false;
    state.loadingSession = false;
    state.loadingReview = false;
    state.fetchingReview = false;
    state.fileName = 'statement.csv';
    state.uploadId = 'upload-1';
    state.extraUploads = [];
    state.config = true;
    state.rows = [{ date: '2025-01-01', amount: -10, payee: 'Shop', validationErrors: [] }];
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });
  afterEach(() => vi.restoreAllMocks());

  it('matches all files in one step, applies overrides, and saves one combined review', async () => {
    state.extraUploads = [{ uploadId: 'upload-2', fileName: 'savings.qif' } as FileUploadResponse];
    state.session.accountId = null;
    state.session.metadata = JSON.stringify({
      importFiles: [
        { uploadId: 'upload-1', fileName: 'statement.csv', accountId: 7, totalTransactions: 1 },
        { uploadId: 'upload-2', fileName: 'savings.qif', accountId: 8, totalTransactions: 1 },
      ],
    });
    state.rows = [
      { ...state.rows[0], sourceUploadId: 'upload-1', sourceFileName: 'statement.csv' },
      { ...state.rows[0], sourceUploadId: 'upload-2', sourceFileName: 'savings.qif' },
    ];
    const rendered = mount();
    await upload();
    expect(state.start).toHaveBeenCalledWith({
      files: [
        { uploadId: 'upload-1', accountId: null, originalFileName: 'statement.csv' },
        { uploadId: 'upload-2', accountId: null, originalFileName: 'savings.qif' },
      ],
    });
    expect(screen.getByRole('combobox', { name: 'Account for statement.csv' })).toHaveValue('7');
    expect(screen.getByRole('combobox', { name: 'Account for savings.qif' })).toHaveValue('8');
    fireEvent.change(screen.getByRole('combobox', { name: 'Account for statement.csv' }), {
      target: { value: '8' },
    });
    rendered.rerender(<div />);
    rendered.rerender(<ImportWizard />);
    expect(screen.getByRole('combobox', { name: 'Account for statement.csv' })).toHaveValue('8');
    next();
    await screen.findByText('2 review rows');
    expect(state.apply).toHaveBeenCalledExactlyOnceWith({
      sessionId: 42,
      accountId: null,
      fileAccounts: { 'upload-1': 8, 'upload-2': 8 },
    });
    expect(state.confirm).not.toHaveBeenCalled();
    next();
    await waitFor(() =>
      expect(state.save).toHaveBeenCalledWith(expect.objectContaining({ transactions: state.rows }))
    );
    fireEvent.click(
      await screen.findByRole('button', { name: i18n.t('import:wizard.confirm.title') })
    );
    await waitFor(() => expect(state.confirm).toHaveBeenCalledOnce());
  });

  it('ignores an upload response that has no server upload identifier', () => {
    state.uploadId = '';
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Upload statement' }));
    expect(state.start).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Upload statement' })).toBeInTheDocument();
  });

  it.each(['API offline', ''])(
    'shows a recoverable start failure with message %s',
    async message => {
      state.start.mockRejectedValue(new Error(message));
      state.startError = new Error(message);
      mount();
      await upload();
      await screen.findByText(i18n.t('import:errors.start'));
      expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
      expect(
        screen.getByText(message || i18n.t('import:wizard.accountSelection.uploadFailedDesc'))
      ).toBeInTheDocument();
      fireEvent.click(
        screen.getByRole('button', { name: i18n.t('import:wizard.accountSelection.startOver') })
      );
      expect(screen.getByRole('button', { name: 'Upload statement' })).toBeInTheDocument();
    }
  );

  it.each(['matched', 'detected', 'absent'])(
    'explains %s account detection without applying it prematurely',
    async kind => {
      state.session.accountId = kind === 'matched' ? 7 : null;
      state.session.suggestedAccountName = kind === 'absent' ? undefined : 'Imported bank';
      mount();
      await upload();
      expect(
        screen.getByText(
          i18n.t(
            `import:wizard.accountSelection.${kind === 'matched' ? 'matched' : kind === 'detected' ? 'detected' : 'noneFound'}`,
            { name: 'statement' }
          ),
          { exact: false }
        )
      ).toBeInTheDocument();
      expect(state.apply).not.toHaveBeenCalled();
    }
  );

  it.each(['8', ''])(
    'applies the user-selected destination %s before requesting review',
    async account => {
      mount();
      await upload();
      fireEvent.change(screen.getByRole('combobox'), { target: { value: account } });
      next();
      await screen.findByLabelText('Review rows');
      expect(state.apply).toHaveBeenCalledWith({ sessionId: 42, accountId: account ? 8 : null });
    }
  );

  it('keeps the account selection after failure and retries the same destination', async () => {
    state.apply.mockRejectedValueOnce(new Error('Account closed'));
    mount();
    await upload();
    next();
    await screen.findByText(i18n.t('import:errors.account'));
    expect(screen.getByRole('combobox')).toHaveValue('7');
    next();
    await screen.findByLabelText('Review rows');
    expect(state.apply).toHaveBeenCalledTimes(2);
  });

  it.each(['parsing', 'failed', 'loading'])('blocks review while the session is %s', async mode => {
    if (mode === 'loading') state.loadingSession = true;
    else {
      state.session.readyForReview = false;
      state.session.status = mode === 'failed' ? 'FAILED' : 'PARSING';
    }
    mount();
    await upload();
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
    if (mode === 'failed') {
      expect(
        screen.getByText(i18n.t('import:wizard.accountSelection.parsingFailedDesc'))
      ).toBeInTheDocument();
      fireEvent.click(
        screen.getByRole('button', { name: i18n.t('import:wizard.accountSelection.startOver') })
      );
      expect(screen.getByRole('button', { name: 'Upload statement' })).toBeInTheDocument();
    }
  });

  it.each(['next', 'previous'])(
    'does not lose review edits when saving before %s fails',
    async direction => {
      state.save.mockRejectedValueOnce(new Error('Cannot save'));
      mount();
      await review();
      fireEvent.click(
        screen.getByRole('button', { name: direction === 'next' ? /next/i : /previous/i })
      );
      await screen.findByText(i18n.t('import:errors.reviewSave'));
      expect(screen.getByLabelText('Review rows')).toBeInTheDocument();
      fireEvent.click(
        screen.getByRole('button', { name: direction === 'next' ? /next/i : /previous/i })
      );
      if (direction === 'next') await screen.findByRole('heading', { name: 'Confirm Import' });
      else await screen.findByRole('combobox');
      expect(state.save).toHaveBeenCalledTimes(2);
    }
  );

  it('keeps an explicitly emptied review even when server rows remain and permits returning to the destination', async () => {
    mount();
    await review();
    fireEvent.click(screen.getByRole('button', { name: 'Remove all rows' }));
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
    expect(screen.getByText('0 review rows')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /previous/i }));
    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(state.save).not.toHaveBeenCalled();
  });

  it('persists duplicate-policy changes and reports save failures before confirmation', async () => {
    mount();
    await review();
    next();
    await screen.findByRole('heading', { name: 'Confirm Import' });
    state.save.mockRejectedValueOnce(new Error('Offline'));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Skip potential duplicates' }));
    await screen.findByText(i18n.t('import:errors.reviewSave'));
    expect(state.save).toHaveBeenLastCalledWith({
      sessionId: 42,
      transactions: state.rows,
      reviewOptions: { categoryMappings: {}, skipDuplicates: false },
    });
  });

  it('only blocks closing the tab while an unfinished session is present', async () => {
    mount();
    const before = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(before);
    expect(before.defaultPrevented).toBe(false);
    await upload();
    await waitFor(() => expect(useImportDraftStore.getState().draft?.sessionId).toBe(42));
    const active = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(active);
    expect(active.defaultPrevented).toBe(true);
  });

  it('connects the destination selector to its visible label', async () => {
    mount();
    await upload();
    expect(screen.getByLabelText(/^Account/i)).toHaveRole('combobox');
  });
});
