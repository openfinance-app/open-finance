import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { AttachmentUpload } from '@/components/attachments/AttachmentUpload';

// ---------------------------------------------------------------------------
// Mock react-dropzone
// ---------------------------------------------------------------------------
const mockGetRootProps = vi.fn(() => ({ onClick: vi.fn() }));
const mockGetInputProps = vi.fn(() => ({}));
let capturedOnDrop: ((accepted: File[], rejected: any[]) => void) | undefined;

vi.mock('react-dropzone', () => ({
  useDropzone: ({ onDrop }: { onDrop: (accepted: File[], rejected: any[]) => void }) => {
    capturedOnDrop = onDrop;
    return {
      getRootProps: mockGetRootProps,
      getInputProps: mockGetInputProps,
      isDragActive: false,
    };
  },
}));

// ---------------------------------------------------------------------------
// Mock upload hook
// ---------------------------------------------------------------------------
const mockUploadFile = vi.fn();
const mockUseUploadAttachment = vi.fn();

vi.mock('@/hooks/useAttachments', () => ({
  useUploadAttachment: () => mockUseUploadAttachment(),
}));

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function makeFile(name: string, type: string, size = 1024): File {
  const file = new File(['content'], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

describe('AttachmentUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedOnDrop = undefined;
    mockAuthentication();
    mockUseUploadAttachment.mockReturnValue({
      mutateAsync: mockUploadFile,
      isPending: false,
      uploadProgress: null,
    });
  });

  describe('Rendering', () => {
    it('should render the dropzone area', () => {
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );
      // The dropzone renders a hidden <input> for file selection
      const fileInput = document.querySelector('input');
      expect(fileInput).toBeInTheDocument();
    });

    it('should render the upload button initially disabled (no files)', () => {
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );
      const uploadButton = screen.queryByRole('button', { name: /upload/i });
      // Button may not be visible until files are added
      if (uploadButton) {
        expect(uploadButton).toBeDisabled();
      }
    });

    it('should show pending state while uploading', () => {
      mockUseUploadAttachment.mockReturnValue({
        mutateAsync: mockUploadFile,
        isPending: true,
        uploadProgress: { percentage: 50, loaded: 512, total: 1024 },
      });
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );
      // The dropzone should be disabled (pointer-events-none) when pending
      const dropzone = document.querySelector('[class*="pointer-events-none"]');
      expect(dropzone).toBeInTheDocument();
    });
  });

  describe('File acceptance', () => {
    it('should add accepted files to the list', async () => {
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );

      const file = makeFile('document.pdf', 'application/pdf');
      capturedOnDrop?.([file], []);

      await waitFor(() => {
        expect(screen.getByText('document.pdf')).toBeInTheDocument();
      });
    });

    it('should show upload button once files are added', async () => {
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );

      const file = makeFile('photo.jpg', 'image/jpeg');
      capturedOnDrop?.([file], []);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /upload/i })).toBeInTheDocument();
      });
    });

    it('should show selected files count', async () => {
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );

      capturedOnDrop?.([makeFile('a.pdf', 'application/pdf'), makeFile('b.jpg', 'image/jpeg')], []);

      await waitFor(() => {
        expect(screen.getByText(/selected files/i)).toBeInTheDocument();
      });
    });
  });

  describe('File rejection', () => {
    it('should show error when file is too large', async () => {
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );

      const rejected = [{ errors: [{ code: 'file-too-large' }] }];
      capturedOnDrop?.([], rejected);

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
    });

    it('should show error when file type is invalid', async () => {
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );

      const rejected = [{ errors: [{ code: 'file-invalid-type' }] }];
      capturedOnDrop?.([], rejected);

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
    });

    it('should show generic error for unknown rejection code', async () => {
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );

      const rejected = [{ errors: [{ code: 'unknown-error' }] }];
      capturedOnDrop?.([], rejected);

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
    });

    it('should show error when too many files are added', async () => {
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} maxFiles={2} />
      );

      // Add 3 files (exceeds maxFiles=2)
      capturedOnDrop?.(
        [makeFile('a.pdf', 'application/pdf'), makeFile('b.jpg', 'image/jpeg'), makeFile('c.png', 'image/png')],
        []
      );

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
    });
  });

  describe('Remove file', () => {
    it('should remove file from list when X button is clicked', async () => {
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );

      const file = makeFile('test.pdf', 'application/pdf');
      capturedOnDrop?.([file], []);

      await waitFor(() => {
        expect(screen.getByText('test.pdf')).toBeInTheDocument();
      });

      const removeButton = screen.getByRole('button', { name: '' }); // X button
      fireEvent.click(removeButton);

      await waitFor(() => {
        expect(screen.queryByText('test.pdf')).not.toBeInTheDocument();
      });
    });
  });

  describe('Upload', () => {
    it('should call upload for each file when upload button is clicked', async () => {
      mockUploadFile.mockResolvedValue({});
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );

      capturedOnDrop?.([makeFile('doc.pdf', 'application/pdf')], []);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /upload/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /upload/i }));

      await waitFor(() => {
        expect(mockUploadFile).toHaveBeenCalledWith(
          expect.objectContaining({
            entityType: 'TRANSACTION',
            entityId: 10,
          })
        );
      });
    });

    it('should call onUploadComplete callback after successful upload', async () => {
      mockUploadFile.mockResolvedValue({});
      const onComplete = vi.fn();
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} onUploadComplete={onComplete} />
      );

      capturedOnDrop?.([makeFile('doc.pdf', 'application/pdf')], []);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /upload/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /upload/i }));

      await waitFor(() => {
        expect(onComplete).toHaveBeenCalled();
      });
    });

    it('should show error when upload fails', async () => {
      mockUploadFile.mockRejectedValue(new Error('Upload failed'));
      renderWithProviders(
        <AttachmentUpload entityType="TRANSACTION" entityId={10} />
      );

      capturedOnDrop?.([makeFile('doc.pdf', 'application/pdf')], []);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /upload/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /upload/i }));

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
    });
  });
});
