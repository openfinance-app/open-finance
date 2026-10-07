import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { useAuthContext } from '@/context/AuthContext';
import { useCurrencyDisplay } from '@/context/CurrencyDisplayContext';
import { STORAGE_KEYS } from '@/constants/storage';

vi.mock('@/hooks/useDocumentTitle', () => ({ useDocumentTitle: vi.fn() }));

const mockCreateBackup = vi.fn().mockResolvedValue({});
const mockRestoreBackup = vi.fn().mockResolvedValue('Restored');
const mockDeleteBackup = vi.fn().mockResolvedValue({});
const mockDownloadBackup = vi.fn().mockResolvedValue({});
const mockUploadAndRestore = vi.fn().mockResolvedValue('Uploaded');

const mockBackups = [
  {
    id: 1,
    filename: 'backup-2024-01-01.ofbak',
    description: 'Daily backup',
    formattedFileSize: '5.2 MB',
    createdAt: '2024-01-01T10:00:00Z',
    backupType: 'MANUAL' as const,
    status: 'COMPLETED' as const,
    errorMessage: null,
  },
  {
    id: 2,
    filename: 'backup-2024-01-02.ofbak',
    description: null,
    formattedFileSize: '3.1 MB',
    createdAt: '2024-01-02T10:00:00Z',
    backupType: 'AUTOMATIC' as const,
    status: 'FAILED' as const,
    errorMessage: 'Disk full',
  },
];

let mockData: typeof mockBackups | undefined = [];
let mockIsLoading = false;
let mockError: Error | null = null;

vi.mock('@/hooks/useBackup', () => ({
  useListBackups: () => ({ data: mockData, isLoading: mockIsLoading, error: mockError }),
  useCreateBackup: () => ({ mutateAsync: mockCreateBackup, isPending: false }),
  useRestoreBackup: () => ({ mutateAsync: mockRestoreBackup, isPending: false }),
  useUploadAndRestoreBackup: () => ({ mutateAsync: mockUploadAndRestore, isPending: false }),
  useDeleteBackup: () => ({ mutateAsync: mockDeleteBackup, isPending: false }),
  useDownloadBackup: () => ({ mutateAsync: mockDownloadBackup, isPending: false }),
}));

vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: ({ open, onConfirm, title }: any) =>
    open ? (
      <div data-testid="confirmation-dialog">
        <span>{title}</span>
        <button onClick={onConfirm}>Confirm</button>
      </div>
    ) : null,
}));

import BackupPage from './BackupPage';

function RestoredPreferences() {
  const { user } = useAuthContext();
  const { displayMode, secondaryCurrency } = useCurrencyDisplay();
  return (
    <output aria-label="Active preferences">{`${user?.baseCurrency}/${displayMode}/${secondaryCurrency}`}</output>
  );
}

