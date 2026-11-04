import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import apiClient from '@/services/apiClient';
import { FileUpload } from '@/components/import/FileUpload';
import type { FileUploadResponse } from '@/types/import';

const response: FileUploadResponse = {
  uploadId: 'upload-1',
  fileName: 'bank.CSV',
  fileSize: 12,
  fileType: 'CSV',
  status: 'VALIDATED',
  message: 'Validated',
  uploadedAt: '2026-01-01T12:00:00Z',
  recordCount: 1,
};
const tr = (key: string) => i18n.t(key, { ns: 'import' });
beforeEach(async () => {
  mockAuthentication();
  await i18n.changeLanguage('en');
});
afterEach(() => vi.restoreAllMocks());

function selectFile(container: HTMLElement, file: File) {
  // The native picker is hidden; the visible Browse button opens it.
  const picker = container.querySelector<HTMLInputElement>('input[type="file"]')!;
  fireEvent.change(picker, { target: { files: [file] } });
}

describe('transaction file selection and upload', () => {
  it.each([
    ['bad.exe', 10],
    ['empty.csv', 0],
    ['large.csv', 2 * 1024 * 1024],
  ])('rejects invalid file %s before contacting the server', async (name, size) => {
    const post = vi.spyOn(apiClient, 'post');
    const onError = vi.fn();
    const { container } = renderWithProviders(<FileUpload maxSizeMB={1} onUploadError={onError} />);
    const file = new File(['x'], name);
    Object.defineProperty(file, 'size', { value: size });
    selectFile(container, file);
    expect(screen.getByText(tr('upload.failed'))).toBeInTheDocument();
    expect(onError).toHaveBeenCalledWith(expect.any(String));
    expect(post).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: tr('upload.tryAgain') }));
    expect(screen.getByRole('button', { name: tr('upload.browse') })).toBeInTheDocument();
  });

  it('accepts uppercase extensions, submits the actual file, and can start another upload', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: response });
    const success = vi.fn();
    const { container } = renderWithProviders(<FileUpload onUploadSuccess={success} />);
    const file = new File(['date,amount\n2026-01-01,12'], 'bank.CSV', { type: 'text/csv' });
    selectFile(container, file);
    expect(screen.getByText('bank.CSV')).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole('button', { name: i18n.t('buttons.upload', { ns: 'common' }) })
    );
    await waitFor(() => expect(success).toHaveBeenCalledWith(response));
    expect((post.mock.calls[0][1] as FormData).get('file')).toBe(file);
    await userEvent.click(screen.getByRole('button', { name: tr('upload.uploadAnother') }));
    expect(screen.queryByText('bank.CSV')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: tr('upload.browse') })).toBeInTheDocument();
  });

  it('shows server validation failures and permits recovery with a different file', async () => {
    vi.spyOn(apiClient, 'post').mockRejectedValue({
      response: { data: { message: 'Invalid CSV columns' } },
    });
    const error = vi.fn();
    const { container } = renderWithProviders(<FileUpload onUploadError={error} />);
    selectFile(container, new File(['invalid'], 'bank.csv'));
    await userEvent.click(
      screen.getByRole('button', { name: i18n.t('buttons.upload', { ns: 'common' }) })
    );
    expect(await screen.findByText('Invalid CSV columns')).toBeInTheDocument();
    expect(error).toHaveBeenCalledWith('Invalid CSV columns');
    await userEvent.click(screen.getByRole('button', { name: tr('upload.tryAgain') }));
    selectFile(container, new File(['valid'], 'replacement.csv'));
    expect(screen.getByText('replacement.csv')).toBeInTheDocument();
  });

  it('accepts a dropped file, handles empty drops, and removes the selection', async () => {
    const { container } = renderWithProviders(<FileUpload />);
    const dropZone = container.querySelector('input[type="file"]')!.parentElement!;
    fireEvent.dragEnter(dropZone);
    fireEvent.dragOver(dropZone);
    fireEvent.dragLeave(dropZone);
    fireEvent.drop(dropZone, { dataTransfer: { files: [] } });
    expect(screen.getByRole('button', { name: tr('upload.browse') })).toBeInTheDocument();
    fireEvent.drop(dropZone, {
      dataTransfer: { files: [new File(['x'.repeat(2048)], 'bank.ofx')] },
    });
    expect(screen.getByText('2.00 KB')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: tr('upload.removeFile') }));
    expect(screen.queryByText('bank.ofx')).not.toBeInTheDocument();
    const picker = container.querySelector<HTMLInputElement>('input[type="file"]')!;
    const click = vi.spyOn(picker, 'click');
    await userEvent.click(screen.getByRole('button', { name: tr('upload.browse') }));
    expect(click).toHaveBeenCalledOnce();
  });
});
