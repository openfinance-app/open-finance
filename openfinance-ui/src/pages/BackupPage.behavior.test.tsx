import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import BackupPage from '@/pages/BackupPage';
import { server } from '@/test/mocks/server';
import { http, HttpResponse } from 'msw';

const state = vi.hoisted(() => ({
  create: vi.fn(),
  restore: vi.fn(),
  upload: vi.fn(),
  remove: vi.fn(),
  download: vi.fn(),
  creating: false,
  uploading: false,
}));
vi.mock('@/hooks/useBackup', () => ({
  useListBackups: () => ({
    data: [
      {
        id: 1,
        filename: 'saved.ofbak',
        status: 'COMPLETED',
        backupType: 'MANUAL',
        formattedFileSize: '1 KB',
        createdAt: '2026-01-01T00:00:00Z',
      },
    ],
    isLoading: false,
  }),
  useCreateBackup: () => ({ mutateAsync: state.create, isPending: state.creating }),
  useRestoreBackup: () => ({ mutateAsync: state.restore, isPending: false }),
  useUploadAndRestoreBackup: () => ({ mutateAsync: state.upload, isPending: state.uploading }),
  useDeleteBackup: () => ({ mutateAsync: state.remove, isPending: false }),
  useDownloadBackup: () => ({ mutateAsync: state.download, isPending: false }),
}));

function openUpload() {
  fireEvent.click(screen.getByRole('button', { name: 'Upload Backup' }));
  return screen.getByLabelText(/Click to select backup file|chosen\.ofbak/);
}
function selectFile(input: HTMLElement) {
  const file = new File(['data'], 'chosen.ofbak');
  fireEvent.change(input, { target: { files: [file] } });
  return file;
}

beforeEach(() => {
  vi.clearAllMocks();
  mockAuthentication();
  state.creating = false;
  state.uploading = false;
  state.create.mockResolvedValue({});
  state.upload.mockResolvedValue('Restored');
  Element.prototype.scrollIntoView = vi.fn();
  vi.spyOn(window, 'alert').mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

describe('backup dialog lifecycle', () => {
  it.each(['Cancel', 'Escape'])(
    'clears both the password and selected archive when upload closes with %s',
    close => {
      renderWithProviders(<BackupPage />);
      const input = openUpload();
      selectFile(input);
      fireEvent.change(screen.getByLabelText(/Backup master password/), {
        target: { value: 'previous-secret' },
      });
      if (close === 'Cancel')
        fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }));
      else fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
      openUpload();
      expect(screen.getByLabelText(/Backup master password/)).toHaveValue('');
      expect(screen.getByRole('button', { name: 'Upload & Restore' })).toBeDisabled();
      expect(screen.queryByText('chosen.ofbak')).not.toBeInTheDocument();
      expect(state.upload).not.toHaveBeenCalled();
    }
  );

  it.each(['Cancel', 'Escape'])('clears the restore password on %s', close => {
    renderWithProviders(<BackupPage />);
    fireEvent.click(screen.getByTitle('Restore from this backup'));
    fireEvent.change(screen.getByLabelText(/Backup master password/), {
      target: { value: 'previous-secret' },
    });
    if (close === 'Cancel') fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    else fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    fireEvent.click(screen.getByTitle('Restore from this backup'));
    expect(screen.getByLabelText(/Backup master password/)).toHaveValue('');
    expect(state.restore).not.toHaveBeenCalled();
  });

  it('cancels deletion without touching the archive', () => {
    renderWithProviders(<BackupPage />);
    fireEvent.click(screen.getByTitle('Delete backup'));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(state.remove).not.toHaveBeenCalled();
  });

  it.each([null, []])('does not enable restore when the file chooser returns %j', files => {
    renderWithProviders(<BackupPage />);
    fireEvent.change(openUpload(), { target: { files } });
    expect(screen.getByRole('button', { name: 'Upload & Restore' })).toBeDisabled();
    expect(state.upload).not.toHaveBeenCalled();
  });

  it('disables duplicate creation and cancellation while a backup is being created', () => {
    state.creating = true;
    renderWithProviders(<BackupPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Create Backup' }));
    expect(screen.getByRole('button', { name: 'Creating...' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled();
  });

  it('disables duplicate upload submission during restoration', () => {
    state.uploading = true;
    renderWithProviders(<BackupPage />);
    selectFile(openUpload());
    expect(screen.getByRole('button', { name: 'Restoring...' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled();
  });

  it('uploads an unencrypted archive without sending an empty password and refreshes preferences', async () => {
    server.use(
      http.get('*/users/me', () =>
        HttpResponse.json({ id: 1, baseCurrency: 'USD', profileImage: 'avatar' })
      ),
      http.get('*/users/me/settings', () => HttpResponse.json({ language: null }))
    );
    renderWithProviders(<BackupPage />);
    const file = selectFile(openUpload());
    fireEvent.click(screen.getByRole('button', { name: 'Upload & Restore' }));
    await waitFor(() =>
      expect(state.upload).toHaveBeenCalledWith({ file, masterPassword: undefined })
    );
    await waitFor(() => expect(window.alert).toHaveBeenCalledWith('Success: Restored'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
