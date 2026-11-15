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

  it.each(['picker', 'drop'])(
    'uploads all files selected by %s before advancing once',
    async method => {
      const first = new File(['date,amount\n2026-01-01,-10'], 'first.csv');
      const second = new File(['!Type:Bank\nD1/1/2026\nT-20\n^'], 'second.qif');
      const secondResponse = { ...response, uploadId: 'upload-2', fileName: second.name };
      let finishSecond!: (value: { data: FileUploadResponse }) => void;
      const post = vi
        .spyOn(apiClient, 'post')
        .mockResolvedValueOnce({ data: response })
        .mockImplementationOnce(
          () =>
            new Promise(resolve => {
              finishSecond = resolve;
            })
        );
      const success = vi.fn();
      const { container } = renderWithProviders(<FileUpload onUploadSuccess={success} />);
      const picker = container.querySelector<HTMLInputElement>('input[type="file"]')!;
      expect(picker.multiple).toBe(true);
      if (method === 'picker') fireEvent.change(picker, { target: { files: [first, second] } });
      else fireEvent.drop(picker.parentElement!, { dataTransfer: { files: [first, second] } });
      await userEvent.click(screen.getByRole('button', { name: tr('upload.startBatch') }));
      await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
      expect(success).not.toHaveBeenCalled();
      finishSecond({ data: secondResponse });
      await waitFor(() =>
        expect(success).toHaveBeenCalledExactlyOnceWith(response, [secondResponse])
      );
      expect(post.mock.calls.map(call => (call[1] as FormData).get('file'))).toEqual([
        first,
        second,
      ]);
    }
  );

  it('uploads distinct files even when their names, sizes and modification times match', async () => {
    const files = [
      new File(['first'], 'statement.csv', { lastModified: 100 }),
      new File(['other'], 'statement.csv', { lastModified: 100 }),
    ];
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: response });
    const success = vi.fn();
    const { container } = renderWithProviders(<FileUpload onUploadSuccess={success} />);
    fireEvent.change(container.querySelector('input[type="file"]')!, { target: { files } });
    await userEvent.click(screen.getByRole('button', { name: tr('upload.startBatch') }));
    await waitFor(() => expect(success).toHaveBeenCalledOnce());
    expect(post.mock.calls.map(call => (call[1] as FormData).get('file'))).toEqual(files);
  });

  it('retries a failed file without reuploading completed files or losing the selection', async () => {
    const first = new File(['first'], 'first.csv');
    const second = new File(['second'], 'second.csv');
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce({ data: response })
      .mockRejectedValueOnce(new Error('Offline'))
      .mockResolvedValueOnce({ data: { ...response, uploadId: 'upload-2' } });
    const success = vi.fn();
    const { container } = renderWithProviders(<FileUpload onUploadSuccess={success} />);
    fireEvent.change(container.querySelector('input[type="file"]')!, {
      target: { files: [first, second] },
    });
    await userEvent.click(screen.getByRole('button', { name: tr('upload.startBatch') }));
    await screen.findByText('second.csv: Offline');
    expect(success).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: tr('upload.tryAgain') }));
    expect(screen.getByText('first.csv')).toBeInTheDocument();
    expect(screen.getByText('second.csv')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: tr('upload.startBatch') }));
    await waitFor(() => expect(success).toHaveBeenCalledOnce());
    expect(post.mock.calls.map(call => (call[1] as FormData).get('file'))).toEqual([
      first,
      second,
      second,
    ]);
  });

  it('keeps valid files, deduplicates additions, and lets a selection be removed', async () => {
    const file = new File(['ok'], 'first.csv');
    const { container } = renderWithProviders(<FileUpload />);
    const picker = container.querySelector('input[type="file"]')!;
    fireEvent.change(picker, { target: { files: [file, new File(['no'], 'bad.exe')] } });
    expect(screen.getByRole('alert')).toHaveTextContent('bad.exe');
    fireEvent.change(picker, { target: { files: [file, new File(['ok'], 'second.qif')] } });
    expect(screen.getAllByText('first.csv')).toHaveLength(1);
    await userEvent.click(screen.getByRole('button', { name: 'Remove first.csv' }));
    expect(screen.queryByText('first.csv')).not.toBeInTheDocument();
    expect(screen.getByText('second.qif')).toBeInTheDocument();
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
    await waitFor(() => expect(success).toHaveBeenCalledWith(response, []));
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
    expect(await screen.findByText('bank.csv: Invalid CSV columns')).toBeInTheDocument();
    expect(error).toHaveBeenCalledWith('bank.csv: Invalid CSV columns');
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
    await userEvent.click(screen.getByRole('button', { name: 'Remove bank.ofx' }));
    expect(screen.queryByText('bank.ofx')).not.toBeInTheDocument();
    const picker = container.querySelector<HTMLInputElement>('input[type="file"]')!;
    const click = vi.spyOn(picker, 'click');
    await userEvent.click(screen.getByRole('button', { name: tr('upload.browse') }));
    expect(click).toHaveBeenCalledOnce();
  });
});