describe('BackupPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockData = [];
    mockIsLoading = false;
    mockError = null;
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders the page title', () => {
    renderWithProviders(<BackupPage />);
    expect(screen.getByText('Backup & Restore')).toBeInTheDocument();
  });

  it('shows empty state when no backups', () => {
    mockData = [];
    renderWithProviders(<BackupPage />);
    expect(screen.getByText(/no backups/i)).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockError = new Error('fail');
    renderWithProviders(<BackupPage />);
    expect(screen.getByText(/error|failed|load/i)).toBeInTheDocument();
  });

  it('renders backups table with data', () => {
    mockData = mockBackups;
    renderWithProviders(<BackupPage />);
    expect(screen.getByText('backup-2024-01-01.ofbak')).toBeInTheDocument();
    expect(screen.getByText('Daily backup')).toBeInTheDocument();
    expect(screen.getByText('5.2 MB')).toBeInTheDocument();
    expect(screen.getByText('Disk full')).toBeInTheDocument();
  });

  it('shows status and type badges', () => {
    mockData = mockBackups;
    renderWithProviders(<BackupPage />);
    expect(screen.getByText('Completed')).toBeInTheDocument();
    expect(screen.getByText('Failed')).toBeInTheDocument();
    expect(screen.getByText('Manual')).toBeInTheDocument();
    expect(screen.getByText('Automatic')).toBeInTheDocument();
  });

  it('opens create backup dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BackupPage />);
    const buttons = screen.getAllByRole('button', { name: /create backup/i });
    await user.click(buttons[0]);
    expect(screen.getByText(/create a portable backup/i)).toBeInTheDocument();
  });

  it('opens upload dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BackupPage />);
    const buttons = screen.getAllByRole('button', { name: /upload backup/i });
    await user.click(buttons[0]);
    expect(screen.getByText(/import your user data/i)).toBeInTheDocument();
  });

  it('calls createBackup when confirmed', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BackupPage />);
    const buttons = screen.getAllByRole('button', { name: /create backup/i });
    await user.click(buttons[0]);
    // Dialog opens - find the create button inside the dialog (last one)
    const allCreateButtons = screen.getAllByRole('button', { name: /create backup/i });
    await user.click(allCreateButtons[allCreateButtons.length - 1]);
    expect(mockCreateBackup).toHaveBeenCalled();
  });

  it('shows delete confirmation when delete clicked', async () => {
    const user = userEvent.setup();
    mockData = mockBackups;
    renderWithProviders(<BackupPage />);
    const deleteButtons = screen.getAllByTitle(/delete/i);
    await user.click(deleteButtons[0]);
    expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();
  });

  it('shows restore confirmation when restore clicked', async () => {
    const user = userEvent.setup();
    mockData = mockBackups;
    renderWithProviders(<BackupPage />);
    const restoreButtons = screen.getAllByTitle(/restore/i);
    await user.click(restoreButtons[0]);
    expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();
  });

  it('calls deleteBackup on confirm', async () => {
    const user = userEvent.setup();
    mockData = mockBackups;
    renderWithProviders(<BackupPage />);
    const deleteButtons = screen.getAllByTitle(/delete/i);
    await user.click(deleteButtons[0]);
    await user.click(screen.getByText('Confirm'));
    expect(mockDeleteBackup).toHaveBeenCalledWith(1);
  });

  it('calls downloadBackup on download click', async () => {
    const user = userEvent.setup();
    mockData = mockBackups;
    renderWithProviders(<BackupPage />);
    const downloadButtons = screen.getAllByTitle(/download/i);
    await user.click(downloadButtons[0]);
    expect(mockDownloadBackup).toHaveBeenCalledWith({
      backupId: 1,
      filename: 'backup-2024-01-01.ofbak',
    });
  });

  it('shows loading skeleton when loading', () => {
    mockIsLoading = true;
    mockData = undefined;
    renderWithProviders(<BackupPage />);
    // Should not show the empty state or table
    expect(screen.queryByText(/no backups/i)).not.toBeInTheDocument();
  });

  it('shows IN_PROGRESS and PENDING status badges', () => {
    mockData = [
      {
        id: 3,
        filename: 'backup-progress.ofbak',
        description: null,
        formattedFileSize: '1 MB',
        createdAt: '2024-01-03T10:00:00Z',
        backupType: 'MANUAL' as const,
        status: 'IN_PROGRESS' as const,
        errorMessage: null,
      },
      {
        id: 4,
        filename: 'backup-pending.ofbak',
        description: null,
        formattedFileSize: '2 MB',
        createdAt: '2024-01-04T10:00:00Z',
        backupType: 'AUTOMATIC' as const,
        status: 'PENDING' as const,
        errorMessage: null,
      },
    ];
    renderWithProviders(<BackupPage />);
    expect(screen.getByText('In Progress')).toBeInTheDocument();
    expect(screen.getByText('Pending')).toBeInTheDocument();
  });

  it('rejects file with wrong extension in upload', async () => {
    const user = userEvent.setup();
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    renderWithProviders(<BackupPage />);

    // Open upload dialog
    const uploadButtons = screen.getAllByRole('button', { name: /upload backup/i });
    await user.click(uploadButtons[0]);

    // Find the file input and simulate selecting a .txt file
    const fileInput = document.getElementById('backup-file-input') as HTMLInputElement;
    expect(fileInput).toBeTruthy();

    const badFile = new File(['content'], 'test.txt', { type: 'text/plain' });
    Object.defineProperty(fileInput, 'files', { value: [badFile], writable: false });
    fireEvent.change(fileInput);

    expect(alertSpy).toHaveBeenCalled();
  });

  it('rejects file too large in upload', async () => {
    const user = userEvent.setup();
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    renderWithProviders(<BackupPage />);

    const uploadButtons = screen.getAllByRole('button', { name: /upload backup/i });
    await user.click(uploadButtons[0]);

    const fileInput = document.getElementById('backup-file-input') as HTMLInputElement;
    const bigFile = new File(['x'], 'big.ofbak', { type: 'application/octet-stream' });
    Object.defineProperty(bigFile, 'size', { value: 150 * 1024 * 1024 });
    Object.defineProperty(fileInput, 'files', { value: [bigFile], writable: false });
    fireEvent.change(fileInput);

    expect(alertSpy).toHaveBeenCalled();
  });

  it('accepts valid .ofbak file and enables upload button', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BackupPage />);

    const uploadButtons = screen.getAllByRole('button', { name: /upload backup/i });
    await user.click(uploadButtons[0]);

    const fileInput = document.getElementById('backup-file-input') as HTMLInputElement;
    const validFile = new File(['backup-data'], 'test.ofbak', { type: 'application/octet-stream' });
    Object.defineProperty(fileInput, 'files', { value: [validFile], writable: false });
    fireEvent.change(fileInput);

    // The file name should appear
    expect(screen.getByText('test.ofbak')).toBeInTheDocument();
  });

  it('calls restoreBackup on confirm and shows alert', async () => {
    const user = userEvent.setup();
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    mockData = mockBackups;
    renderWithProviders(<BackupPage />);

    const restoreButtons = screen.getAllByTitle(/restore/i);
    await user.click(restoreButtons[0]);
    await user.click(screen.getByText('Confirm'));

    expect(mockRestoreBackup).toHaveBeenCalledWith({ backupId: 1, masterPassword: undefined });
    // Wait for the alert to fire
    await vi.waitFor(() => {
      expect(alertSpy).toHaveBeenCalled();
    });
  });

  it.each(['stored', 'uploaded'])(
    'refreshes profile and currency preferences after a %s restore',
    async source => {
      const user = userEvent.setup();
      vi.spyOn(window, 'alert').mockImplementation(() => {});
      mockData = mockBackups;
      server.use(
        http.get('/api/v1/users/me', () =>
          HttpResponse.json({
            id: 1,
            username: 'testuser',
            baseCurrency: 'GBP',
            profileImage: 'data:image/png;base64,restored',
          })
        ),
        http.get('/api/v1/users/me/settings', () =>
          HttpResponse.json({
            userId: 1,
            language: 'en',
            amountDisplayMode: 'both',
            secondaryCurrency: 'EUR',
          })
        )
      );
      renderWithProviders(
        <>
          <BackupPage />
          <RestoredPreferences />
        </>
      );

      if (source === 'stored') {
        await user.click(screen.getAllByTitle(/restore/i)[0]);
        await user.click(screen.getByText('Confirm'));
      } else {
        await user.click(screen.getByRole('button', { name: /upload backup/i }));
        const fileInput = document.getElementById('backup-file-input') as HTMLInputElement;
        fireEvent.change(fileInput, {
          target: { files: [new File(['archive'], 'portable.ofbak')] },
        });
        await user.type(
          screen.getByLabelText(/Backup master password/),
          'Original master password'
        );
        await user.click(screen.getByRole('button', { name: 'Upload & Restore' }));
        expect(mockUploadAndRestore).toHaveBeenCalledWith({
          file: expect.any(File),
          masterPassword: 'Original master password',
        });
      }

      await vi.waitFor(() =>
        expect(screen.getByLabelText('Active preferences')).toHaveTextContent('GBP/both/EUR')
      );
      const storedUser = JSON.parse(localStorage.getItem(STORAGE_KEYS.AUTH_USER) ?? '{}');
      expect(storedUser.username).toBe('testuser');
      expect(storedUser.baseCurrency).toBe('GBP');
      expect(storedUser.profileImage).toBe('data:image/png;base64,restored');
    }
  );

  it('handles createBackup error gracefully', async () => {
    mockCreateBackup.mockRejectedValueOnce(new Error('Create failed'));
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const user = userEvent.setup();
    renderWithProviders(<BackupPage />);

    const buttons = screen.getAllByRole('button', { name: /create backup/i });
    await user.click(buttons[0]);
    const allCreateButtons = screen.getAllByRole('button', { name: /create backup/i });
    await user.click(allCreateButtons[allCreateButtons.length - 1]);

    await vi.waitFor(() => {
      expect(consoleSpy).toHaveBeenCalled();
    });
  });
});
